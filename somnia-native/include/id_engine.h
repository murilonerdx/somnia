#ifndef SOMNIA_ID_ENGINE_H
#define SOMNIA_ID_ENGINE_H

#include "common.h"
#include "agent.h"
#include "table.h"

/**
 * ID Engine - The Unconscious
 * 
 * Evaluates rules against context and generates proposals.
 * Rules have the form: when <condition> => propose <action> @weight
 */

// ============================================================================
// CONDITION TYPES
// ============================================================================

typedef enum {
    COND_INTENT,        // intent("name")
    COND_FACT,          // fact("key")
    COND_FACT_VALUE,    // fact("key") == value
    COND_DRIVE,         // drive(name) > threshold
    COND_AFFECT,        // affect(name) > threshold
    COND_AND,           // cond1 and cond2
    COND_OR,            // cond1 or cond2
    COND_NOT,           // not cond
    COND_TRUE,          // always true
    COND_FALSE          // always false
} ConditionType;

typedef struct Condition {
    ConditionType type;
    union {
        struct {
            ObjString* intentName;
        } intent;
        struct {
            ObjString* factKey;
            Value expectedValue;
            bool checkValue;
        } fact;
        struct {
            ObjString* driveName;
            double threshold;
            bool greaterThan;
        } drive;
        struct {
            ObjString* affectName;
            double threshold;
            bool greaterThan;
        } affect;
        struct {
            struct Condition* left;
            struct Condition* right;
        } binary;
        struct {
            struct Condition* operand;
        } unary;
    } as;
} Condition;

Condition* createIntentCondition(ObjString* name);
Condition* createFactCondition(ObjString* key);
Condition* createFactValueCondition(ObjString* key, Value expected);
Condition* createDriveCondition(ObjString* name, double threshold, bool gt);
Condition* createAffectCondition(ObjString* name, double threshold, bool gt);
Condition* createAndCondition(Condition* left, Condition* right);
Condition* createOrCondition(Condition* left, Condition* right);
Condition* createNotCondition(Condition* operand);
void freeCondition(Condition* cond);
bool evaluateCondition(Condition* cond, ExecutionContext* ctx);

// ============================================================================
// RULE
// ============================================================================

typedef struct {
    ObjString* id;              // Unique rule ID
    int line;                   // Source line
    Condition* condition;       // When clause
    ObjString* action;          // Action to propose
    Table actionArgs;           // Action arguments
    double baseWeight;          // Base weight @0.8
} Rule;

typedef struct {
    Rule* rules;
    int count;
    int capacity;
} RuleArray;

void initRuleArray(RuleArray* array);
void writeRuleArray(RuleArray* array, Rule rule);
void freeRuleArray(RuleArray* array);

// ============================================================================
// ID ENGINE
// ============================================================================

typedef struct {
    RuleArray rules;
    Table ruleIndex;    // Index by action name for fast lookup
} IdEngine;

void initIdEngine(IdEngine* engine);
void freeIdEngine(IdEngine* engine);
void addRule(IdEngine* engine, Rule rule);
ProposalArray evaluate(IdEngine* engine, ExecutionContext* ctx);

#endif // SOMNIA_ID_ENGINE_H
