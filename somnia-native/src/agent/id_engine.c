#include "id_engine.h"
#include "memory.h"
#include <stdlib.h>
#include <string.h>

// ============================================================================
// CONDITION CREATION
// ============================================================================

static Condition* allocCondition(ConditionType type) {
    Condition* cond = (Condition*)malloc(sizeof(Condition));
    cond->type = type;
    return cond;
}

Condition* createIntentCondition(ObjString* name) {
    Condition* cond = allocCondition(COND_INTENT);
    cond->as.intent.intentName = name;
    return cond;
}

Condition* createFactCondition(ObjString* key) {
    Condition* cond = allocCondition(COND_FACT);
    cond->as.fact.factKey = key;
    cond->as.fact.checkValue = false;
    return cond;
}

Condition* createFactValueCondition(ObjString* key, Value expected) {
    Condition* cond = allocCondition(COND_FACT_VALUE);
    cond->as.fact.factKey = key;
    cond->as.fact.expectedValue = expected;
    cond->as.fact.checkValue = true;
    return cond;
}

Condition* createDriveCondition(ObjString* name, double threshold, bool gt) {
    Condition* cond = allocCondition(COND_DRIVE);
    cond->as.drive.driveName = name;
    cond->as.drive.threshold = threshold;
    cond->as.drive.greaterThan = gt;
    return cond;
}

Condition* createAffectCondition(ObjString* name, double threshold, bool gt) {
    Condition* cond = allocCondition(COND_AFFECT);
    cond->as.affect.affectName = name;
    cond->as.affect.threshold = threshold;
    cond->as.affect.greaterThan = gt;
    return cond;
}

Condition* createAndCondition(Condition* left, Condition* right) {
    Condition* cond = allocCondition(COND_AND);
    cond->as.binary.left = left;
    cond->as.binary.right = right;
    return cond;
}

Condition* createOrCondition(Condition* left, Condition* right) {
    Condition* cond = allocCondition(COND_OR);
    cond->as.binary.left = left;
    cond->as.binary.right = right;
    return cond;
}

Condition* createNotCondition(Condition* operand) {
    Condition* cond = allocCondition(COND_NOT);
    cond->as.unary.operand = operand;
    return cond;
}

void freeCondition(Condition* cond) {
    if (cond == NULL) return;
    
    switch (cond->type) {
        case COND_AND:
        case COND_OR:
            freeCondition(cond->as.binary.left);
            freeCondition(cond->as.binary.right);
            break;
        case COND_NOT:
            freeCondition(cond->as.unary.operand);
            break;
        default:
            break;
    }
    free(cond);
}

// ============================================================================
// CONDITION EVALUATION
// ============================================================================

bool evaluateCondition(Condition* cond, ExecutionContext* ctx) {
    if (cond == NULL) return false;
    
    switch (cond->type) {
        case COND_TRUE:
            return true;
            
        case COND_FALSE:
            return false;
            
        case COND_INTENT: {
            if (ctx->intent.name == NULL) return false;
            return strcmp(ctx->intent.name->chars, 
                         cond->as.intent.intentName->chars) == 0;
        }
        
        case COND_FACT: {
            Value val = getFactValue(&ctx->facts, cond->as.fact.factKey->chars);
            // Fact exists if it's not null
            return !IS_NULL(val);
        }
        
        case COND_FACT_VALUE: {
            Value val = getFactValue(&ctx->facts, cond->as.fact.factKey->chars);
            return valuesEqual(val, cond->as.fact.expectedValue);
        }
        
        case COND_DRIVE: {
            double intensity = getDriveIntensity(ctx, cond->as.drive.driveName->chars);
            if (cond->as.drive.greaterThan) {
                return intensity > cond->as.drive.threshold;
            } else {
                return intensity < cond->as.drive.threshold;
            }
        }
        
        case COND_AFFECT: {
            double valence = getAffectValence(ctx, cond->as.affect.affectName->chars);
            if (cond->as.affect.greaterThan) {
                return valence > cond->as.affect.threshold;
            } else {
                return valence < cond->as.affect.threshold;
            }
        }
        
        case COND_AND:
            return evaluateCondition(cond->as.binary.left, ctx) &&
                   evaluateCondition(cond->as.binary.right, ctx);
                   
        case COND_OR:
            return evaluateCondition(cond->as.binary.left, ctx) ||
                   evaluateCondition(cond->as.binary.right, ctx);
                   
        case COND_NOT:
            return !evaluateCondition(cond->as.unary.operand, ctx);
            
        default:
            return false;
    }
}

// ============================================================================
// RULE ARRAY
// ============================================================================

void initRuleArray(RuleArray* array) {
    array->rules = NULL;
    array->count = 0;
    array->capacity = 0;
}

void writeRuleArray(RuleArray* array, Rule rule) {
    if (array->capacity < array->count + 1) {
        int oldCapacity = array->capacity;
        array->capacity = GROW_CAPACITY(oldCapacity);
        array->rules = (Rule*)realloc(array->rules, sizeof(Rule) * array->capacity);
    }
    array->rules[array->count] = rule;
    array->count++;
}

void freeRuleArray(RuleArray* array) {
    for (int i = 0; i < array->count; i++) {
        freeCondition(array->rules[i].condition);
        freeTable(&array->rules[i].actionArgs);
    }
    free(array->rules);
    initRuleArray(array);
}

// ============================================================================
// ID ENGINE
// ============================================================================

void initIdEngine(IdEngine* engine) {
    initRuleArray(&engine->rules);
    initTable(&engine->ruleIndex);
}

void freeIdEngine(IdEngine* engine) {
    freeRuleArray(&engine->rules);
    freeTable(&engine->ruleIndex);
}

void addRule(IdEngine* engine, Rule rule) {
    writeRuleArray(&engine->rules, rule);
}

// Create a rule
Rule createRule(const char* id, int line, Condition* condition, 
                const char* action, double weight) {
    Rule r;
    r.id = copyString(id, (int)strlen(id));
    r.line = line;
    r.condition = condition;
    r.action = copyString(action, (int)strlen(action));
    initTable(&r.actionArgs);
    r.baseWeight = weight;
    return r;
}

// Add argument to rule's action
void addRuleActionArg(Rule* r, const char* key, Value value) {
    ObjString* keyStr = copyString(key, (int)strlen(key));
    tableSet(&r->actionArgs, keyStr, value);
}

// ============================================================================
// PROPOSAL GENERATION
// ============================================================================

ProposalArray evaluate(IdEngine* engine, ExecutionContext* ctx) {
    ProposalArray proposals;
    initProposalArray(&proposals);
    
    // Evaluate each rule
    for (int i = 0; i < engine->rules.count; i++) {
        Rule* rule = &engine->rules.rules[i];
        
        // Check if condition matches
        if (evaluateCondition(rule->condition, ctx)) {
            // Create proposal
            Proposal p;
            p.action = rule->action;
            
            // Copy action args
            initTable(&p.args);
            tableAddAll(&rule->actionArgs, &p.args);
            
            // Calculate weight (could be modified by drives/affects)
            double weight = rule->baseWeight;
            
            // Apply drive modifiers (example: if efficiency drive is high, boost weight)
            // This is a simple implementation, could be made more sophisticated
            for (int d = 0; d < ctx->driveCount; d++) {
                // If action aligns with drive, boost weight slightly
                weight *= (1.0 + ctx->drives[d].intensity * 0.1);
            }
            
            // Clamp weight
            if (weight > 1.0) weight = 1.0;
            
            p.weight = weight;
            p.ruleId = rule->id;
            p.ruleLine = rule->line;
            p.explanation = NULL;
            
            writeProposalArray(&proposals, p);
        }
    }
    
    // Sort by weight (descending)
    sortProposalsByWeight(&proposals);
    
    return proposals;
}
