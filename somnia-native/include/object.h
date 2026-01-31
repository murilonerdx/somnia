#ifndef SOMNIA_OBJECT_H
#define SOMNIA_OBJECT_H

#include "common.h"
#include "value.h"
#include "chunk.h"

/**
 * Object Types
 * All heap-allocated values in Somnia.
 */
typedef enum {
    OBJ_STRING,
    OBJ_FUNCTION,
    OBJ_NATIVE,
    OBJ_CLOSURE,
    OBJ_UPVALUE,
    OBJ_CLASS,
    OBJ_INSTANCE,
    OBJ_BOUND_METHOD,
    OBJ_ARRAY,
    OBJ_MAP,
} ObjType;

/**
 * Base Object
 * All objects share this header for GC and type info.
 */
struct Obj {
    ObjType type;
    bool isMarked;      // GC mark bit
    struct Obj* next;   // Intrusive linked list for GC
};

/**
 * String Object
 * Interned, immutable strings.
 */
struct ObjString {
    Obj obj;
    int length;
    char* chars;
    uint32_t hash;      // Cached hash for fast table lookup
};

/**
 * Function Object
 * User-defined functions.
 */
struct ObjFunction {
    Obj obj;
    int arity;
    int upvalueCount;
    Chunk chunk;
    ObjString* name;
};

/**
 * Native Function
 */
typedef Value (*NativeFn)(int argCount, Value* args);

typedef struct {
    Obj obj;
    NativeFn function;
    const char* name;
    int arity;
} ObjNative;

/**
 * Upvalue (for closures)
 */
typedef struct ObjUpvalue {
    Obj obj;
    Value* location;
    Value closed;
    struct ObjUpvalue* next;
} ObjUpvalue;

/**
 * Closure
 */
typedef struct {
    Obj obj;
    ObjFunction* function;
    ObjUpvalue** upvalues;
    int upvalueCount;
} ObjClosure;

/**
 * Class
 */
struct ObjClass {
    Obj obj;
    ObjString* name;
    struct Table methods;
    struct ObjClass* superclass;
};

/**
 * Instance
 */
struct ObjInstance {
    Obj obj;
    ObjClass* klass;
    struct Table fields;
};

/**
 * Bound Method (method + instance)
 */
typedef struct {
    Obj obj;
    Value receiver;
    ObjClosure* method;
} ObjBoundMethod;

/**
 * Array
 */
typedef struct {
    Obj obj;
    ValueArray elements;
} ObjArray;

/**
 * Map (hash table)
 */
typedef struct {
    Obj obj;
    struct Table entries;
} ObjMap;

// Object type checking
#define OBJ_TYPE(value)        (AS_OBJ(value)->type)
#define IS_STRING(value)       isObjType(value, OBJ_STRING)
#define IS_FUNCTION(value)     isObjType(value, OBJ_FUNCTION)
#define IS_NATIVE(value)       isObjType(value, OBJ_NATIVE)
#define IS_CLOSURE(value)      isObjType(value, OBJ_CLOSURE)
#define IS_CLASS(value)        isObjType(value, OBJ_CLASS)
#define IS_INSTANCE(value)     isObjType(value, OBJ_INSTANCE)
#define IS_BOUND_METHOD(value) isObjType(value, OBJ_BOUND_METHOD)
#define IS_ARRAY(value)        isObjType(value, OBJ_ARRAY)
#define IS_MAP(value)          isObjType(value, OBJ_MAP)

// Object casting
#define AS_STRING(value)       ((ObjString*)AS_OBJ(value))
#define AS_CSTRING(value)      (((ObjString*)AS_OBJ(value))->chars)
#define AS_FUNCTION(value)     ((ObjFunction*)AS_OBJ(value))
#define AS_NATIVE(value)       ((ObjNative*)AS_OBJ(value))
#define AS_CLOSURE(value)      ((ObjClosure*)AS_OBJ(value))
#define AS_CLASS(value)        ((ObjClass*)AS_OBJ(value))
#define AS_INSTANCE(value)     ((ObjInstance*)AS_OBJ(value))
#define AS_BOUND_METHOD(value) ((ObjBoundMethod*)AS_OBJ(value))
#define AS_ARRAY(value)        ((ObjArray*)AS_OBJ(value))
#define AS_MAP(value)          ((ObjMap*)AS_OBJ(value))

static inline bool isObjType(Value value, ObjType type) {
    return IS_OBJ(value) && AS_OBJ(value)->type == type;
}

// Object creation
ObjString* takeString(char* chars, int length);
ObjString* copyString(const char* chars, int length);
ObjFunction* newFunction(void);
ObjNative* newNative(NativeFn function, const char* name, int arity);
ObjClosure* newClosure(ObjFunction* function);
ObjUpvalue* newUpvalue(Value* slot);
ObjClass* newClass(ObjString* name);
ObjInstance* newInstance(ObjClass* klass);
ObjBoundMethod* newBoundMethod(Value receiver, ObjClosure* method);
ObjArray* newArray(void);
ObjMap* newMap(void);

void printObject(Value value);

#endif // SOMNIA_OBJECT_H
