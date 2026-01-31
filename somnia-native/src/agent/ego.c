#include "ego.h"
#include "memory.h"
#include <stdlib.h>
#include <string.h>

// ============================================================================
// EGO INITIALIZATION
// ============================================================================

void initEgo(Ego* ego) {
    ego->forbidPolicies.policies = NULL;
    ego->forbidPolicies.count = 0;
    ego->forbidPolicies.capacity = 0;
    
    ego->budgetPolicies.policies = NULL;
    ego->budgetPolicies.count = 0;
    ego->budgetPolicies.capacity = 0;
    
    ego->config.selectTopN = 1;
    ego->config.tieBreaker = TIE_RULE_ORDER;
    ego->config.minWeight = 0.0;
}

void freeEgo(Ego* ego) {
    for (int i = 0; i < ego->forbidPolicies.count; i++) {
        freeCondition(ego->forbidPolicies.policies[i].condition);
    }
    free(ego->forbidPolicies.policies);
    free(ego->budgetPolicies.policies);
    initEgo(ego);
}

// ============================================================================
// POLICY MANAGEMENT
// ============================================================================

void addForbidPolicy(Ego* ego, ForbidPolicy policy) {
    ForbidPolicyArray* arr = &ego->forbidPolicies;
    if (arr->capacity < arr->count + 1) {
        int oldCapacity = arr->capacity;
        arr->capacity = GROW_CAPACITY(oldCapacity);
        arr->policies = (ForbidPolicy*)realloc(arr->policies, 
            sizeof(ForbidPolicy) * arr->capacity);
    }
    arr->policies[arr->count] = policy;
    arr->count++;
}

void addBudgetPolicy(Ego* ego, BudgetPolicy policy) {
    BudgetPolicyArray* arr = &ego->budgetPolicies;
    if (arr->capacity < arr->count + 1) {
        int oldCapacity = arr->capacity;
        arr->capacity = GROW_CAPACITY(oldCapacity);
        arr->policies = (BudgetPolicy*)realloc(arr->policies, 
            sizeof(BudgetPolicy) * arr->capacity);
    }
    arr->policies[arr->count] = policy;
    arr->count++;
}

void setEgoConfig(Ego* ego, EgoConfig config) {
    ego->config = config;
}

// ============================================================================
// FORBIDDEN CHECK
// ============================================================================

bool isForbidden(Ego* ego, Proposal* proposal, ExecutionContext* ctx, ObjString** reason) {
    for (int i = 0; i < ego->forbidPolicies.count; i++) {
        ForbidPolicy* policy = &ego->forbidPolicies.policies[i];
        
        // Check if action matches pattern (NULL = any action)
        if (policy->actionPattern != NULL) {
            if (strcmp(policy->actionPattern->chars, proposal->action->chars) != 0) {
                continue;  // Action doesn't match, skip this policy
            }
        }
        
        // Check if condition matches
        if (evaluateCondition(policy->condition, ctx)) {
            if (reason != NULL) {
                *reason = policy->policyId;
            }
            return true;
        }
    }
    return false;
}

// ============================================================================
// BUDGET CHECK
// ============================================================================

bool exceedsBudget(Ego* ego, Proposal* proposal, ObjString** reason) {
    for (int i = 0; i < ego->budgetPolicies.count; i++) {
        BudgetPolicy* policy = &ego->budgetPolicies.policies[i];
        
        // Check if action matches
        if (strcmp(policy->actionName->chars, proposal->action->chars) != 0) {
            continue;
        }
        
        // Check if over budget
        if (policy->currentCount >= policy->maxCount) {
            if (reason != NULL) {
                *reason = policy->actionName;
            }
            return true;
        }
    }
    return false;
}

// Increment budget counter for an action
static void incrementBudget(Ego* ego, const char* actionName) {
    for (int i = 0; i < ego->budgetPolicies.count; i++) {
        BudgetPolicy* policy = &ego->budgetPolicies.policies[i];
        if (strcmp(policy->actionName->chars, actionName) == 0) {
            policy->currentCount++;
            return;
        }
    }
}

void resetBudgetWindows(Ego* ego, uint64_t currentTime) {
    for (int i = 0; i < ego->budgetPolicies.count; i++) {
        BudgetPolicy* policy = &ego->budgetPolicies.policies[i];
        uint64_t windowMs = (uint64_t)policy->windowSeconds * 1000;
        
        if (currentTime - policy->windowStart >= windowMs) {
            policy->currentCount = 0;
            policy->windowStart = currentTime;
        }
    }
}

// ============================================================================
// TIE BREAKING
// ============================================================================

static int tieBreakCompare(const Proposal* a, const Proposal* b, TieBreaker method) {
    switch (method) {
        case TIE_RULE_ORDER:
            // Earlier rule wins (lower line number)
            return a->ruleLine - b->ruleLine;
            
        case TIE_ALPHABETICAL:
            // Alphabetical by action name
            return strcmp(a->action->chars, b->action->chars);
            
        case TIE_HASH_BASED: {
            // Deterministic hash
            uint32_t hashA = a->action->hash ^ (uint32_t)a->ruleLine;
            uint32_t hashB = b->action->hash ^ (uint32_t)b->ruleLine;
            return (int)(hashA - hashB);
        }
        
        default:
            return 0;
    }
}

// ============================================================================
// SELECTION
// ============================================================================

SelectionResult select(Ego* ego, ProposalArray* proposals, ExecutionContext* ctx) {
    SelectionResult result;
    result.selected = NULL;
    result.selectedCount = 0;
    result.rejected = NULL;
    result.rejectedCount = 0;
    
    // Allocate max possible
    result.selected = (SelectedProposal*)malloc(sizeof(SelectedProposal) * proposals->count);
    result.rejected = (RejectedProposal*)malloc(sizeof(RejectedProposal) * proposals->count);
    
    int selectedIdx = 0;
    int rejectedIdx = 0;
    
    for (int i = 0; i < proposals->count; i++) {
        Proposal* p = &proposals->proposals[i];
        ObjString* reason = NULL;
        
        // Check minimum weight
        if (p->weight < ego->config.minWeight) {
            result.rejected[rejectedIdx].proposal = *p;
            result.rejected[rejectedIdx].reason = copyString("Low weight", 10);
            result.rejected[rejectedIdx].policyId = NULL;
            rejectedIdx++;
            continue;
        }
        
        // Check forbid policies
        if (isForbidden(ego, p, ctx, &reason)) {
            result.rejected[rejectedIdx].proposal = *p;
            result.rejected[rejectedIdx].reason = copyString("Forbidden", 9);
            result.rejected[rejectedIdx].policyId = reason;
            rejectedIdx++;
            continue;
        }
        
        // Check budget
        if (exceedsBudget(ego, p, &reason)) {
            result.rejected[rejectedIdx].proposal = *p;
            result.rejected[rejectedIdx].reason = copyString("Budget exceeded", 15);
            result.rejected[rejectedIdx].policyId = reason;
            rejectedIdx++;
            continue;
        }
        
        // Check if we've hit top N
        if (selectedIdx >= ego->config.selectTopN) {
            result.rejected[rejectedIdx].proposal = *p;
            result.rejected[rejectedIdx].reason = copyString("Not selected", 12);
            result.rejected[rejectedIdx].policyId = NULL;
            rejectedIdx++;
            continue;
        }
        
        // Handle tie-breaking with previous proposals at same weight
        if (selectedIdx > 0) {
            SelectedProposal* last = &result.selected[selectedIdx - 1];
            if (last->proposal.weight == p->weight) {
                // Apply tie-breaker
                int cmp = tieBreakCompare(&last->proposal, p, ego->config.tieBreaker);
                if (cmp > 0) {
                    // Swap: new proposal ranks higher
                    Proposal temp = last->proposal;
                    last->proposal = *p;
                    
                    // Reject the displaced proposal
                    result.rejected[rejectedIdx].proposal = temp;
                    result.rejected[rejectedIdx].reason = copyString("Tie-break loss", 14);
                    result.rejected[rejectedIdx].policyId = NULL;
                    rejectedIdx++;
                    continue;
                }
            }
        }
        
        // Select this proposal
        result.selected[selectedIdx].proposal = *p;
        result.selected[selectedIdx].rank = selectedIdx + 1;
        result.selected[selectedIdx].reason = copyString("Selected", 8);
        
        // Increment budget
        incrementBudget(ego, p->action->chars);
        
        selectedIdx++;
    }
    
    result.selectedCount = selectedIdx;
    result.rejectedCount = rejectedIdx;
    
    return result;
}

void freeSelectionResult(SelectionResult* result) {
    free(result->selected);
    free(result->rejected);
    result->selected = NULL;
    result->rejected = NULL;
    result->selectedCount = 0;
    result->rejectedCount = 0;
}

// ============================================================================
// HELPER: CREATE POLICIES
// ============================================================================

ForbidPolicy createForbidPolicy(const char* id, Condition* cond, const char* action) {
    ForbidPolicy p;
    p.policyId = copyString(id, (int)strlen(id));
    p.condition = cond;
    p.actionPattern = action ? copyString(action, (int)strlen(action)) : NULL;
    return p;
}

BudgetPolicy createBudgetPolicy(const char* action, int maxCount, int windowSeconds) {
    BudgetPolicy p;
    p.actionName = copyString(action, (int)strlen(action));
    p.maxCount = maxCount;
    p.windowSeconds = windowSeconds;
    p.currentCount = 0;
    p.windowStart = 0;
    return p;
}
