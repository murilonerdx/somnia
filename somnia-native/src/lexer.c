/*
 * Somnia Programming Language
 * Lexer (Tokenizer) Implementation
 */

#include "../include/somnia.h"

/* ============================================================================
 * HELPERS
 * ============================================================================ */

static bool is_at_end(Lexer* lexer) {
    return lexer->source[lexer->current] == '\0';
}

static char peek(Lexer* lexer) {
    return lexer->source[lexer->current];
}

static char peek_next(Lexer* lexer) {
    if (is_at_end(lexer)) return '\0';
    return lexer->source[lexer->current + 1];
}

static char advance(Lexer* lexer) {
    char c = lexer->source[lexer->current++];
    if (c == '\n') {
        lexer->line++;
        lexer->column = 1;
    } else {
        lexer->column++;
    }
    return c;
}

static bool match(Lexer* lexer, char expected) {
    if (is_at_end(lexer)) return false;
    if (lexer->source[lexer->current] != expected) return false;
    advance(lexer);
    return true;
}

static void skip_whitespace(Lexer* lexer) {
    while (!is_at_end(lexer)) {
        char c = peek(lexer);
        switch (c) {
            case ' ':
            case '\t':
            case '\r':
                advance(lexer);
                break;
            case '\n':
                advance(lexer);
                break;
            case '#':
                // Comment until end of line
                while (peek(lexer) != '\n' && !is_at_end(lexer)) {
                    advance(lexer);
                }
                break;
            case '/':
                if (peek_next(lexer) == '/') {
                    advance(lexer); advance(lexer);
                    while (peek(lexer) != '\n' && !is_at_end(lexer)) advance(lexer);
                } else if (peek_next(lexer) == '*') {
                    advance(lexer); advance(lexer); // Skip '/' and '*'
                    while (!is_at_end(lexer) && !(peek(lexer) == '*' && peek_next(lexer) == '/')) {
                        advance(lexer);
                    }
                    if (!is_at_end(lexer)) {
                        advance(lexer); // Skip '*'
                        advance(lexer); // Skip '/'
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

static Token make_token(Lexer* lexer, TokenType type) {
    Token token;
    token.type = type;
    
    int len = lexer->current - lexer->start;
    token.lexeme = malloc(len + 1);
    strncpy(token.lexeme, &lexer->source[lexer->start], len);
    token.lexeme[len] = '\0';
    
    token.line = lexer->line;
    token.column = lexer->column - len;
    token.literal = value_null();
    
    return token;
}

static Token error_token(Lexer* lexer, const char* message) {
    Token token;
    token.type = TOKEN_ERROR;
    token.lexeme = strdup(message);
    token.line = lexer->line;
    token.column = lexer->column;
    token.literal = value_null();
    return token;
}

static void add_token(Lexer* lexer, Token token) {
    if (lexer->token_count >= MAX_TOKENS) {
        fprintf(stderr, "[ERROR] Too many tokens\n");
        return;
    }
    lexer->tokens[lexer->token_count++] = token;
}

/* ============================================================================
 * TOKEN SCANNING
 * ============================================================================ */

static Token scan_string(Lexer* lexer) {
    char* buf = malloc(MAX_STRING);
    int len = 0;
    
    while (peek(lexer) != '"' && !is_at_end(lexer)) {
        if (peek(lexer) == '\\') {
            advance(lexer);
            switch (peek(lexer)) {
                case 'n': buf[len++] = '\n'; break;
                case 't': buf[len++] = '\t'; break;
                case 'r': buf[len++] = '\r'; break;
                case '\\': buf[len++] = '\\'; break;
                case '"': buf[len++] = '"'; break;
                default: buf[len++] = peek(lexer);
            }
            advance(lexer);
        } else {
            buf[len++] = advance(lexer);
        }
    }
    
    if (is_at_end(lexer)) {
        free(buf);
        return error_token(lexer, "Unterminated string");
    }
    
    advance(lexer); // Closing "
    buf[len] = '\0';
    
    Token token = make_token(lexer, TOKEN_STRING);
    token.literal = value_string(buf);
    free(buf);
    
    return token;
}

static Token scan_number(Lexer* lexer) {
    while (isdigit(peek(lexer))) advance(lexer);
    
    // Look for decimal part
    if (peek(lexer) == '.' && isdigit(peek_next(lexer))) {
        advance(lexer); // consume .
        while (isdigit(peek(lexer))) advance(lexer);
    }
    
    Token token = make_token(lexer, TOKEN_NUMBER);
    token.literal = value_number(atof(token.lexeme));
    
    return token;
}

static TokenType check_keyword(const char* start, int len, const char* rest, TokenType type) {
    if ((int)strlen(rest) == len && memcmp(start, rest, len) == 0) {
        return type;
    }
    return TOKEN_IDENTIFIER;
}

static TokenType identifier_type(Lexer* lexer) {
    int len = lexer->current - lexer->start;
    const char* start = &lexer->source[lexer->start];
    
    // Check keywords
    // Check keywords
    switch (start[0]) {
        case 'a': 
            if (len > 1) {
                if (start[1] == 'n') return check_keyword(start + 2, len - 2, "d", TOKEN_AND);
                if (start[1] == 'c') return check_keyword(start + 2, len - 2, "t", TOKEN_ACT);
                if (start[1] == 'f') return check_keyword(start + 2, len - 2, "fect", TOKEN_AFFECT);
            }
            break;
        case 'b': 
            if (len > 1) {
                if (start[1] == 'r') return check_keyword(start + 2, len - 2, "eak", TOKEN_BREAK);
                if (start[1] == 'u') return check_keyword(start + 2, len - 2, "dget", TOKEN_BUDGET);
            }
            break;
        case 'c':
            if (len > 1) {
                switch (start[1]) {
                    case 'l': return check_keyword(start + 2, len - 2, "ass", TOKEN_CLASS);
                    case 'a': return check_keyword(start + 2, len - 2, "se", TOKEN_CASE);
                    case 'o':
                        if (len > 3 && start[2] == 'n') {
                            if (start[3] == 's') return check_keyword(start + 4, len - 4, "t", TOKEN_CONST);
                            if (start[3] == 't') return check_keyword(start + 4, len - 4, "inue", TOKEN_CONTINUE);
                        }
                        break;
                }
            }
            break;
        case 'd': 
            if (len > 1) {
                if (start[1] == 'e') return check_keyword(start + 2, len - 2, "fault", TOKEN_DEFAULT);
                if (start[1] == 'r') return check_keyword(start + 2, len - 2, "ive", TOKEN_DRIVE);
            }
            break;
        case 'e':
            if (len > 1) {
                switch (start[1]) {
                    case 'l': return check_keyword(start + 2, len - 2, "se", TOKEN_ELSE);
                    case 'x': return check_keyword(start + 2, len - 2, "port", TOKEN_EXPORT);
                    case 'g': return check_keyword(start + 2, len - 2, "o", TOKEN_EGO);
                }
            }
            break;
        case 'E':
            if (len > 1 && start[1] == 'G') return check_keyword(start + 2, len - 2, "O", TOKEN_EGO);
            break;
        case 'f':
            if (len > 1) {
                switch (start[1]) {
                    case 'a': 
                        if (len > 2 && start[2] == 'l') return check_keyword(start + 3, len - 3, "se", TOKEN_FALSE);
                        if (len > 2 && start[2] == 'c') return check_keyword(start + 3, len - 3, "t", TOKEN_FACT);
                        break;
                    case 'u': return check_keyword(start + 2, len - 2, "n", TOKEN_FUN);
                    case 'o': 
                        if (len > 2 && start[2] == 'r') {
                            if (len == 3) return TOKEN_FOR;
                            return check_keyword(start + 3, len - 3, "bid", TOKEN_FORBID);
                        }
                        break;
                    case 'r': return check_keyword(start + 2, len - 2, "om", TOKEN_FROM);
                    case 'i': return check_keyword(start + 2, len - 2, "eld", TOKEN_FIELD);
                }
            }
            break;
        case 'i':
            if (len > 1) {
                switch (start[1]) {
                    case 'f': return len == 2 ? TOKEN_IF : TOKEN_IDENTIFIER;
                    case 'n': 
                        if (len == 2) return TOKEN_IN;
                        return check_keyword(start + 2, len - 2, "tent", TOKEN_INTENT);
                    case 'm': return check_keyword(start + 2, len - 2, "port", TOKEN_IMPORT);
                    case 'd': return len == 2 ? TOKEN_ID : TOKEN_IDENTIFIER;
                }
            }
            break;
        case 'I':
            if (len > 1 && start[1] == 'D') return len == 2 ? TOKEN_ID : TOKEN_IDENTIFIER;
            break;
        case 'A':
            if (len > 1 && start[1] == 'C') return check_keyword(start + 2, len - 2, "T", TOKEN_ACT);
            break;
        case 'm': return check_keyword(start + 1, len - 1, "ethod", TOKEN_METHOD); 
        case 'n':
            if (len > 1) {
                switch (start[1]) {
                    case 'e': return check_keyword(start + 2, len - 2, "w", TOKEN_NEW);
                    case 'o': return check_keyword(start + 2, len - 2, "t", TOKEN_NOT);
                    case 'u': return check_keyword(start + 2, len - 2, "ll", TOKEN_NULL);
                }
            }
            break;
        case 'o': 
            if (len > 1) {
                if (start[1] == 'r') return len == 2 ? TOKEN_OR : TOKEN_IDENTIFIER;
                if (start[1] == 'n') {
                    if (len > 3 && start[2] == '_') return check_keyword(start + 3, len - 3, "tie", TOKEN_ON_TIE);
                }
            }
            break;
        case 'p': return check_keyword(start + 1, len - 1, "ropose", TOKEN_PROPOSE);
        case 'r': 
            if (len > 1) {
                if (start[1] == 'e') return check_keyword(start + 2, len - 2, "turn", TOKEN_RETURN);
                if (start[1] == 'u') return check_keyword(start + 2, len - 2, "le_order", TOKEN_RULE_ORDER);
            }
            break;
        case 's': return check_keyword(start + 1, len - 1, "elect", TOKEN_SELECT);
        case 't':
            if (len > 1) {
                if (start[1] == 'r') {
                    if (len > 2 && start[2] == 'y') return TOKEN_TRY;
                    return check_keyword(start + 2, len - 2, "ue", TOKEN_TRUE);
                }
                if (start[1] == 'o') return check_keyword(start + 2, len - 2, "p", TOKEN_TOP);
            }
            break;
        case 'v': return check_keyword(start + 1, len - 1, "ar", TOKEN_VAR);
        case 'w':
            if (len > 2 && start[1] == 'h') {
                switch (start[2]) {
                    case 'i': return check_keyword(start + 3, len - 3, "le", TOKEN_WHILE);
                    case 'e': return check_keyword(start + 3, len - 3, "n", TOKEN_WHEN);
                }
            }
            break;
    }
    
    return TOKEN_IDENTIFIER;
}

static Token scan_identifier(Lexer* lexer) {
    while (isalnum(peek(lexer)) || peek(lexer) == '_') {
        advance(lexer);
    }
    
    TokenType type = identifier_type(lexer);
    Token token = make_token(lexer, type);
    
    if (type == TOKEN_TRUE) {
        token.literal = value_bool(true);
    } else if (type == TOKEN_FALSE) {
        token.literal = value_bool(false);
    } else if (type == TOKEN_NULL) {
        token.literal = value_null();
    }
    
    return token;
}

static Token scan_token(Lexer* lexer) {
    skip_whitespace(lexer);
    
    lexer->start = lexer->current;
    
    if (is_at_end(lexer)) {
        return make_token(lexer, TOKEN_EOF);
    }
    
    char c = advance(lexer);
    
    // Identifiers and keywords
    if (isalpha(c) || c == '_') {
        return scan_identifier(lexer);
    }
    
    // Numbers
    if (isdigit(c)) {
        return scan_number(lexer);
    }
    
    switch (c) {
        case '(': return make_token(lexer, TOKEN_LPAREN);
        case ')': return make_token(lexer, TOKEN_RPAREN);
        case '{': return make_token(lexer, TOKEN_LBRACE);
        case '}': return make_token(lexer, TOKEN_RBRACE);
        case '[': return make_token(lexer, TOKEN_LBRACKET);
        case ']': return make_token(lexer, TOKEN_RBRACKET);
        case ',': return make_token(lexer, TOKEN_COMMA);
        case '.': return make_token(lexer, TOKEN_DOT);
        case ':': return make_token(lexer, TOKEN_COLON);
        case ';': return make_token(lexer, TOKEN_SEMICOLON);
        case '+': return make_token(lexer, TOKEN_PLUS);
        case '*': return make_token(lexer, TOKEN_STAR);
        case '/': return make_token(lexer, TOKEN_SLASH);
        case '%': return make_token(lexer, TOKEN_PERCENT);
        
        case '-':
            if (match(lexer, '>')) return make_token(lexer, TOKEN_THIN_ARROW);
            return make_token(lexer, TOKEN_MINUS);
        
        case '=':
            if (match(lexer, '>')) return make_token(lexer, TOKEN_ARROW);
            if (match(lexer, '=')) return make_token(lexer, TOKEN_EQEQ);
            return make_token(lexer, TOKEN_EQ);
        
        case '!':
            if (match(lexer, '=')) return make_token(lexer, TOKEN_NEQ);
            return make_token(lexer, TOKEN_NOT);
        
        case '<':
            if (match(lexer, '=')) return make_token(lexer, TOKEN_LTE);
            return make_token(lexer, TOKEN_LT);
        
        case '>':
            if (match(lexer, '=')) return make_token(lexer, TOKEN_GTE);
            return make_token(lexer, TOKEN_GT);
        
        case '"': return scan_string(lexer);
    }
    
    return error_token(lexer, "Unexpected character");
}

/* ============================================================================
 * PUBLIC API
 * ============================================================================ */

Lexer* lexer_create(const char* source) {
    Lexer* lexer = malloc(sizeof(Lexer));
    lexer->source = source;
    lexer->start = 0;
    lexer->current = 0;
    lexer->line = 1;
    lexer->column = 1;
    lexer->tokens = malloc(sizeof(Token) * MAX_TOKENS);
    lexer->token_count = 0;
    return lexer;
}

void lexer_scan_tokens(Lexer* lexer) {
    int error_count = 0;
    while (!is_at_end(lexer) && error_count < 10) {
        Token token = scan_token(lexer);
        add_token(lexer, token);
        
        if (token.type == TOKEN_EOF) break;
        if (token.type == TOKEN_ERROR) {
            fprintf(stderr, "[LEXER ERROR] Line %d: %s\n", token.line, token.lexeme);
            error_count++;
        }
    }
    // Always add EOF at the end
    if (lexer->token_count == 0 || lexer->tokens[lexer->token_count - 1].type != TOKEN_EOF) {
        Token eof;
        eof.type = TOKEN_EOF;
        eof.lexeme = strdup("");
        eof.line = lexer->line;
        eof.column = lexer->column;
        eof.literal = value_null();
        add_token(lexer, eof);
    }
}

void lexer_free(Lexer* lexer) {
    if (lexer == NULL) return;
    
    for (int i = 0; i < lexer->token_count; i++) {
        free(lexer->tokens[i].lexeme);
    }
    free(lexer->tokens);
    free(lexer);
}
