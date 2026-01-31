#include "vm.h"
#include "memory.h"
#include "object.h"
#include "compiler/compiler.h"
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <time.h>
#include <math.h>

// Global VM instance
VM vm;

// Forward declarations
static InterpretResult run(void);
static bool callValue(Value callee, int argCount);
static bool call(ObjClosure* closure, int argCount);
static ObjUpvalue* captureUpvalue(Value* local);
static void closeUpvalues(Value* last);
static void defineMethod(ObjString* name);
static bool bindMethod(ObjClass* klass, ObjString* name);
static bool invokeFromClass(ObjClass* klass, ObjString* name, int argCount);
static bool invoke(ObjString* name, int argCount);

// Built-in native functions
static Value clockNative(int argCount, Value* args) {
    (void)argCount; (void)args;
    return DOUBLE_VAL((double)clock() / CLOCKS_PER_SEC);
}

static Value printNative(int argCount, Value* args) {
    for (int i = 0; i < argCount; i++) {
        if (i > 0) printf(" ");
        printValue(args[i]);
    }
    return NULL_VAL;
}

static Value printlnNative(int argCount, Value* args) {
    for (int i = 0; i < argCount; i++) {
        if (i > 0) printf(" ");
        printValue(args[i]);
    }
    printf("\n");
    return NULL_VAL;
}

static Value typeNative(int argCount, Value* args) {
    if (argCount != 1) return NULL_VAL;
    
    Value v = args[0];
    const char* name;
    switch (v.type) {
        case VAL_NULL:   name = "null"; break;
        case VAL_BOOL:   name = "bool"; break;
        case VAL_INT:    name = "int"; break;
        case VAL_DOUBLE: name = "double"; break;
        case VAL_OBJECT: {
            switch (OBJ_TYPE(v)) {
                case OBJ_STRING: name = "string"; break;
                case OBJ_FUNCTION:
                case OBJ_CLOSURE:
                case OBJ_NATIVE: name = "function"; break;
                case OBJ_CLASS: name = "class"; break;
                case OBJ_INSTANCE: name = AS_INSTANCE(v)->klass->name->chars; break;
                case OBJ_ARRAY: name = "array"; break;
                case OBJ_MAP: name = "map"; break;
                default: name = "object"; break;
            }
            break;
        }
        default: name = "unknown"; break;
    }
    return OBJ_VAL(copyString(name, (int)strlen(name)));
}

static Value lenNative(int argCount, Value* args) {
    if (argCount != 1) return INT_VAL(0);
    
    Value v = args[0];
    if (IS_STRING(v)) return INT_VAL(AS_STRING(v)->length);
    if (IS_ARRAY(v)) return INT_VAL(AS_ARRAY(v)->elements.count);
    return INT_VAL(0);
}

static Value sqrtNative(int argCount, Value* args) {
    if (argCount != 1) return DOUBLE_VAL(0);
    return DOUBLE_VAL(sqrt(valueToDouble(args[0])));
}

static Value absNative(int argCount, Value* args) {
    if (argCount != 1) return DOUBLE_VAL(0);
    double v = valueToDouble(args[0]);
    return DOUBLE_VAL(v < 0 ? -v : v);
}

static Value gcRunNative(int argCount, Value* args) {
    (void)argCount; (void)args;
    collectGarbage();
    return NULL_VAL;
}

static Value memoryUsedNative(int argCount, Value* args) {
    (void)argCount; (void)args;
    return INT_VAL((int64_t)getGCMetrics()->bytesAllocated);
}

void initVM(void) {
    vm.stackTop = vm.stack;
    vm.frameCount = 0;
    vm.objects = NULL;
    vm.bytesAllocated = 0;
    vm.nextGC = 1024 * 1024;  // 1MB initial threshold
    
    vm.grayCount = 0;
    vm.grayCapacity = 0;
    vm.grayStack = NULL;
    
    vm.openUpvalues = NULL;
    
    initTable(&vm.globals);
    initTable(&vm.strings);
    
    vm.initString = NULL;
    vm.initString = copyString("init", 4);
    
    // Register native functions
    defineNative("clock", clockNative, 0);
    defineNative("print", printNative, -1);
    defineNative("println", printlnNative, -1);
    defineNative("type", typeNative, 1);
    defineNative("len", lenNative, 1);
    defineNative("sqrt", sqrtNative, 1);
    defineNative("abs", absNative, 1);
    defineNative("gc", gcRunNative, 0);
    defineNative("memoryUsed", memoryUsedNative, 0);
}

void freeVM(void) {
    freeTable(&vm.globals);
    freeTable(&vm.strings);
    vm.initString = NULL;
    
    // Free all objects
    Obj* object = vm.objects;
    while (object != NULL) {
        Obj* next = object->next;
        freeObject(object);
        object = next;
    }
    
    free(vm.grayStack);
}

void push(Value value) {
    *vm.stackTop = value;
    vm.stackTop++;
}

Value pop(void) {
    vm.stackTop--;
    return *vm.stackTop;
}

Value peek(int distance) {
    return vm.stackTop[-1 - distance];
}

void defineNative(const char* name, NativeFn function, int arity) {
    push(OBJ_VAL(copyString(name, (int)strlen(name))));
    push(OBJ_VAL(newNative(function, name, arity)));
    tableSet(&vm.globals, AS_STRING(vm.stack[0]), vm.stack[1]);
    pop();
    pop();
}

void runtimeError(const char* format, ...) {
    va_list args;
    va_start(args, format);
    vfprintf(stderr, format, args);
    va_end(args);
    fputs("\n", stderr);
    
    // Stack trace
    for (int i = vm.frameCount - 1; i >= 0; i--) {
        CallFrame* frame = &vm.frames[i];
        ObjFunction* function = frame->closure->function;
        size_t instruction = frame->ip - function->chunk.code - 1;
        fprintf(stderr, "[line %d] in ", function->chunk.lines[instruction]);
        if (function->name == NULL) {
            fprintf(stderr, "script\n");
        } else {
            fprintf(stderr, "%s()\n", function->name->chars);
        }
    }
    
    vm.stackTop = vm.stack;
    vm.frameCount = 0;
}

InterpretResult interpret(const char* source) {
    ObjFunction* function = compile(source);
    if (function == NULL) return INTERPRET_COMPILE_ERROR;
    
    push(OBJ_VAL(function));
    ObjClosure* closure = newClosure(function);
    pop();
    push(OBJ_VAL(closure));
    call(closure, 0);
    
    return run();
}

InterpretResult interpretFile(const char* path) {
    FILE* file = fopen(path, "rb");
    if (file == NULL) {
        fprintf(stderr, "[Somnia] Error: Could not open file '%s'\n", path);
        return INTERPRET_COMPILE_ERROR;
    }
    
    fseek(file, 0L, SEEK_END);
    size_t fileSize = ftell(file);
    rewind(file);
    
    char* buffer = (char*)malloc(fileSize + 1);
    if (buffer == NULL) {
        fprintf(stderr, "[Somnia] Error: Not enough memory to read file\n");
        fclose(file);
        return INTERPRET_COMPILE_ERROR;
    }
    
    size_t bytesRead = fread(buffer, sizeof(char), fileSize, file);
    buffer[bytesRead] = '\0';
    fclose(file);
    
    InterpretResult result = interpret(buffer);
    free(buffer);
    return result;
}

static bool call(ObjClosure* closure, int argCount) {
    if (closure->function->arity != -1 && argCount != closure->function->arity) {
        runtimeError("Expected %d arguments but got %d.", closure->function->arity, argCount);
        return false;
    }
    
    if (vm.frameCount == FRAMES_MAX) {
        runtimeError("Stack overflow.");
        return false;
    }
    
    CallFrame* frame = &vm.frames[vm.frameCount++];
    frame->closure = closure;
    frame->ip = closure->function->chunk.code;
    frame->slots = vm.stackTop - argCount - 1;
    return true;
}

static bool callValue(Value callee, int argCount) {
    if (IS_OBJ(callee)) {
        switch (OBJ_TYPE(callee)) {
            case OBJ_CLOSURE:
                return call(AS_CLOSURE(callee), argCount);
            case OBJ_NATIVE: {
                ObjNative* native = AS_NATIVE(callee);
                if (native->arity != -1 && argCount != native->arity) {
                    runtimeError("Expected %d arguments but got %d.", native->arity, argCount);
                    return false;
                }
                Value result = native->function(argCount, vm.stackTop - argCount);
                vm.stackTop -= argCount + 1;
                push(result);
                return true;
            }
            case OBJ_CLASS: {
                ObjClass* klass = AS_CLASS(callee);
                vm.stackTop[-argCount - 1] = OBJ_VAL(newInstance(klass));
                
                Value initializer;
                if (tableGet(&klass->methods, vm.initString, &initializer)) {
                    return call(AS_CLOSURE(initializer), argCount);
                } else if (argCount != 0) {
                    runtimeError("Expected 0 arguments but got %d.", argCount);
                    return false;
                }
                return true;
            }
            case OBJ_BOUND_METHOD: {
                ObjBoundMethod* bound = AS_BOUND_METHOD(callee);
                vm.stackTop[-argCount - 1] = bound->receiver;
                return call(bound->method, argCount);
            }
            default:
                break;
        }
    }
    runtimeError("Can only call functions and classes.");
    return false;
}

static ObjUpvalue* captureUpvalue(Value* local) {
    ObjUpvalue* prevUpvalue = NULL;
    ObjUpvalue* upvalue = vm.openUpvalues;
    while (upvalue != NULL && upvalue->location > local) {
        prevUpvalue = upvalue;
        upvalue = upvalue->next;
    }
    
    if (upvalue != NULL && upvalue->location == local) {
        return upvalue;
    }
    
    ObjUpvalue* createdUpvalue = newUpvalue(local);
    createdUpvalue->next = upvalue;
    
    if (prevUpvalue == NULL) {
        vm.openUpvalues = createdUpvalue;
    } else {
        prevUpvalue->next = createdUpvalue;
    }
    
    return createdUpvalue;
}

static void closeUpvalues(Value* last) {
    while (vm.openUpvalues != NULL && vm.openUpvalues->location >= last) {
        ObjUpvalue* upvalue = vm.openUpvalues;
        upvalue->closed = *upvalue->location;
        upvalue->location = &upvalue->closed;
        vm.openUpvalues = upvalue->next;
    }
}

static void defineMethod(ObjString* name) {
    Value method = peek(0);
    ObjClass* klass = AS_CLASS(peek(1));
    tableSet(&klass->methods, name, method);
    pop();
}

static bool bindMethod(ObjClass* klass, ObjString* name) {
    Value method;
    if (!tableGet(&klass->methods, name, &method)) {
        runtimeError("Undefined property '%s'.", name->chars);
        return false;
    }
    
    ObjBoundMethod* bound = newBoundMethod(peek(0), AS_CLOSURE(method));
    pop();
    push(OBJ_VAL(bound));
    return true;
}

static bool invokeFromClass(ObjClass* klass, ObjString* name, int argCount) {
    Value method;
    if (!tableGet(&klass->methods, name, &method)) {
        runtimeError("Undefined property '%s'.", name->chars);
        return false;
    }
    return call(AS_CLOSURE(method), argCount);
}

static bool invoke(ObjString* name, int argCount) {
    Value receiver = peek(argCount);
    
    if (!IS_INSTANCE(receiver)) {
        runtimeError("Only instances have methods.");
        return false;
    }
    
    ObjInstance* instance = AS_INSTANCE(receiver);
    
    Value value;
    if (tableGet(&instance->fields, name, &value)) {
        vm.stackTop[-argCount - 1] = value;
        return callValue(value, argCount);
    }
    
    return invokeFromClass(instance->klass, name, argCount);
}

static InterpretResult run(void) {
    CallFrame* frame = &vm.frames[vm.frameCount - 1];
    
#define READ_BYTE() (*frame->ip++)
#define READ_SHORT() \
    (frame->ip += 2, (uint16_t)((frame->ip[-2] << 8) | frame->ip[-1]))
#define READ_CONSTANT() \
    (frame->closure->function->chunk.constants.values[READ_BYTE()])
#define READ_STRING() AS_STRING(READ_CONSTANT())
#define BINARY_OP(valueType, op) \
    do { \
        if (!IS_NUMBER(peek(0)) || !IS_NUMBER(peek(1))) { \
            runtimeError("Operands must be numbers."); \
            return INTERPRET_RUNTIME_ERROR; \
        } \
        double b = valueToDouble(pop()); \
        double a = valueToDouble(pop()); \
        push(valueType(a op b)); \
    } while (false)

    for (;;) {
#if DEBUG_TRACE_EXECUTION
        printf("          ");
        for (Value* slot = vm.stack; slot < vm.stackTop; slot++) {
            printf("[ ");
            printValue(*slot);
            printf(" ]");
        }
        printf("\n");
#endif
        
        uint8_t instruction = READ_BYTE();
        switch (instruction) {
            case OP_CONSTANT: {
                Value constant = READ_CONSTANT();
                push(constant);
                break;
            }
            case OP_NULL: push(NULL_VAL); break;
            case OP_TRUE: push(BOOL_VAL(true)); break;
            case OP_FALSE: push(BOOL_VAL(false)); break;
            case OP_POP: pop(); break;
            case OP_DUP: push(peek(0)); break;
            
            case OP_GET_LOCAL: {
                uint8_t slot = READ_BYTE();
                push(frame->slots[slot]);
                break;
            }
            case OP_SET_LOCAL: {
                uint8_t slot = READ_BYTE();
                frame->slots[slot] = peek(0);
                break;
            }
            case OP_GET_GLOBAL: {
                ObjString* name = READ_STRING();
                Value value;
                if (!tableGet(&vm.globals, name, &value)) {
                    runtimeError("Undefined variable '%s'.", name->chars);
                    return INTERPRET_RUNTIME_ERROR;
                }
                push(value);
                break;
            }
            case OP_DEFINE_GLOBAL: {
                ObjString* name = READ_STRING();
                tableSet(&vm.globals, name, peek(0));
                pop();
                break;
            }
            case OP_SET_GLOBAL: {
                ObjString* name = READ_STRING();
                if (tableSet(&vm.globals, name, peek(0))) {
                    tableDelete(&vm.globals, name);
                    runtimeError("Undefined variable '%s'.", name->chars);
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_GET_UPVALUE: {
                uint8_t slot = READ_BYTE();
                push(*frame->closure->upvalues[slot]->location);
                break;
            }
            case OP_SET_UPVALUE: {
                uint8_t slot = READ_BYTE();
                *frame->closure->upvalues[slot]->location = peek(0);
                break;
            }
            case OP_CLOSE_UPVALUE:
                closeUpvalues(vm.stackTop - 1);
                pop();
                break;
                
            case OP_ADD: {
                if (IS_STRING(peek(0)) && IS_STRING(peek(1))) {
                    ObjString* b = AS_STRING(pop());
                    ObjString* a = AS_STRING(pop());
                    int length = a->length + b->length;
                    char* chars = ALLOCATE(char, length + 1);
                    memcpy(chars, a->chars, a->length);
                    memcpy(chars + a->length, b->chars, b->length);
                    chars[length] = '\0';
                    ObjString* result = takeString(chars, length);
                    push(OBJ_VAL(result));
                } else if (IS_NUMBER(peek(0)) && IS_NUMBER(peek(1))) {
                    double b = valueToDouble(pop());
                    double a = valueToDouble(pop());
                    push(DOUBLE_VAL(a + b));
                } else {
                    runtimeError("Operands must be two numbers or two strings.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_SUBTRACT: BINARY_OP(DOUBLE_VAL, -); break;
            case OP_MULTIPLY: BINARY_OP(DOUBLE_VAL, *); break;
            case OP_DIVIDE: BINARY_OP(DOUBLE_VAL, /); break;
            case OP_MODULO: {
                if (!IS_NUMBER(peek(0)) || !IS_NUMBER(peek(1))) {
                    runtimeError("Operands must be numbers.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                double b = valueToDouble(pop());
                double a = valueToDouble(pop());
                push(DOUBLE_VAL(fmod(a, b)));
                break;
            }
            case OP_NEGATE:
                if (!IS_NUMBER(peek(0))) {
                    runtimeError("Operand must be a number.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                push(DOUBLE_VAL(-valueToDouble(pop())));
                break;
                
            case OP_EQUAL: {
                Value b = pop();
                Value a = pop();
                push(BOOL_VAL(valuesEqual(a, b)));
                break;
            }
            case OP_NOT_EQUAL: {
                Value b = pop();
                Value a = pop();
                push(BOOL_VAL(!valuesEqual(a, b)));
                break;
            }
            case OP_GREATER: BINARY_OP(BOOL_VAL, >); break;
            case OP_GREATER_EQUAL: BINARY_OP(BOOL_VAL, >=); break;
            case OP_LESS: BINARY_OP(BOOL_VAL, <); break;
            case OP_LESS_EQUAL: BINARY_OP(BOOL_VAL, <=); break;
            
            case OP_NOT:
                push(BOOL_VAL(!AS_BOOL(valueTruthy(pop()))));
                break;
                
            case OP_JUMP: {
                uint16_t offset = READ_SHORT();
                frame->ip += offset;
                break;
            }
            case OP_JUMP_IF_FALSE: {
                uint16_t offset = READ_SHORT();
                if (!AS_BOOL(valueTruthy(peek(0)))) {
                    frame->ip += offset;
                }
                break;
            }
            case OP_LOOP: {
                uint16_t offset = READ_SHORT();
                frame->ip -= offset;
                break;
            }
            
            case OP_CALL: {
                int argCount = READ_BYTE();
                if (!callValue(peek(argCount), argCount)) {
                    return INTERPRET_RUNTIME_ERROR;
                }
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            case OP_CLOSURE: {
                ObjFunction* function = AS_FUNCTION(READ_CONSTANT());
                ObjClosure* closure = newClosure(function);
                push(OBJ_VAL(closure));
                for (int i = 0; i < closure->upvalueCount; i++) {
                    uint8_t isLocal = READ_BYTE();
                    uint8_t index = READ_BYTE();
                    if (isLocal) {
                        closure->upvalues[i] = captureUpvalue(frame->slots + index);
                    } else {
                        closure->upvalues[i] = frame->closure->upvalues[index];
                    }
                }
                break;
            }
            case OP_RETURN: {
                Value result = pop();
                closeUpvalues(frame->slots);
                vm.frameCount--;
                if (vm.frameCount == 0) {
                    pop();
                    return INTERPRET_OK;
                }
                vm.stackTop = frame->slots;
                push(result);
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            
            case OP_CLASS:
                push(OBJ_VAL(newClass(READ_STRING())));
                break;
            case OP_INHERIT: {
                Value superclass = peek(1);
                if (!IS_CLASS(superclass)) {
                    runtimeError("Superclass must be a class.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                ObjClass* subclass = AS_CLASS(peek(0));
                tableAddAll(&AS_CLASS(superclass)->methods, &subclass->methods);
                subclass->superclass = AS_CLASS(superclass);
                pop();
                break;
            }
            case OP_METHOD:
                defineMethod(READ_STRING());
                break;
            case OP_GET_PROPERTY: {
                if (!IS_INSTANCE(peek(0))) {
                    runtimeError("Only instances have properties.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                ObjInstance* instance = AS_INSTANCE(peek(0));
                ObjString* name = READ_STRING();
                
                Value value;
                if (tableGet(&instance->fields, name, &value)) {
                    pop();
                    push(value);
                    break;
                }
                
                if (!bindMethod(instance->klass, name)) {
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_SET_PROPERTY: {
                if (!IS_INSTANCE(peek(1))) {
                    runtimeError("Only instances have fields.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                ObjInstance* instance = AS_INSTANCE(peek(1));
                tableSet(&instance->fields, READ_STRING(), peek(0));
                Value value = pop();
                pop();
                push(value);
                break;
            }
            case OP_INVOKE: {
                ObjString* method = READ_STRING();
                int argCount = READ_BYTE();
                if (!invoke(method, argCount)) {
                    return INTERPRET_RUNTIME_ERROR;
                }
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            case OP_GET_SUPER: {
                ObjString* name = READ_STRING();
                ObjClass* superclass = AS_CLASS(pop());
                if (!bindMethod(superclass, name)) {
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_SUPER_INVOKE: {
                ObjString* method = READ_STRING();
                int argCount = READ_BYTE();
                ObjClass* superclass = AS_CLASS(pop());
                if (!invokeFromClass(superclass, method, argCount)) {
                    return INTERPRET_RUNTIME_ERROR;
                }
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            
            case OP_PRINT: {
                printValue(pop());
                break;
            }
            case OP_PRINTLN: {
                printValue(pop());
                printf("\n");
                break;
            }
            
            case OP_ARRAY: {
                int count = READ_BYTE();
                ObjArray* array = newArray();
                for (int i = count - 1; i >= 0; i--) {
                    writeValueArray(&array->elements, peek(i));
                }
                for (int i = 0; i < count; i++) pop();
                push(OBJ_VAL(array));
                break;
            }
            case OP_INDEX_GET: {
                Value index = pop();
                Value container = pop();
                
                if (IS_ARRAY(container)) {
                    if (!IS_INT(index)) {
                        runtimeError("Array index must be an integer.");
                        return INTERPRET_RUNTIME_ERROR;
                    }
                    ObjArray* array = AS_ARRAY(container);
                    int idx = (int)AS_INT(index);
                    if (idx < 0 || idx >= array->elements.count) {
                        runtimeError("Array index out of bounds.");
                        return INTERPRET_RUNTIME_ERROR;
                    }
                    push(array->elements.values[idx]);
                } else {
                    runtimeError("Only arrays can be indexed.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            case OP_INDEX_SET: {
                Value value = pop();
                Value index = pop();
                Value container = pop();
                
                if (IS_ARRAY(container)) {
                    if (!IS_INT(index)) {
                        runtimeError("Array index must be an integer.");
                        return INTERPRET_RUNTIME_ERROR;
                    }
                    ObjArray* array = AS_ARRAY(container);
                    int idx = (int)AS_INT(index);
                    if (idx < 0 || idx >= array->elements.count) {
                        runtimeError("Array index out of bounds.");
                        return INTERPRET_RUNTIME_ERROR;
                    }
                    array->elements.values[idx] = value;
                    push(value);
                } else {
                    runtimeError("Only arrays can be indexed.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                break;
            }
            
            default:
                runtimeError("Unknown opcode: %d", instruction);
                return INTERPRET_RUNTIME_ERROR;
        }
    }

#undef READ_BYTE
#undef READ_SHORT
#undef READ_CONSTANT
#undef READ_STRING
#undef BINARY_OP
}
