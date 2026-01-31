#ifndef SOMNIA_CHUNK_H
#define SOMNIA_CHUNK_H

#include "common.h"
#include "value.h"

/**
 * Opcodes
 * The instruction set for the Somnia VM.
 */
typedef enum {
    // Constants
    OP_CONSTANT,        // Push constant
    OP_NULL,
    OP_TRUE,
    OP_FALSE,
    
    // Stack manipulation
    OP_POP,
    OP_DUP,
    
    // Variables
    OP_GET_LOCAL,
    OP_SET_LOCAL,
    OP_GET_GLOBAL,
    OP_DEFINE_GLOBAL,
    OP_SET_GLOBAL,
    OP_GET_UPVALUE,
    OP_SET_UPVALUE,
    OP_CLOSE_UPVALUE,
    
    // Arithmetic
    OP_ADD,
    OP_SUBTRACT,
    OP_MULTIPLY,
    OP_DIVIDE,
    OP_MODULO,
    OP_NEGATE,
    
    // Comparison
    OP_EQUAL,
    OP_NOT_EQUAL,
    OP_GREATER,
    OP_GREATER_EQUAL,
    OP_LESS,
    OP_LESS_EQUAL,
    
    // Logic
    OP_NOT,
    OP_AND,
    OP_OR,
    
    // Control flow
    OP_JUMP,
    OP_JUMP_IF_FALSE,
    OP_LOOP,
    
    // Functions
    OP_CALL,
    OP_CLOSURE,
    OP_RETURN,
    
    // OOP
    OP_CLASS,
    OP_INHERIT,
    OP_METHOD,
    OP_GET_PROPERTY,
    OP_SET_PROPERTY,
    OP_GET_SUPER,
    OP_INVOKE,
    OP_SUPER_INVOKE,
    
    // Collections
    OP_ARRAY,           // Create array with N elements on stack
    OP_MAP,             // Create map with N key-value pairs
    OP_INDEX_GET,       // array[index] or map[key]
    OP_INDEX_SET,       // array[index] = val or map[key] = val
    
    // Built-in
    OP_PRINT,
    OP_PRINTLN,
    
} OpCode;

/**
 * Chunk
 * A sequence of bytecode instructions with associated constants.
 */
typedef struct {
    int count;
    int capacity;
    uint8_t* code;
    int* lines;         // Line numbers for debugging
    ValueArray constants;
} Chunk;

void initChunk(Chunk* chunk);
void freeChunk(Chunk* chunk);
void writeChunk(Chunk* chunk, uint8_t byte, int line);
int addConstant(Chunk* chunk, Value value);

#endif // SOMNIA_CHUNK_H
