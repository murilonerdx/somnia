/*
 * Somnia Programming Language
 * Native Runtime - Header File
 * 
 * Copyright 2024 - Pure C Implementation
 */

#ifndef SOMNIA_H
#define SOMNIA_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <ctype.h>
#include <math.h>
#include <time.h>

/* ============================================================================
 * CONSTANTS
 * ============================================================================ */

#define SOMNIA_VERSION "1.0.0"
#define MAX_TOKENS 10000
#define MAX_VARS 1000
#define MAX_STRING 4096
#define MAX_ARRAY 1000
#define MAX_FIELDS 100
#define MAX_ARGS 20
#define MAX_STACK 1000

/* ============================================================================
 * TOKEN TYPES
 * ============================================================================ */

typedef enum {
    // Literals
    TOKEN_NUMBER,
    TOKEN_STRING,
    TOKEN_IDENTIFIER,
    TOKEN_TRUE,
    TOKEN_FALSE,
    TOKEN_NULL,
    
    // Keywords
    TOKEN_VAR,
    TOKEN_CONST,
    TOKEN_FUN,
    TOKEN_RETURN,
    TOKEN_IF,
    TOKEN_ELSE,
    TOKEN_WHEN,
    TOKEN_FOR,
    TOKEN_WHILE,
    TOKEN_IN,
    TOKEN_BREAK,
    TOKEN_CONTINUE,
    TOKEN_CLASS,
    TOKEN_METHOD,
    TOKEN_FIELD,
    TOKEN_NEW,
    TOKEN_IMPORT,
    TOKEN_EXPORT,
    
    // Operators
    TOKEN_PLUS,
    TOKEN_MINUS,
    TOKEN_STAR,
    TOKEN_SLASH,
    TOKEN_PERCENT,
    TOKEN_EQ,
    TOKEN_EQEQ,
    TOKEN_NEQ,
    TOKEN_LT,
    TOKEN_GT,
    TOKEN_LTE,
    TOKEN_GTE,
    TOKEN_AND,
    TOKEN_OR,
    TOKEN_NOT,
    TOKEN_ARROW,      // =>
    TOKEN_THIN_ARROW, // ->
    
    // Delimiters
    TOKEN_LPAREN,
    TOKEN_RPAREN,
    TOKEN_LBRACE,
    TOKEN_RBRACE,
    TOKEN_LBRACKET,
    TOKEN_RBRACKET,
    TOKEN_COMMA,
    TOKEN_DOT,
    TOKEN_COLON,
    TOKEN_SEMICOLON,
    TOKEN_HASH,
    
    TOKEN_DEFAULT,
    TOKEN_CASE,
    TOKEN_FROM,
    TOKEN_NEWLINE,
    TOKEN_EOF,
    TOKEN_ERROR
} TokenType;

/* ============================================================================
 * VALUE TYPES
 * ============================================================================ */

typedef enum {
    VAL_NULL,
    VAL_BOOL,
    VAL_NUMBER,
    VAL_STRING,
    VAL_ARRAY,
    VAL_MAP,
    VAL_FUNCTION,
    VAL_NATIVE_FN,
    VAL_OBJECT
} ValueType;

/* Forward declarations */
struct Value;
struct Env;
struct ASTNode;
struct Array;
struct Map;
struct Function;
struct Object;

/* Value structure - defined first */
typedef struct Value {
    ValueType type;
    union {
        bool boolean;
        double number;
        char* string;
        struct Array* array;
        struct Map* map;
        struct Function* function;
        struct Value (*native_fn)(struct Value* args, int arg_count, struct Env* env);
        struct Object* object;
    } as;
} Value;

/* Native function pointer */
typedef Value (*NativeFn)(Value* args, int arg_count, struct Env* env);

/* Array structure */
typedef struct Array {
    Value* items;
    int count;
    int capacity;
} Array;

/* Object structure */
typedef struct Object {
    char* class_name;
    struct Env* fields;
    struct ASTNode* ast;
} Object;

/* Map entry */
typedef struct MapEntry {
    char* key;
    Value value;
} MapEntry;

/* Map structure */
typedef struct Map {
    MapEntry* entries;
    int count;
    int capacity;
} Map;

/* Function structure */
typedef struct Function {
    char* name;
    char** params;
    int param_count;
    struct ASTNode* body;
    struct Env* closure;
} Function;

/* ============================================================================
 * TOKEN
 * ============================================================================ */

typedef struct {
    TokenType type;
    char* lexeme;
    int line;
    int column;
    Value literal;
} Token;

/* ============================================================================
 * AST NODE TYPES
 * ============================================================================ */

typedef enum {
    AST_PROGRAM,
    AST_BLOCK,
    AST_EXPR_STMT,
    AST_VAR_DECL,
    AST_FUN_DECL,
    AST_CLASS,
    AST_IMPORT,
    AST_EXPORT,
    AST_IF,
    AST_WHILE,
    AST_FOR,
    AST_WHEN,
    AST_RETURN,
    AST_BREAK,
    AST_CONTINUE,
    AST_ASSIGN,
    AST_BINARY,
    AST_UNARY,
    AST_CALL,
    AST_GET,
    AST_SET,
    AST_INDEX,
    AST_INDEX_SET,
    AST_VARIABLE,
    AST_LITERAL,
    AST_ARRAY,
    AST_MAP,
    AST_OBJECT
} ASTNodeType;

/* AST Node */
typedef struct ASTNode {
    ASTNodeType type;
    int line;
    
    union {
        // Literals
        Value literal;
        
        // Variable
        char* var_name;
        
        // Binary/Unary
        struct {
            struct ASTNode* left;
            struct ASTNode* right;
            TokenType op;
        } binary;
        
        struct {
            struct ASTNode* operand;
            TokenType op;
        } unary;
        
        // Assignment
        struct {
            char* name;
            struct ASTNode* value;
        } assign;
        
        // Call
        struct {
            struct ASTNode* callee;
            struct ASTNode** args;
            int arg_count;
        } call;
        
        // Function declaration
        struct {
            char* name;
            char** params;
            int param_count;
            struct ASTNode* body;
        } fun_decl;
        
        // Variable declaration
        struct {
            char* name;
            struct ASTNode* initializer;
        } var_decl;
        
        // Block
        struct {
            struct ASTNode** statements;
            int stmt_count;
        } block;
        
        struct {
            struct ASTNode* object;
            struct ASTNode* index;
            struct ASTNode* value;
        } index_set;
        
        // If statement
        struct {
            struct ASTNode* condition;
            struct ASTNode* then_branch;
            struct ASTNode* else_branch;
        } if_stmt;
        
        // When statement
        struct {
            struct ASTNode* condition;
            struct ASTNode* body;
        } when_stmt;
        
        // For loop
        struct {
            char* var_name;
            struct ASTNode* iterable;
            struct ASTNode* body;
        } for_stmt;
        
        // While loop
        struct {
            struct ASTNode* condition;
            struct ASTNode* body;
        } while_stmt;
        
        // Return
        struct {
            struct ASTNode* value;
        } return_stmt;
        
        // Array literal
        struct {
            struct ASTNode** elements;
            int count;
        } array_lit;
        
        // Map literal
        struct {
            char** keys;
            struct ASTNode** values;
            int count;
        } map_lit;
        
        // Index access
        struct {
            struct ASTNode* object;
            struct ASTNode* index;
        } index_expr;
        
        // Property access
        struct {
            struct ASTNode* object;
            char* property;
        } get_expr;
        
        // Property set
        struct {
            struct ASTNode* object;
            char* property;
            struct ASTNode* value;
        } set_expr;
        
        // Import
        struct {
            char* path;
            char** names;
            int count;
        } import_stmt;
        
        // Export
        struct {
            char** names;
            int count;
        } export_stmt;
        
        // Class Declaration
        struct {
            char* name;
            char** fields;
            int field_count;
            struct ASTNode** methods;
            int method_count;
        } class_decl;
        
        // Object Instantiation (Class { ... })
        struct {
            char* class_name;
            char** fields;
            struct ASTNode** values;
            int count;
        } obj_inst;
        
    } as;
} ASTNode;

/* ============================================================================
 * ENVIRONMENT
 * ============================================================================ */

typedef struct {
    char* name;
    Value value;
    bool is_const;
} Variable;

typedef struct Env {
    Variable* vars;
    int var_count;
    int var_capacity;
    struct Env* parent;
} Env;

/* ============================================================================
 * LEXER
 * ============================================================================ */

typedef struct {
    const char* source;
    int start;
    int current;
    int line;
    int column;
    Token* tokens;
    int token_count;
} Lexer;

/* ============================================================================
 * PARSER
 * ============================================================================ */

typedef struct {
    Token* tokens;
    int token_count;
    int current;
} Parser;

/* ============================================================================
 * INTERPRETER
 * ============================================================================ */

typedef struct {
    Env* global_env;
    Env* current_env;
    bool had_error;
    bool returning;
    bool breaking;
    bool continuing;
    Value return_value;
} Interpreter;

/* ============================================================================
 * FUNCTION DECLARATIONS
 * ============================================================================ */

/* Lexer */
Lexer* lexer_create(const char* source);
void lexer_scan_tokens(Lexer* lexer);
void lexer_free(Lexer* lexer);

/* Parser */
Parser* parser_create(Token* tokens, int count);
ASTNode* parser_parse(Parser* parser);
void parser_free(Parser* parser);
void ast_free(ASTNode* node);

/* Interpreter */
Interpreter* interpreter_create(void);
Value interpreter_run(Interpreter* interp, ASTNode* program);
void interpreter_free(Interpreter* interp);

/* Environment */
Env* env_create(Env* parent);
void env_define(Env* env, const char* name, Value value, bool is_const);
Value* env_get(Env* env, const char* name);
bool env_set(Env* env, const char* name, Value value);
void env_free(Env* env);

/* Value */
Value value_null(void);
Value value_bool(bool b);
Value value_number(double n);
Value value_string(const char* s);
Value value_array(void);
Value value_map(void);
char* value_to_string(Value val);
void value_print(Value val);
bool value_is_truthy(Value val);
bool value_equals(Value a, Value b);
Value value_copy(Value val);
Value value_object(const char* class_name, Env* fields);
void value_free(Value* val);

/* Array operations */
void array_push(Array* arr, Value val);
Value array_get(Array* arr, int index);
void array_set(Array* arr, int index, Value val);

/* Map operations */
void map_set(Map* m, const char* key, Value val);
Value* map_get(Map* m, const char* key);
bool map_has(Map* m, const char* key);

/* Standard library */
void stdlib_register(Env* env);

/* Network primitives (Native functions) */
Value native_net_listen(Value* args, int arg_count, Env* env);
Value native_net_accept(Value* args, int arg_count, Env* env);
Value native_net_read(Value* args, int arg_count, Env* env);
Value native_net_write(Value* args, int arg_count, Env* env);
Value native_net_close(Value* args, int arg_count, Env* env);

/* Utilities */
char* read_file(const char* path);
void somnia_error(const char* message, int line);

#endif /* SOMNIA_H */
