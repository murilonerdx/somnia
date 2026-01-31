#include "agent.h"
#include "memory.h"
#include <stdlib.h>
#include <string.h>

// ============================================================================
// PROPOSAL ARRAY
// ============================================================================

void initProposalArray(ProposalArray* array) {
    array->proposals = NULL;
    array->count = 0;
    array->capacity = 0;
}

void writeProposalArray(ProposalArray* array, Proposal proposal) {
    if (array->capacity < array->count + 1) {
        int oldCapacity = array->capacity;
        array->capacity = GROW_CAPACITY(oldCapacity);
        array->proposals = (Proposal*)realloc(array->proposals, 
            sizeof(Proposal) * array->capacity);
    }
    array->proposals[array->count] = proposal;
    array->count++;
}

void freeProposalArray(ProposalArray* array) {
    for (int i = 0; i < array->count; i++) {
        freeTable(&array->proposals[i].args);
    }
    free(array->proposals);
    initProposalArray(array);
}

// Compare function for qsort (descending order by weight)
static int compareProposals(const void* a, const void* b) {
    const Proposal* pa = (const Proposal*)a;
    const Proposal* pb = (const Proposal*)b;
    if (pb->weight > pa->weight) return 1;
    if (pb->weight < pa->weight) return -1;
    return 0;
}

void sortProposalsByWeight(ProposalArray* array) {
    qsort(array->proposals, array->count, sizeof(Proposal), compareProposals);
}

// ============================================================================
// FACT ARRAY
// ============================================================================

void initFactArray(FactArray* array) {
    array->facts = NULL;
    array->count = 0;
    array->capacity = 0;
}

void writeFactArray(FactArray* array, Fact fact) {
    // Check if fact already exists, update if so
    for (int i = 0; i < array->count; i++) {
        if (strcmp(array->facts[i].key->chars, fact.key->chars) == 0) {
            array->facts[i].value = fact.value;
            return;
        }
    }
    
    // Add new fact
    if (array->capacity < array->count + 1) {
        int oldCapacity = array->capacity;
        array->capacity = GROW_CAPACITY(oldCapacity);
        array->facts = (Fact*)realloc(array->facts, 
            sizeof(Fact) * array->capacity);
    }
    array->facts[array->count] = fact;
    array->count++;
}

void freeFactArray(FactArray* array) {
    free(array->facts);
    initFactArray(array);
}

Value getFactValue(FactArray* array, const char* key) {
    for (int i = 0; i < array->count; i++) {
        if (strcmp(array->facts[i].key->chars, key) == 0) {
            return array->facts[i].value;
        }
    }
    return NULL_VAL;
}

// ============================================================================
// EXECUTION CONTEXT
// ============================================================================

void initExecutionContext(ExecutionContext* ctx) {
    ctx->intent.name = NULL;
    initTable(&ctx->intent.args);
    initFactArray(&ctx->facts);
    ctx->drives = NULL;
    ctx->driveCount = 0;
    ctx->affects = NULL;
    ctx->affectCount = 0;
    ctx->associations = NULL;
    ctx->associationCount = 0;
    ctx->seed = 0;
    ctx->timestamp = 0;
}

void freeExecutionContext(ExecutionContext* ctx) {
    freeTable(&ctx->intent.args);
    freeFactArray(&ctx->facts);
    free(ctx->drives);
    free(ctx->affects);
    free(ctx->associations);
    initExecutionContext(ctx);
}

// Add a drive to context
void addDrive(ExecutionContext* ctx, const char* name, double intensity) {
    ctx->driveCount++;
    ctx->drives = (Drive*)realloc(ctx->drives, sizeof(Drive) * ctx->driveCount);
    ctx->drives[ctx->driveCount - 1].name = copyString(name, (int)strlen(name));
    ctx->drives[ctx->driveCount - 1].intensity = intensity;
}

// Get drive intensity by name
double getDriveIntensity(ExecutionContext* ctx, const char* name) {
    for (int i = 0; i < ctx->driveCount; i++) {
        if (strcmp(ctx->drives[i].name->chars, name) == 0) {
            return ctx->drives[i].intensity;
        }
    }
    return 0.0;
}

// Add an affect to context
void addAffect(ExecutionContext* ctx, const char* name, double valence) {
    ctx->affectCount++;
    ctx->affects = (Affect*)realloc(ctx->affects, sizeof(Affect) * ctx->affectCount);
    ctx->affects[ctx->affectCount - 1].name = copyString(name, (int)strlen(name));
    ctx->affects[ctx->affectCount - 1].valence = valence;
}

// Get affect valence by name
double getAffectValence(ExecutionContext* ctx, const char* name) {
    for (int i = 0; i < ctx->affectCount; i++) {
        if (strcmp(ctx->affects[i].name->chars, name) == 0) {
            return ctx->affects[i].valence;
        }
    }
    return 0.0;
}

// ============================================================================
// CYCLE RESULT
// ============================================================================

void initCycleResult(CycleResult* result) {
    initProposalArray(&result->generatedProposals);
    initProposalArray(&result->selectedProposals);
    result->results = NULL;
    result->resultCount = 0;
    result->totalDurationMs = 0;
    result->traceId = NULL;
}

void freeCycleResult(CycleResult* result) {
    freeProposalArray(&result->generatedProposals);
    freeProposalArray(&result->selectedProposals);
    free(result->results);
    initCycleResult(result);
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

// Create a proposal
Proposal createProposal(const char* action, double weight, const char* ruleId, int line) {
    Proposal p;
    p.action = copyString(action, (int)strlen(action));
    initTable(&p.args);
    p.weight = weight;
    p.ruleId = copyString(ruleId, (int)strlen(ruleId));
    p.ruleLine = line;
    p.explanation = NULL;
    return p;
}

// Add argument to proposal
void addProposalArg(Proposal* p, const char* key, Value value) {
    ObjString* keyStr = copyString(key, (int)strlen(key));
    tableSet(&p->args, keyStr, value);
}

// Create an intent
Intent createIntent(const char* name) {
    Intent i;
    i.name = copyString(name, (int)strlen(name));
    initTable(&i.args);
    return i;
}

// Add argument to intent
void addIntentArg(Intent* i, const char* key, Value value) {
    ObjString* keyStr = copyString(key, (int)strlen(key));
    tableSet(&i->args, keyStr, value);
}

// Create a fact
Fact createFact(const char* key, Value value) {
    Fact f;
    f.key = copyString(key, (int)strlen(key));
    f.value = value;
    return f;
}
