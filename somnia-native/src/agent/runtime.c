#include "runtime.h"
#include "memory.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#ifdef _WIN32
#include <windows.h>
static uint64_t getTimeMs(void) {
    return GetTickCount64();
}
#else
#include <sys/time.h>
static uint64_t getTimeMs(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (uint64_t)(tv.tv_sec) * 1000 + (uint64_t)(tv.tv_usec) / 1000;
}
#endif

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

uint64_t currentTimeMs(void) {
    return getTimeMs();
}

// Simple xorshift64 for deterministic random
double deterministicRandom(uint64_t* seed) {
    uint64_t x = *seed;
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    *seed = x;
    return (double)(x % 10000) / 10000.0;
}

// Generate unique cycle ID
static uint64_t cycleCounter = 0;

ObjString* generateCycleId(void) {
    char buffer[32];
    snprintf(buffer, sizeof(buffer), "cycle_%llu_%llu", 
             (unsigned long long)currentTimeMs(), 
             (unsigned long long)cycleCounter++);
    return copyString(buffer, (int)strlen(buffer));
}

// ============================================================================
// TRACE
// ============================================================================

void initTrace(Trace* trace) {
    trace->cycleId = NULL;
    trace->timestamp = 0;
    trace->intent.name = NULL;
    initTable(&trace->intent.args);
    trace->rulesEvaluated = 0;
    initProposalArray(&trace->proposals);
    trace->idDurationMs = 0;
    trace->selection.selected = NULL;
    trace->selection.selectedCount = 0;
    trace->selection.rejected = NULL;
    trace->selection.rejectedCount = 0;
    trace->egoDurationMs = 0;
    trace->results = NULL;
    trace->resultCount = 0;
    trace->actDurationMs = 0;
    trace->totalDurationMs = 0;
}

void freeTrace(Trace* trace) {
    freeTable(&trace->intent.args);
    freeProposalArray(&trace->proposals);
    freeSelectionResult(&trace->selection);
    free(trace->results);
    initTrace(trace);
}

// Convert trace to JSON string
char* traceToJson(Trace* trace) {
    // Calculate buffer size (simplified, would need dynamic allocation in production)
    size_t bufSize = 8192;
    char* buffer = (char*)malloc(bufSize);
    char* ptr = buffer;
    int remaining = (int)bufSize;
    int written;
    
    written = snprintf(ptr, remaining, "{\n");
    ptr += written; remaining -= written;
    
    // Cycle info
    written = snprintf(ptr, remaining, 
        "  \"cycle_id\": \"%s\",\n"
        "  \"timestamp\": %llu,\n",
        trace->cycleId ? trace->cycleId->chars : "unknown",
        (unsigned long long)trace->timestamp);
    ptr += written; remaining -= written;
    
    // Intent
    written = snprintf(ptr, remaining,
        "  \"intent\": {\n"
        "    \"name\": \"%s\"\n"
        "  },\n",
        trace->intent.name ? trace->intent.name->chars : "unknown");
    ptr += written; remaining -= written;
    
    // ID phase
    written = snprintf(ptr, remaining,
        "  \"id_phase\": {\n"
        "    \"rules_evaluated\": %d,\n"
        "    \"proposals_count\": %d,\n"
        "    \"duration_ms\": %.2f\n"
        "  },\n",
        trace->rulesEvaluated,
        trace->proposals.count,
        trace->idDurationMs);
    ptr += written; remaining -= written;
    
    // EGO phase
    written = snprintf(ptr, remaining,
        "  \"ego_phase\": {\n"
        "    \"selected_count\": %d,\n"
        "    \"rejected_count\": %d,\n"
        "    \"duration_ms\": %.2f\n"
        "  },\n",
        trace->selection.selectedCount,
        trace->selection.rejectedCount,
        trace->egoDurationMs);
    ptr += written; remaining -= written;
    
    // ACT phase
    written = snprintf(ptr, remaining,
        "  \"act_phase\": {\n"
        "    \"actions_executed\": %d,\n"
        "    \"duration_ms\": %.2f\n"
        "  },\n",
        trace->resultCount,
        trace->actDurationMs);
    ptr += written; remaining -= written;
    
    // Total
    written = snprintf(ptr, remaining,
        "  \"total_duration_ms\": %.2f\n"
        "}\n",
        trace->totalDurationMs);
    ptr += written; remaining -= written;
    
    return buffer;
}

// ============================================================================
// RUNTIME
// ============================================================================

static Trace lastTrace;  // Store last trace for debugging

void initRuntime(SomniaRuntime* runtime) {
    initIdEngine(&runtime->id);
    initEgo(&runtime->ego);
    initAct(&runtime->act);
    
    runtime->config.seed = (uint64_t)time(NULL);
    runtime->config.enableTracing = true;
    runtime->config.deterministicMode = true;
    runtime->config.maxCyclesPerRequest = 100;
    
    initFactArray(&runtime->worldState);
    initTable(&runtime->memory);
    
    runtime->totalCycles = 0;
    runtime->totalProposals = 0;
    runtime->totalExecutions = 0;
    runtime->avgCycleDurationMs = 0;
    
    // Register built-in actions
    registerBuiltinActions(&runtime->act);
    
    initTrace(&lastTrace);
}

void freeRuntime(SomniaRuntime* runtime) {
    freeIdEngine(&runtime->id);
    freeEgo(&runtime->ego);
    freeAct(&runtime->act);
    freeFactArray(&runtime->worldState);
    freeTable(&runtime->memory);
    freeTrace(&lastTrace);
}

void setRuntimeConfig(SomniaRuntime* runtime, RuntimeConfig config) {
    runtime->config = config;
}

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

void updateFact(SomniaRuntime* runtime, const char* key, Value value) {
    Fact fact = createFact(key, value);
    writeFactArray(&runtime->worldState, fact);
}

Value getFact(SomniaRuntime* runtime, const char* key) {
    return getFactValue(&runtime->worldState, key);
}

void setMemory(SomniaRuntime* runtime, const char* key, Value value) {
    ObjString* keyStr = copyString(key, (int)strlen(key));
    tableSet(&runtime->memory, keyStr, value);
}

Value getMemory(SomniaRuntime* runtime, const char* key) {
    ObjString* keyStr = copyString(key, (int)strlen(key));
    Value value;
    if (tableGet(&runtime->memory, keyStr, &value)) {
        return value;
    }
    return NULL_VAL;
}

// ============================================================================
// CYCLE EXECUTION
// ============================================================================

CycleResult runCycle(SomniaRuntime* runtime, Intent intent, FactArray* facts) {
    CycleResult result;
    initCycleResult(&result);
    
    uint64_t cycleStart = currentTimeMs();
    
    // Setup trace
    freeTrace(&lastTrace);
    initTrace(&lastTrace);
    lastTrace.cycleId = generateCycleId();
    lastTrace.timestamp = cycleStart;
    lastTrace.intent = intent;
    
    result.traceId = lastTrace.cycleId;
    
    // Build execution context
    ExecutionContext ctx;
    initExecutionContext(&ctx);
    ctx.intent = intent;
    ctx.seed = runtime->config.seed;
    ctx.timestamp = cycleStart;
    
    // Copy facts
    if (facts != NULL) {
        for (int i = 0; i < facts->count; i++) {
            writeFactArray(&ctx.facts, facts->facts[i]);
        }
    }
    // Add world state facts
    for (int i = 0; i < runtime->worldState.count; i++) {
        writeFactArray(&ctx.facts, runtime->worldState.facts[i]);
    }
    
    // Reset budget windows
    resetBudgetWindows(&runtime->ego, cycleStart);
    
    // =========================================================================
    // ID PHASE: Generate proposals
    // =========================================================================
    uint64_t idStart = currentTimeMs();
    
    ProposalArray proposals = evaluate(&runtime->id, &ctx);
    
    uint64_t idEnd = currentTimeMs();
    lastTrace.idDurationMs = (double)(idEnd - idStart);
    lastTrace.rulesEvaluated = runtime->id.rules.count;
    lastTrace.proposals = proposals;
    
    result.generatedProposals = proposals;
    runtime->totalProposals += proposals.count;
    
    printf("[ID] Generated %d proposals (%.2fms)\n", 
           proposals.count, lastTrace.idDurationMs);
    
    // =========================================================================
    // EGO PHASE: Select proposals
    // =========================================================================
    uint64_t egoStart = currentTimeMs();
    
    SelectionResult selection = select(&runtime->ego, &proposals, &ctx);
    
    uint64_t egoEnd = currentTimeMs();
    lastTrace.egoDurationMs = (double)(egoEnd - egoStart);
    lastTrace.selection = selection;
    
    printf("[EGO] Selected %d, Rejected %d (%.2fms)\n",
           selection.selectedCount, selection.rejectedCount, lastTrace.egoDurationMs);
    
    // =========================================================================
    // ACT PHASE: Execute selected proposals
    // =========================================================================
    uint64_t actStart = currentTimeMs();
    
    int resultCount = 0;
    ActionResult* results = NULL;
    
    if (selection.selectedCount > 0) {
        results = executeAll(&runtime->act, selection.selected, 
                            selection.selectedCount, &resultCount);
        
        runtime->totalExecutions += resultCount;
    }
    
    uint64_t actEnd = currentTimeMs();
    lastTrace.actDurationMs = (double)(actEnd - actStart);
    lastTrace.results = results;
    lastTrace.resultCount = resultCount;
    
    result.results = results;
    result.resultCount = resultCount;
    
    printf("[ACT] Executed %d actions (%.2fms)\n",
           resultCount, lastTrace.actDurationMs);
    
    // Print results
    for (int i = 0; i < resultCount; i++) {
        const char* status = "UNKNOWN";
        switch (results[i].type) {
            case ACTION_SUCCESS: status = "SUCCESS"; break;
            case ACTION_ERROR_RETRYABLE: status = "ERROR_RETRYABLE"; break;
            case ACTION_ERROR_FATAL: status = "ERROR_FATAL"; break;
            case ACTION_TIMEOUT: status = "TIMEOUT"; break;
            case ACTION_CANCELLED: status = "CANCELLED"; break;
        }
        printf("[ACT]   [%d] %s: %s (%.2fms)\n", 
               i + 1, 
               selection.selected[i].proposal.action->chars,
               status, 
               results[i].durationMs);
    }
    
    // =========================================================================
    // FINALIZE
    // =========================================================================
    uint64_t cycleEnd = currentTimeMs();
    result.totalDurationMs = (double)(cycleEnd - cycleStart);
    lastTrace.totalDurationMs = result.totalDurationMs;
    
    runtime->totalCycles++;
    
    // Update average cycle duration
    double total = runtime->avgCycleDurationMs * (runtime->totalCycles - 1);
    runtime->avgCycleDurationMs = (total + result.totalDurationMs) / runtime->totalCycles;
    
    printf("[CYCLE] Complete in %.2fms\n", result.totalDurationMs);
    
    // Cleanup context
    freeExecutionContext(&ctx);
    
    return result;
}

Trace* getLastTrace(SomniaRuntime* runtime) {
    (void)runtime;
    return &lastTrace;
}

// ============================================================================
// SNAPSHOT/RESTORE
// ============================================================================

char* snapshotState(SomniaRuntime* runtime) {
    size_t bufSize = 4096;
    char* buffer = (char*)malloc(bufSize);
    char* ptr = buffer;
    int remaining = (int)bufSize;
    int written;
    
    written = snprintf(ptr, remaining, "{\n  \"facts\": {\n");
    ptr += written; remaining -= written;
    
    for (int i = 0; i < runtime->worldState.count; i++) {
        Fact* f = &runtime->worldState.facts[i];
        written = snprintf(ptr, remaining, "    \"%s\": ", f->key->chars);
        ptr += written; remaining -= written;
        
        // Print value (simplified)
        if (IS_BOOL(f->value)) {
            written = snprintf(ptr, remaining, "%s", AS_BOOL(f->value) ? "true" : "false");
        } else if (IS_INT(f->value)) {
            written = snprintf(ptr, remaining, "%lld", (long long)AS_INT(f->value));
        } else if (IS_DOUBLE(f->value)) {
            written = snprintf(ptr, remaining, "%g", AS_DOUBLE(f->value));
        } else if (IS_STRING(f->value)) {
            written = snprintf(ptr, remaining, "\"%s\"", AS_CSTRING(f->value));
        } else {
            written = snprintf(ptr, remaining, "null");
        }
        ptr += written; remaining -= written;
        
        if (i < runtime->worldState.count - 1) {
            written = snprintf(ptr, remaining, ",\n");
        } else {
            written = snprintf(ptr, remaining, "\n");
        }
        ptr += written; remaining -= written;
    }
    
    written = snprintf(ptr, remaining, "  },\n  \"metrics\": {\n");
    ptr += written; remaining -= written;
    
    written = snprintf(ptr, remaining,
        "    \"total_cycles\": %llu,\n"
        "    \"total_proposals\": %llu,\n"
        "    \"total_executions\": %llu,\n"
        "    \"avg_cycle_duration_ms\": %.2f\n"
        "  }\n}\n",
        (unsigned long long)runtime->totalCycles,
        (unsigned long long)runtime->totalProposals,
        (unsigned long long)runtime->totalExecutions,
        runtime->avgCycleDurationMs);
    ptr += written; remaining -= written;
    
    return buffer;
}

bool restoreState(SomniaRuntime* runtime, const char* json) {
    // Simplified JSON parsing (would need proper JSON parser in production)
    (void)runtime;
    (void)json;
    printf("[RUNTIME] State restore not fully implemented\n");
    return false;
}
