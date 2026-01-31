#ifndef SOMNIA_EGO_H
#define SOMNIA_EGO_H

#include "common.h"
#include "agent.h"
#include "id_engine.h"

/**
 * EGO - The Subconscious (Policy Layer)
 * 
 * Filters and selects proposals based on policies:
 * - forbid: block certain actions
 * - budget: rate limits
 * - select: choose top N
 */

// ============================================================================
// FORBID POLICY
// ============================================================================

typedef struct {
    ObjString* policyId;
    Condition* condition;       // When to forbid
    ObjString* actionPattern;   // Action to forbid (or NULL for any)
} ForbidPolicy;

typedef struct {
    ForbidPolicy* policies;
    int count;
    int capacity;
} ForbidPolicyArray;

// ============================================================================
// BUDGET POLICY
// ============================================================================

typedef struct {
    ObjString* actionName;
    int maxCount;
    int windowSeconds;          // per minute = 60, per second = 1
    int currentCount;
    uint64_t windowStart;
} BudgetPolicy;

typedef struct {
    BudgetPolicy* policies;
    int count;
    int capacity;
} BudgetPolicyArray;

// ============================================================================
// TIE BREAKER
// ============================================================================

typedef enum {
    TIE_RULE_ORDER,     // First rule wins
    TIE_ALPHABETICAL,   // Action name order
    TIE_HASH_BASED      // Deterministic hash
} TieBreaker;

// ============================================================================
// SELECTION RESULT
// ============================================================================

typedef struct {
    Proposal proposal;
    int rank;
    ObjString* reason;
} SelectedProposal;

typedef struct {
    Proposal proposal;
    ObjString* reason;
    ObjString* policyId;
} RejectedProposal;

typedef struct {
    SelectedProposal* selected;
    int selectedCount;
    RejectedProposal* rejected;
    int rejectedCount;
} SelectionResult;

// ============================================================================
// EGO CONFIG
// ============================================================================

typedef struct {
    int selectTopN;             // How many to select
    TieBreaker tieBreaker;
    double minWeight;           // Minimum weight threshold
} EgoConfig;

// ============================================================================
// EGO
// ============================================================================

typedef struct {
    ForbidPolicyArray forbidPolicies;
    BudgetPolicyArray budgetPolicies;
    EgoConfig config;
} Ego;

void initEgo(Ego* ego);
void freeEgo(Ego* ego);

void addForbidPolicy(Ego* ego, ForbidPolicy policy);
void addBudgetPolicy(Ego* ego, BudgetPolicy policy);
void setEgoConfig(Ego* ego, EgoConfig config);

SelectionResult select(Ego* ego, ProposalArray* proposals, ExecutionContext* ctx);
void freeSelectionResult(SelectionResult* result);

// Check if action is forbidden
bool isForbidden(Ego* ego, Proposal* proposal, ExecutionContext* ctx, ObjString** reason);

// Check if action exceeds budget
bool exceedsBudget(Ego* ego, Proposal* proposal, ObjString** reason);

// Reset budget windows (call periodically)
void resetBudgetWindows(Ego* ego, uint64_t currentTime);

#endif // SOMNIA_EGO_H
