#include "value.h"
#include "object.h"
#include "memory.h"
#include <stdio.h>

void initValueArray(ValueArray* array) {
    array->values = NULL;
    array->capacity = 0;
    array->count = 0;
}

void writeValueArray(ValueArray* array, Value value) {
    if (array->capacity < array->count + 1) {
        int oldCapacity = array->capacity;
        array->capacity = GROW_CAPACITY(oldCapacity);
        array->values = GROW_ARRAY(Value, array->values, oldCapacity, array->capacity);
    }
    
    array->values[array->count] = value;
    array->count++;
}

void freeValueArray(ValueArray* array) {
    FREE_ARRAY(Value, array->values, array->capacity);
    initValueArray(array);
}

void printValue(Value value) {
    switch (value.type) {
        case VAL_NULL:
            printf("null");
            break;
        case VAL_BOOL:
            printf(AS_BOOL(value) ? "true" : "false");
            break;
        case VAL_INT:
            printf("%lld", (long long)AS_INT(value));
            break;
        case VAL_DOUBLE:
            printf("%g", AS_DOUBLE(value));
            break;
        case VAL_OBJECT:
            printObject(value);
            break;
    }
}

bool valuesEqual(Value a, Value b) {
    if (a.type != b.type) return false;
    
    switch (a.type) {
        case VAL_NULL:   return true;
        case VAL_BOOL:   return AS_BOOL(a) == AS_BOOL(b);
        case VAL_INT:    return AS_INT(a) == AS_INT(b);
        case VAL_DOUBLE: return AS_DOUBLE(a) == AS_DOUBLE(b);
        case VAL_OBJECT: return AS_OBJ(a) == AS_OBJ(b);
        default:         return false;
    }
}

Value valueTruthy(Value value) {
    switch (value.type) {
        case VAL_NULL:   return BOOL_VAL(false);
        case VAL_BOOL:   return value;
        case VAL_INT:    return BOOL_VAL(AS_INT(value) != 0);
        case VAL_DOUBLE: return BOOL_VAL(AS_DOUBLE(value) != 0.0);
        case VAL_OBJECT: return BOOL_VAL(true);
        default:         return BOOL_VAL(false);
    }
}
