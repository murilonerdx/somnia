#ifndef SOMNIA_RUNTIME_H
#define SOMNIA_RUNTIME_H

#include "common.h"
#include "agent.h"
#include "id_engine.h"
#include "ego.h"
#include "act.h"

/**
 * Somnia Runtime - Full Agent Cycle
 * 
 * Intent + Context → ID → EGO → ACT → Result
 */

// ============================================================================
// RUNTIME CONFIG
// ============================================================================

typedef struct {
    uint64_t seed;              // Random seed for determinism
    bool enableTracing;         // Generate traces
    bool deterministicMode;     // Same input = same output
    int maxCyclesPerRequest;    // Limit cycles
} RuntimeConfig;

// ============================================================================
// TRACE
// ============================================================================

typedef struct {
    ObjString* cycleId;
    uint64_t timestamp;
    Intent intent;
    
    // ID phase
    int rulesEvaluated;
    ProposalArray proposals;
    double idDurationMs;
    
    // EGO phase
    SelectionResult selection;
    double egoDurationMs;
    
    // ACT phase
    ActionResult* results;
    int resultCount;
    double actDurationMs;
    
    // Total
    double totalDurationMs;
} Trace;

void initTrace(Trace* trace);
void freeTrace(Trace* trace);
char* traceToJson(Trace* trace);

// ============================================================================
// RUNTIME
// ============================================================================

typedef struct {
    IdEngine id;
    Ego ego;
    Act act;
    RuntimeConfig config;
    
    // State
    FactArray worldState;
    Table memory;               // Persistent agent memory
    
    // Metrics
    uint64_t totalCycles;
    uint64_t totalProposals;
    uint64_t totalExecutions;
    double avgCycleDurationMs;
} SomniaRuntime;

void initRuntime(SomniaRuntime* runtime);
void freeRuntime(SomniaRuntime* runtime);
void setRuntimeConfig(SomniaRuntime* runtime, RuntimeConfig config);

// Load module from file
bool loadModule(SomniaRuntime* runtime, const char* path);

// Run a single cycle
CycleResult runCycle(SomniaRuntime* runtime, Intent intent, FactArray* facts);

// Convenience: run with JSON input
CycleResult runCycleJson(SomniaRuntime* runtime, const char* intentJson);

// Get trace for debugging
Trace* getLastTrace(SomniaRuntime* runtime);

// State management
void updateFact(SomniaRuntime* runtime, const char* key, Value value);
Value getFact(SomniaRuntime* runtime, const char* key);
void setMemory(SomniaRuntime* runtime, const char* key, Value value);
Value getMemory(SomniaRuntime* runtime, const char* key);

// Snapshot/restore for persistence
char* snapshotState(SomniaRuntime* runtime);
bool restoreState(SomniaRuntime* runtime, const char* json);

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

// Generate unique cycle ID
ObjString* generateCycleId(void);

// Get current timestamp in ms
uint64_t currentTimeMs(void);

// Deterministic random (using seed)
double deterministicRandom(uint64_t* seed);

#endif // SOMNIA_RUNTIME_H
