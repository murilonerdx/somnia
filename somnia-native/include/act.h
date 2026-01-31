#ifndef SOMNIA_ACT_H
#define SOMNIA_ACT_H

#include "common.h"
#include "agent.h"
#include "value.h"

/**
 * ACT - The Conscious (Action Execution)
 * 
 * Executes selected actions with:
 * - Timeout
 * - Retry with backoff
 * - Cancellation
 */

// ============================================================================
// ACTION HANDLER
// ============================================================================

// Function pointer type for action handlers
typedef ActionResult (*ActionHandler)(Table* args, void* userData);

typedef struct {
    ObjString* name;
    ActionHandler handler;
    void* userData;
    int timeoutMs;
    int maxRetries;
    bool retryable;
} ActionDef;

typedef struct {
    ActionDef* actions;
    int count;
    int capacity;
} ActionRegistry;

void initActionRegistry(ActionRegistry* registry);
void freeActionRegistry(ActionRegistry* registry);
void registerAction(ActionRegistry* registry, ActionDef action);
ActionDef* findAction(ActionRegistry* registry, const char* name);

// ============================================================================
// EXECUTION CONFIG
// ============================================================================

typedef struct {
    int defaultTimeoutMs;
    int maxConcurrent;
    bool cancelOnError;
} ActConfig;

// ============================================================================
// ACT
// ============================================================================

typedef struct {
    ActionRegistry registry;
    ActConfig config;
} Act;

void initAct(Act* act);
void freeAct(Act* act);
void setActConfig(Act* act, ActConfig config);

// Execute a single proposal
ActionResult executeProposal(Act* act, Proposal* proposal);

// Execute multiple proposals
ActionResult* executeAll(Act* act, SelectedProposal* proposals, int count, int* resultCount);

// ============================================================================
// BUILT-IN ACTIONS
// ============================================================================

// log(level, message)
ActionResult actionLog(Table* args, void* userData);

// sleep(ms)
ActionResult actionSleep(Table* args, void* userData);

// http.get(url)
ActionResult actionHttpGet(Table* args, void* userData);

// http.post(url, body)
ActionResult actionHttpPost(Table* args, void* userData);

// respond(status, body) - for web host
ActionResult actionRespond(Table* args, void* userData);

// Register all built-in actions
void registerBuiltinActions(Act* act);

#endif // SOMNIA_ACT_H
