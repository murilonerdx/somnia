#ifndef SOMNIA_VALUE_H
#define SOMNIA_VALUE_H

#include "common.h"

// Forward declarations
typedef struct Obj Obj;
typedef struct ObjString ObjString;
typedef struct ObjFunction ObjFunction;
typedef struct ObjClass ObjClass;
typedef struct ObjInstance ObjInstance;

/**
 * Value Types
 * Somnia has a dynamic type system with these base types.
 */
typedef enum {
    VAL_NULL,
    VAL_BOOL,
    VAL_INT,
    VAL_DOUBLE,
    VAL_OBJECT,  // Heap-allocated objects (strings, classes, etc.)
} ValueType;

/**
 * Value
 * A tagged union representing any Somnia value.
 * Uses NaN-boxing optimization for 64-bit efficiency.
 */
typedef struct {
    ValueType type;
    union {
        bool boolean;
        int64_t integer;
        double number;
        Obj* obj;
    } as;
} Value;

// Value constructors
#define NULL_VAL           ((Value){VAL_NULL, {.integer = 0}})
#define BOOL_VAL(value)    ((Value){VAL_BOOL, {.boolean = value}})
#define INT_VAL(value)     ((Value){VAL_INT, {.integer = value}})
#define DOUBLE_VAL(value)  ((Value){VAL_DOUBLE, {.number = value}})
#define OBJ_VAL(object)    ((Value){VAL_OBJECT, {.obj = (Obj*)object}})

// Value accessors
#define AS_BOOL(value)     ((value).as.boolean)
#define AS_INT(value)      ((value).as.integer)
#define AS_DOUBLE(value)   ((value).as.number)
#define AS_OBJ(value)      ((value).as.obj)

// Type checking
#define IS_NULL(value)     ((value).type == VAL_NULL)
#define IS_BOOL(value)     ((value).type == VAL_BOOL)
#define IS_INT(value)      ((value).type == VAL_INT)
#define IS_DOUBLE(value)   ((value).type == VAL_DOUBLE)
#define IS_NUMBER(value)   (IS_INT(value) || IS_DOUBLE(value))
#define IS_OBJ(value)      ((value).type == VAL_OBJECT)

// Convert to double for arithmetic
static inline double valueToDouble(Value v) {
    if (IS_INT(v)) return (double)AS_INT(v);
    if (IS_DOUBLE(v)) return AS_DOUBLE(v);
    return 0.0;
}

/**
 * Dynamic array of Values
 */
typedef struct {
    int capacity;
    int count;
    Value* values;
} ValueArray;

void initValueArray(ValueArray* array);
void writeValueArray(ValueArray* array, Value value);
void freeValueArray(ValueArray* array);

// Value operations
void printValue(Value value);
bool valuesEqual(Value a, Value b);
Value valueTruthy(Value value);

#endif // SOMNIA_VALUE_H
