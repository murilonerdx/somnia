#ifndef SOMNIA_LEXER_H
#define SOMNIA_LEXER_H

#include "common.h"

/**
 * Token Types
 */
typedef enum {
    // Single-character tokens
    TOKEN_LEFT_PAREN, TOKEN_RIGHT_PAREN,
    TOKEN_LEFT_BRACE, TOKEN_RIGHT_BRACE,
    TOKEN_LEFT_BRACKET, TOKEN_RIGHT_BRACKET,
    TOKEN_COMMA, TOKEN_DOT, TOKEN_MINUS, TOKEN_PLUS,
    TOKEN_SEMICOLON, TOKEN_COLON, TOKEN_SLASH, TOKEN_STAR,
    TOKEN_PERCENT,
    
    // One or two character tokens
    TOKEN_BANG, TOKEN_BANG_EQUAL,
    TOKEN_EQUAL, TOKEN_EQUAL_EQUAL,
    TOKEN_GREATER, TOKEN_GREATER_EQUAL,
    TOKEN_LESS, TOKEN_LESS_EQUAL,
    TOKEN_ARROW,        // ->
    TOKEN_FAT_ARROW,    // =>
    
    // Literals
    TOKEN_IDENTIFIER, TOKEN_STRING, TOKEN_NUMBER, TOKEN_INT,
    
    // Keywords
    TOKEN_AND, TOKEN_CLASS, TOKEN_ELSE, TOKEN_FALSE,
    TOKEN_FOR, TOKEN_FUN, TOKEN_IF, TOKEN_NULL,
    TOKEN_OR, TOKEN_PRINT, TOKEN_PRINTLN, TOKEN_RETURN, TOKEN_SUPER,
    TOKEN_SELF, TOKEN_TRUE, TOKEN_VAR, TOKEN_CONST, TOKEN_WHILE,
    TOKEN_EXTENDS, TOKEN_IMPORT, TOKEN_FROM, TOKEN_AS,
    TOKEN_BREAK, TOKEN_CONTINUE, TOKEN_IN, TOKEN_MATCH, TOKEN_CASE,
    
    // Somnia-specific (psychological blocks)
    TOKEN_ID, TOKEN_EGO, TOKEN_ACT, TOKEN_ACTION,
    
    TOKEN_ERROR,
    TOKEN_EOF
} TokenType;

/**
 * Token
 */
typedef struct {
    TokenType type;
    const char* start;
    int length;
    int line;
} Token;

/**
 * Lexer
 */
typedef struct {
    const char* start;
    const char* current;
    int line;
} Lexer;

void initLexer(Lexer* lexer, const char* source);
Token scanToken(Lexer* lexer);

#endif // SOMNIA_LEXER_H
