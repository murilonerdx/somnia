#ifndef SOMNIA_VM_H
#define SOMNIA_VM_H

#include "common.h"
#include "value.h"
#include "chunk.h"
#include "object.h"
#include "table.h"

/**
 * Call Frame
 * Represents a single function call.
 */
typedef struct {
    ObjClosure* closure;
    uint8_t* ip;            // Instruction pointer
    Value* slots;           // Stack window
} CallFrame;

/**
 * Somnia Virtual Machine
 * The heart of the Somnia runtime.
 */
typedef struct {
    // Call stack
    CallFrame frames[FRAMES_MAX];
    int frameCount;
    
    // Value stack
    Value stack[STACK_MAX];
    Value* stackTop;
    
    // Global variables
    Table globals;
    
    // String interning
    Table strings;
    
    // Special strings
    ObjString* initString;
    
    // Upvalues
    ObjUpvalue* openUpvalues;
    
    // GC
    Obj* objects;           // Linked list of all objects
    int grayCount;
    int grayCapacity;
    Obj** grayStack;
    
    // GC metrics
    size_t bytesAllocated;
    size_t nextGC;
} VM;

/**
 * Interpretation result
 */
typedef enum {
    INTERPRET_OK,
    INTERPRET_COMPILE_ERROR,
    INTERPRET_RUNTIME_ERROR,
} InterpretResult;

// Global VM instance
extern VM vm;

// VM lifecycle
void initVM(void);
void freeVM(void);

// Execution
InterpretResult interpret(const char* source);
InterpretResult interpretFile(const char* path);

// Stack operations
void push(Value value);
Value pop(void);
Value peek(int distance);

// Native function registration
void defineNative(const char* name, NativeFn function, int arity);

// Error reporting
void runtimeError(const char* format, ...);

#endif // SOMNIA_VM_H
