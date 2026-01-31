#include "lexer.h"
#include <string.h>
#include <ctype.h>

void initLexer(Lexer* lexer, const char* source) {
    lexer->start = source;
    lexer->current = source;
    lexer->line = 1;
}

static bool isAtEnd(Lexer* lexer) {
    return *lexer->current == '\0';
}

static char advance(Lexer* lexer) {
    lexer->current++;
    return lexer->current[-1];
}

static char peek(Lexer* lexer) {
    return *lexer->current;
}

static char peekNext(Lexer* lexer) {
    if (isAtEnd(lexer)) return '\0';
    return lexer->current[1];
}

static bool match(Lexer* lexer, char expected) {
    if (isAtEnd(lexer)) return false;
    if (*lexer->current != expected) return false;
    lexer->current++;
    return true;
}

static Token makeToken(Lexer* lexer, TokenType type) {
    Token token;
    token.type = type;
    token.start = lexer->start;
    token.length = (int)(lexer->current - lexer->start);
    token.line = lexer->line;
    return token;
}

static Token errorToken(Lexer* lexer, const char* message) {
    Token token;
    token.type = TOKEN_ERROR;
    token.start = message;
    token.length = (int)strlen(message);
    token.line = lexer->line;
    return token;
}

static void skipWhitespace(Lexer* lexer) {
    for (;;) {
        char c = peek(lexer);
        switch (c) {
            case ' ':
            case '\r':
            case '\t':
                advance(lexer);
                break;
            case '\n':
                lexer->line++;
                advance(lexer);
                break;
            case '#':  // Comment
                while (peek(lexer) != '\n' && !isAtEnd(lexer)) {
                    advance(lexer);
                }
                break;
            case '/':
                if (peekNext(lexer) == '/') {
                    // Line comment
                    while (peek(lexer) != '\n' && !isAtEnd(lexer)) {
                        advance(lexer);
                    }
                } else if (peekNext(lexer) == '*') {
                    // Block comment
                    advance(lexer); advance(lexer);
                    while (!isAtEnd(lexer)) {
                        if (peek(lexer) == '*' && peekNext(lexer) == '/') {
                            advance(lexer); advance(lexer);
                            break;
                        }
                        if (peek(lexer) == '\n') lexer->line++;
                        advance(lexer);
                    }
                } else {
                    return;
                }
                break;
            default:
                return;
        }
    }
}

static TokenType checkKeyword(Lexer* lexer, int start, int length, const char* rest, TokenType type) {
    if (lexer->current - lexer->start == start + length &&
        memcmp(lexer->start + start, rest, length) == 0) {
        return type;
    }
    return TOKEN_IDENTIFIER;
}

static TokenType identifierType(Lexer* lexer) {
    switch (lexer->start[0]) {
        case 'a':
            if (lexer->current - lexer->start > 1) {
                switch (lexer->start[1]) {
                    case 'c': return checkKeyword(lexer, 2, 4, "tion", TOKEN_ACTION);
                    case 'n': return checkKeyword(lexer, 2, 1, "d", TOKEN_AND);
                    case 's': return checkKeyword(lexer, 2, 0, "", TOKEN_AS);
                }
            }
            break;
        case 'b': return checkKeyword(lexer, 1, 4, "reak", TOKEN_BREAK);
        case 'c':
            if (lexer->current - lexer->start > 1) {
                switch (lexer->start[1]) {
                    case 'a': return checkKeyword(lexer, 2, 2, "se", TOKEN_CASE);
                    case 'l': return checkKeyword(lexer, 2, 3, "ass", TOKEN_CLASS);
                    case 'o':
                        if (lexer->current - lexer->start > 2) {
                            switch (lexer->start[2]) {
                                case 'n':
                                    if (lexer->current - lexer->start > 3 && lexer->start[3] == 's') {
                                        return checkKeyword(lexer, 4, 1, "t", TOKEN_CONST);
                                    }
                                    return checkKeyword(lexer, 2, 6, "ntinue", TOKEN_CONTINUE);
                            }
                        }
                        break;
                }
            }
            break;
        case 'e':
            if (lexer->current - lexer->start > 1) {
                switch (lexer->start[1]) {
                    case 'l': return checkKeyword(lexer, 2, 2, "se", TOKEN_ELSE);
                    case 'x': return checkKeyword(lexer, 2, 5, "tends", TOKEN_EXTENDS);
                }
            }
            break;
        case 'f':
            if (lexer->current - lexer->start > 1) {
                switch (lexer->start[1]) {
                    case 'a': return checkKeyword(lexer, 2, 3, "lse", TOKEN_FALSE);
                    case 'o': return checkKeyword(lexer, 2, 1, "r", TOKEN_FOR);
                    case 'r': return checkKeyword(lexer, 2, 2, "om", TOKEN_FROM);
                    case 'u': return checkKeyword(lexer, 2, 1, "n", TOKEN_FUN);
                }
            }
            break;
        case 'i':
            if (lexer->current - lexer->start > 1) {
                switch (lexer->start[1]) {
                    case 'f': return checkKeyword(lexer, 2, 0, "", TOKEN_IF);
                    case 'm': return checkKeyword(lexer, 2, 4, "port", TOKEN_IMPORT);
                    case 'n': return checkKeyword(lexer, 2, 0, "", TOKEN_IN);
                }
            }
            break;
        case 'm': return checkKeyword(lexer, 1, 4, "atch", TOKEN_MATCH);
        case 'n': return checkKeyword(lexer, 1, 3, "ull", TOKEN_NULL);
        case 'o': return checkKeyword(lexer, 1, 1, "r", TOKEN_OR);
        case 'p':
            if (lexer->current - lexer->start > 4 && lexer->start[5] == 'l') {
                return checkKeyword(lexer, 1, 6, "rintln", TOKEN_PRINTLN);
            }
            return checkKeyword(lexer, 1, 4, "rint", TOKEN_PRINT);
        case 'r': return checkKeyword(lexer, 1, 5, "eturn", TOKEN_RETURN);
        case 's':
            if (lexer->current - lexer->start > 1) {
                switch (lexer->start[1]) {
                    case 'e': return checkKeyword(lexer, 2, 2, "lf", TOKEN_SELF);
                    case 'u': return checkKeyword(lexer, 2, 3, "per", TOKEN_SUPER);
                }
            }
            break;
        case 't': return checkKeyword(lexer, 1, 3, "rue", TOKEN_TRUE);
        case 'v': return checkKeyword(lexer, 1, 2, "ar", TOKEN_VAR);
        case 'w': return checkKeyword(lexer, 1, 4, "hile", TOKEN_WHILE);
        
        // Somnia psychological blocks
        case 'I': return checkKeyword(lexer, 1, 1, "D", TOKEN_ID);
        case 'E': return checkKeyword(lexer, 1, 2, "GO", TOKEN_EGO);
        case 'A': return checkKeyword(lexer, 1, 2, "CT", TOKEN_ACT);
    }
    return TOKEN_IDENTIFIER;
}

static Token identifier(Lexer* lexer) {
    while (isalnum(peek(lexer)) || peek(lexer) == '_') {
        advance(lexer);
    }
    return makeToken(lexer, identifierType(lexer));
}

static Token number(Lexer* lexer) {
    bool isFloat = false;
    
    while (isdigit(peek(lexer))) {
        advance(lexer);
    }
    
    // Look for a decimal part
    if (peek(lexer) == '.' && isdigit(peekNext(lexer))) {
        isFloat = true;
        advance(lexer);  // Consume the '.'
        while (isdigit(peek(lexer))) {
            advance(lexer);
        }
    }
    
    // Scientific notation
    if (peek(lexer) == 'e' || peek(lexer) == 'E') {
        isFloat = true;
        advance(lexer);
        if (peek(lexer) == '+' || peek(lexer) == '-') {
            advance(lexer);
        }
        while (isdigit(peek(lexer))) {
            advance(lexer);
        }
    }
    
    return makeToken(lexer, isFloat ? TOKEN_NUMBER : TOKEN_INT);
}

static Token string(Lexer* lexer, char quote) {
    while (peek(lexer) != quote && !isAtEnd(lexer)) {
        if (peek(lexer) == '\n') lexer->line++;
        if (peek(lexer) == '\\' && peekNext(lexer) != '\0') {
            advance(lexer);  // Skip escape char
        }
        advance(lexer);
    }
    
    if (isAtEnd(lexer)) {
        return errorToken(lexer, "Unterminated string.");
    }
    
    advance(lexer);  // Closing quote
    return makeToken(lexer, TOKEN_STRING);
}

Token scanToken(Lexer* lexer) {
    skipWhitespace(lexer);
    lexer->start = lexer->current;
    
    if (isAtEnd(lexer)) return makeToken(lexer, TOKEN_EOF);
    
    char c = advance(lexer);
    
    if (isalpha(c) || c == '_') return identifier(lexer);
    if (isdigit(c)) return number(lexer);
    
    switch (c) {
        case '(': return makeToken(lexer, TOKEN_LEFT_PAREN);
        case ')': return makeToken(lexer, TOKEN_RIGHT_PAREN);
        case '{': return makeToken(lexer, TOKEN_LEFT_BRACE);
        case '}': return makeToken(lexer, TOKEN_RIGHT_BRACE);
        case '[': return makeToken(lexer, TOKEN_LEFT_BRACKET);
        case ']': return makeToken(lexer, TOKEN_RIGHT_BRACKET);
        case ';': return makeToken(lexer, TOKEN_SEMICOLON);
        case ':': return makeToken(lexer, TOKEN_COLON);
        case ',': return makeToken(lexer, TOKEN_COMMA);
        case '.': return makeToken(lexer, TOKEN_DOT);
        case '+': return makeToken(lexer, TOKEN_PLUS);
        case '*': return makeToken(lexer, TOKEN_STAR);
        case '%': return makeToken(lexer, TOKEN_PERCENT);
        case '/': return makeToken(lexer, TOKEN_SLASH);
        
        case '-':
            return makeToken(lexer, match(lexer, '>') ? TOKEN_ARROW : TOKEN_MINUS);
        case '!':
            return makeToken(lexer, match(lexer, '=') ? TOKEN_BANG_EQUAL : TOKEN_BANG);
        case '=':
            if (match(lexer, '=')) return makeToken(lexer, TOKEN_EQUAL_EQUAL);
            if (match(lexer, '>')) return makeToken(lexer, TOKEN_FAT_ARROW);
            return makeToken(lexer, TOKEN_EQUAL);
        case '<':
            return makeToken(lexer, match(lexer, '=') ? TOKEN_LESS_EQUAL : TOKEN_LESS);
        case '>':
            return makeToken(lexer, match(lexer, '=') ? TOKEN_GREATER_EQUAL : TOKEN_GREATER);
            
        case '"': return string(lexer, '"');
        case '\'': return string(lexer, '\'');
    }
    
    return errorToken(lexer, "Unexpected character.");
}
