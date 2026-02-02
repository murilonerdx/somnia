/*
 * Somnia Programming Language
 * Parser Implementation
 */

#include "../include/somnia.h"

/* ============================================================================
 * HELPERS
 * ============================================================================ */

static Token peek_token(Parser* parser) {
    if (parser->current >= parser->token_count) {
        Token eof;
        memset(&eof, 0, sizeof(Token));
        eof.type = TOKEN_EOF;
        eof.lexeme = "";
        eof.line = -1;
        return eof;
    }
    return parser->tokens[parser->current];
}

static Token previous(Parser* parser) {
    if (parser->current <= 0) {
        Token start;
        memset(&start, 0, sizeof(Token));
        start.type = TOKEN_ERROR;
        start.lexeme = "START";
        return start;
    }
    return parser->tokens[parser->current - 1];
}

static bool is_at_end_p(Parser* parser) {
    return parser->current >= parser->token_count || peek_token(parser).type == TOKEN_EOF;
}

static bool check_p(Parser* parser, TokenType type) {
    if (is_at_end_p(parser)) return false;
    return peek_token(parser).type == type;
}

static Token advance_p(Parser* parser) {
    if (!is_at_end_p(parser)) parser->current++;
    return previous(parser);
}

static bool match_p(Parser* parser, TokenType type) {
    if (check_p(parser, type)) {
        advance_p(parser);
        return true;
    }
    return false;
}

static Token consume(Parser* parser, TokenType type, const char* message) {
    if (check_p(parser, type)) return advance_p(parser);
    
    Token tok = peek_token(parser);
    fprintf(stderr, "[PARSE ERROR] Line %d: %s (got '%s')\n", 
            tok.line, message, tok.lexeme);
    return tok;
}

/* ============================================================================
 * AST NODE CREATION
 * ============================================================================ */

static ASTNode* create_node(ASTNodeType type, int line) {
    ASTNode* node = malloc(sizeof(ASTNode));
    memset(node, 0, sizeof(ASTNode));
    node->type = type;
    node->line = line;
    return node;
}

/* ============================================================================
 * EXPRESSION PARSING
 * ============================================================================ */

static ASTNode* parse_expression(Parser* parser);
static ASTNode* parse_statement(Parser* parser);
static ASTNode* parse_block(Parser* parser);
static ASTNode* parse_function_declaration(Parser* parser);
static ASTNode* parse_id_block(Parser* parser);
static ASTNode* parse_ego_block(Parser* parser);
static ASTNode* parse_act_block(Parser* parser);

static ASTNode* parse_primary(Parser* parser) {
    if (match_p(parser, TOKEN_FUN)) {
        return parse_function_declaration(parser);
    }
    
    // Literals
    if (match_p(parser, TOKEN_NUMBER) || match_p(parser, TOKEN_STRING) ||
        match_p(parser, TOKEN_TRUE) || match_p(parser, TOKEN_FALSE) ||
        match_p(parser, TOKEN_NULL)) {
        ASTNode* node = create_node(AST_LITERAL, previous(parser).line);
        node->as.literal = previous(parser).literal;
        return node;
    }
    
    // Object Instantiation: new ClassName { field: val }
    if (match_p(parser, TOKEN_NEW)) {
        Token name = consume(parser, TOKEN_IDENTIFIER, "Expected class name after 'new'");
        consume(parser, TOKEN_LBRACE, "Expected '{' after class name");
        
        ASTNode* node = create_node(AST_OBJECT, name.line);
        node->as.obj_inst.class_name = strdup(name.lexeme);
        node->as.obj_inst.fields = malloc(sizeof(char*) * MAX_FIELDS);
        node->as.obj_inst.values = malloc(sizeof(ASTNode*) * MAX_FIELDS);
        node->as.obj_inst.count = 0;
        
        if (!check_p(parser, TOKEN_RBRACE)) {
            do {
                Token f_name = consume(parser, TOKEN_IDENTIFIER, "Expected field name");
                consume(parser, TOKEN_COLON, "Expected ':' after field name");
                ASTNode* f_val = parse_expression(parser);
                node->as.obj_inst.fields[node->as.obj_inst.count] = strdup(f_name.lexeme);
                node->as.obj_inst.values[node->as.obj_inst.count] = f_val;
                node->as.obj_inst.count++;
            } while (match_p(parser, TOKEN_COMMA));
        }
        consume(parser, TOKEN_RBRACE, "Expected '}' after instantiation fields");
        return node;
    }
    
    // Identifier
    if (match_p(parser, TOKEN_IDENTIFIER)) {
        Token name = previous(parser);
        ASTNode* node = create_node(AST_VARIABLE, name.line);
        node->as.var_name = strdup(name.lexeme);
        return node;
    }
    
    // Grouped expression
    if (match_p(parser, TOKEN_LPAREN)) {
        ASTNode* expr = parse_expression(parser);
        consume(parser, TOKEN_RPAREN, "Expected ')' after expression");
        return expr;
    }
    
    // Array literal
    if (match_p(parser, TOKEN_LBRACKET)) {
        ASTNode* node = create_node(AST_ARRAY, previous(parser).line);
        node->as.array_lit.elements = malloc(sizeof(ASTNode*) * MAX_ARRAY);
        node->as.array_lit.count = 0;
        
        if (!check_p(parser, TOKEN_RBRACKET)) {
            do {
                node->as.array_lit.elements[node->as.array_lit.count++] = parse_expression(parser);
            } while (match_p(parser, TOKEN_COMMA));
        }
        
        consume(parser, TOKEN_RBRACKET, "Expected ']' after array elements");
        return node;
    }
    
    // Map literal
    if (match_p(parser, TOKEN_LBRACE)) {
        ASTNode* node = create_node(AST_MAP, previous(parser).line);
        node->as.map_lit.keys = malloc(sizeof(char*) * MAX_FIELDS);
        node->as.map_lit.values = malloc(sizeof(ASTNode*) * MAX_FIELDS);
        node->as.map_lit.count = 0;
        
        if (!check_p(parser, TOKEN_RBRACE)) {
            do {
                // Key (string or identifier)
                char* key;
                if (match_p(parser, TOKEN_STRING)) {
                    key = strdup(previous(parser).literal.as.string);
                } else if (match_p(parser, TOKEN_IDENTIFIER)) {
                    key = strdup(previous(parser).lexeme);
                } else {
                    fprintf(stderr, "[PARSE ERROR] Expected map key\n");
                    return node;
                }
                
                consume(parser, TOKEN_COLON, "Expected ':' after map key");
                ASTNode* value = parse_expression(parser);
                
                node->as.map_lit.keys[node->as.map_lit.count] = key;
                node->as.map_lit.values[node->as.map_lit.count] = value;
                node->as.map_lit.count++;
            } while (match_p(parser, TOKEN_COMMA));
        }
        
        consume(parser, TOKEN_RBRACE, "Expected '}' after map");
        return node;
    }
    
    // Default fallback
    Token tok = peek_token(parser);
    if (tok.type != TOKEN_EOF) {
        fprintf(stderr, "[PARSE ERROR] Line %d: Unexpected token '%s'\n", tok.line, tok.lexeme);
        advance_p(parser);
    }
    return create_node(AST_LITERAL, tok.line);
}

static ASTNode* parse_call(Parser* parser) {
    ASTNode* expr = parse_primary(parser);
    
    while (true) {
        // Function call
        if (match_p(parser, TOKEN_LPAREN)) {
            ASTNode* call = create_node(AST_CALL, previous(parser).line);
            call->as.call.callee = expr;
            call->as.call.args = malloc(sizeof(ASTNode*) * MAX_ARGS);
            call->as.call.arg_count = 0;
            
            if (!check_p(parser, TOKEN_RPAREN)) {
                do {
                    call->as.call.args[call->as.call.arg_count++] = parse_expression(parser);
                } while (match_p(parser, TOKEN_COMMA));
            }
            
            consume(parser, TOKEN_RPAREN, "Expected ')' after arguments");
            expr = call;
        }
        // Property access
        else if (match_p(parser, TOKEN_DOT)) {
            Token name = consume(parser, TOKEN_IDENTIFIER, "Expected property name");
            ASTNode* get = create_node(AST_GET, name.line);
            get->as.get_expr.object = expr;
            get->as.get_expr.property = strdup(name.lexeme);
            expr = get;
        }
        // Index access
        else if (match_p(parser, TOKEN_LBRACKET)) {
            ASTNode* idx = create_node(AST_INDEX, previous(parser).line);
            idx->as.index_expr.object = expr;
            idx->as.index_expr.index = parse_expression(parser);
            consume(parser, TOKEN_RBRACKET, "Expected ']' after index");
            expr = idx;
        }
        else {
            break;
        }
    }
    
    return expr;
}

static ASTNode* parse_unary(Parser* parser) {
    if (match_p(parser, TOKEN_NOT) || match_p(parser, TOKEN_MINUS)) {
        Token op = previous(parser);
        ASTNode* right = parse_unary(parser);
        ASTNode* node = create_node(AST_UNARY, op.line);
        node->as.unary.op = op.type;
        node->as.unary.operand = right;
        return node;
    }
    
    return parse_call(parser);
}

static ASTNode* parse_factor(Parser* parser) {
    ASTNode* left = parse_unary(parser);
    
    while (match_p(parser, TOKEN_STAR) || match_p(parser, TOKEN_SLASH) || 
           match_p(parser, TOKEN_PERCENT)) {
        Token op = previous(parser);
        ASTNode* right = parse_unary(parser);
        ASTNode* node = create_node(AST_BINARY, op.line);
        node->as.binary.op = op.type;
        node->as.binary.left = left;
        node->as.binary.right = right;
        left = node;
    }
    
    return left;
}

static ASTNode* parse_term(Parser* parser) {
    ASTNode* left = parse_factor(parser);
    
    while (match_p(parser, TOKEN_PLUS) || match_p(parser, TOKEN_MINUS)) {
        Token op = previous(parser);
        ASTNode* right = parse_factor(parser);
        ASTNode* node = create_node(AST_BINARY, op.line);
        node->as.binary.op = op.type;
        node->as.binary.left = left;
        node->as.binary.right = right;
        left = node;
    }
    
    return left;
}

static ASTNode* parse_comparison(Parser* parser) {
    ASTNode* left = parse_term(parser);
    
    while (match_p(parser, TOKEN_LT) || match_p(parser, TOKEN_GT) ||
           match_p(parser, TOKEN_LTE) || match_p(parser, TOKEN_GTE) ||
           match_p(parser, TOKEN_IN)) {
        Token op = previous(parser);
        ASTNode* right = parse_term(parser);
        ASTNode* node = create_node(AST_BINARY, op.line);
        node->as.binary.op = op.type;
        node->as.binary.left = left;
        node->as.binary.right = right;
        left = node;
    }
    
    return left;
}

static ASTNode* parse_equality(Parser* parser) {
    ASTNode* left = parse_comparison(parser);
    
    while (match_p(parser, TOKEN_EQEQ) || match_p(parser, TOKEN_NEQ)) {
        Token op = previous(parser);
        ASTNode* right = parse_comparison(parser);
        ASTNode* node = create_node(AST_BINARY, op.line);
        node->as.binary.op = op.type;
        node->as.binary.left = left;
        node->as.binary.right = right;
        left = node;
    }
    
    return left;
}

static ASTNode* parse_and(Parser* parser) {
    ASTNode* left = parse_equality(parser);
    
    while (match_p(parser, TOKEN_AND)) {
        Token op = previous(parser);
        ASTNode* right = parse_equality(parser);
        ASTNode* node = create_node(AST_BINARY, op.line);
        node->as.binary.op = op.type;
        node->as.binary.left = left;
        node->as.binary.right = right;
        left = node;
    }
    
    return left;
}

static ASTNode* parse_or(Parser* parser) {
    ASTNode* left = parse_and(parser);
    
    while (match_p(parser, TOKEN_OR)) {
        Token op = previous(parser);
        ASTNode* right = parse_and(parser);
        ASTNode* node = create_node(AST_BINARY, op.line);
        node->as.binary.op = op.type;
        node->as.binary.left = left;
        node->as.binary.right = right;
        left = node;
    }
    return left;
}

static ASTNode* parse_assignment(Parser* parser) {
    ASTNode* expr = parse_or(parser);

    if (match_p(parser, TOKEN_EQ)) {
        ASTNode* value = parse_assignment(parser);
        
        if (expr->type == AST_VARIABLE) {
            ASTNode* node = create_node(AST_ASSIGN, expr->line);
            node->as.assign.name = strdup(expr->as.var_name);
            node->as.assign.value = value;
            return node;
        } else if (expr->type == AST_GET) {
            ASTNode* node = create_node(AST_SET, expr->line);
            node->as.set_expr.object = expr->as.get_expr.object;
            node->as.set_expr.property = strdup(expr->as.get_expr.property);
            node->as.set_expr.value = value;
            return node;
        } else if (expr->type == AST_INDEX) {
            ASTNode* node = create_node(AST_INDEX_SET, expr->line);
            node->as.index_set.object = expr->as.index_expr.object;
            node->as.index_set.index = expr->as.index_expr.index;
            node->as.index_set.value = value;
            return node;
        }
    }
    
    return expr;
}

static ASTNode* parse_expression(Parser* parser) {
    return parse_assignment(parser);
}

/* ============================================================================
 * STATEMENT PARSING
 * ============================================================================ */

static ASTNode* parse_function_declaration(Parser* parser); // Forward declaration

static ASTNode* parse_var_declaration(Parser* parser) {
    Token name = consume(parser, TOKEN_IDENTIFIER, "Expected variable name");
    
    // Skip optional type
    if (match_p(parser, TOKEN_COLON)) {
        if (!match_p(parser, TOKEN_IDENTIFIER) && !match_p(parser, TOKEN_ANY)) {
            // Might be a list or map type, just consume tokens until EQ or newline
            while (!check_p(parser, TOKEN_EQ) && !check_p(parser, TOKEN_NEWLINE) && !is_at_end_p(parser)) {
                advance_p(parser);
            }
        }
    }
    
    ASTNode* node = create_node(AST_VAR_DECL, name.line);
    node->as.var_decl.name = strdup(name.lexeme);
    node->as.var_decl.initializer = NULL;
    
    if (match_p(parser, TOKEN_EQ)) {
        node->as.var_decl.initializer = parse_expression(parser);
    }
    
    return node;
}

static ASTNode* parse_function_declaration(Parser* parser) {
    char* name_str = NULL;
    if (check_p(parser, TOKEN_IDENTIFIER)) {
        Token name = consume(parser, TOKEN_IDENTIFIER, "Expected function name");
        name_str = strdup(name.lexeme);
    }
    
    ASTNode* node = create_node(AST_FUN_DECL, previous(parser).line);
    node->as.fun_decl.name = name_str;
    node->as.fun_decl.params = malloc(sizeof(char*) * MAX_ARGS);
    node->as.fun_decl.param_count = 0;
    
    // Parameters
    consume(parser, TOKEN_LPAREN, "Expected '(' after function name");
    if (!check_p(parser, TOKEN_RPAREN)) {
        do {
            Token param = consume(parser, TOKEN_IDENTIFIER, "Expected parameter name");
            // Skip optional parameter type
            if (match_p(parser, TOKEN_COLON)) {
                if (!match_p(parser, TOKEN_IDENTIFIER) && !match_p(parser, TOKEN_ANY)) {
                    // Skip complex types
                    while (!check_p(parser, TOKEN_COMMA) && !check_p(parser, TOKEN_RPAREN) && !is_at_end_p(parser)) advance_p(parser);
                }
            }
            node->as.fun_decl.params[node->as.fun_decl.param_count++] = strdup(param.lexeme);
        } while (match_p(parser, TOKEN_COMMA));
    }
    consume(parser, TOKEN_RPAREN, "Expected ')' after parameters");
    
    // Skip return type
    if (match_p(parser, TOKEN_THIN_ARROW)) {
        consume(parser, TOKEN_IDENTIFIER, "Expected return type");
    }
    
    consume(parser, TOKEN_LBRACE, "Expected '{' before function body");
    node->as.fun_decl.body = parse_block(parser);
    
    return node;
}

static ASTNode* parse_when_statement(Parser* parser) {
    ASTNode* node = create_node(AST_WHEN, previous(parser).line);
    node->as.when_stmt.condition = parse_expression(parser);
    
    consume(parser, TOKEN_ARROW, "Expected '=>' after when condition");
    
    // Can be single statement or block
    if (check_p(parser, TOKEN_LBRACE)) {
        match_p(parser, TOKEN_LBRACE);
        node->as.when_stmt.body = parse_block(parser);
    } else {
        node->as.when_stmt.body = parse_statement(parser);
    }
    
    return node;
}

static ASTNode* parse_for_statement(Parser* parser) {
    Token var = consume(parser, TOKEN_IDENTIFIER, "Expected variable name in for");
    consume(parser, TOKEN_IN, "Expected 'in' after variable");
    
    ASTNode* node = create_node(AST_FOR, previous(parser).line);
    node->as.for_stmt.var_name = strdup(var.lexeme);
    node->as.for_stmt.iterable = parse_expression(parser);
    
    consume(parser, TOKEN_LBRACE, "Expected '{' before for body");
    node->as.for_stmt.body = parse_block(parser);
    
    return node;
}

static ASTNode* parse_while_statement(Parser* parser) {
    ASTNode* node = create_node(AST_WHILE, previous(parser).line);
    node->as.while_stmt.condition = parse_expression(parser);
    
    consume(parser, TOKEN_LBRACE, "Expected '{' before while body");
    node->as.while_stmt.body = parse_block(parser);
    
    return node;
}

static ASTNode* parse_if_statement(Parser* parser) {
    ASTNode* node = create_node(AST_IF, previous(parser).line);
    node->as.if_stmt.condition = parse_expression(parser);
    
    consume(parser, TOKEN_LBRACE, "Expected '{' before if body");
    node->as.if_stmt.then_branch = parse_block(parser);
    node->as.if_stmt.else_branch = NULL;
    
    if (match_p(parser, TOKEN_ELSE)) {
        if (match_p(parser, TOKEN_IF)) {
            node->as.if_stmt.else_branch = parse_if_statement(parser);
        } else {
            consume(parser, TOKEN_LBRACE, "Expected '{' before else body");
            node->as.if_stmt.else_branch = parse_block(parser);
        }
    }
    
    return node;
}

static ASTNode* parse_return_statement(Parser* parser) {
    ASTNode* node = create_node(AST_RETURN, previous(parser).line);
    node->as.return_stmt.value = NULL;
    
    if (!check_p(parser, TOKEN_RBRACE) && !is_at_end_p(parser)) {
        node->as.return_stmt.value = parse_expression(parser);
    }
    
    return node;
}

static ASTNode* parse_block(Parser* parser) {
    ASTNode* block = create_node(AST_BLOCK, peek_token(parser).line);
    block->as.block.statements = malloc(sizeof(ASTNode*) * MAX_STACK);
    block->as.block.stmt_count = 0;
    
    while (!check_p(parser, TOKEN_RBRACE) && !is_at_end_p(parser)) {
        block->as.block.statements[block->as.block.stmt_count++] = parse_statement(parser);
    }
    
    consume(parser, TOKEN_RBRACE, "Expected '}' after block");
    
    return block;
}

static ASTNode* parse_import_statement(Parser* parser) {
    ASTNode* node = create_node(AST_IMPORT, previous(parser).line);
    
    if (match_p(parser, TOKEN_LBRACE)) {
        // import { a, b } from "..."
        node->as.import_stmt.names = malloc(sizeof(char*) * MAX_ARGS);
        node->as.import_stmt.count = 0;
        do {
            Token name = consume(parser, TOKEN_IDENTIFIER, "Expected member name");
            node->as.import_stmt.names[node->as.import_stmt.count++] = strdup(name.lexeme);
        } while (match_p(parser, TOKEN_COMMA));
        consume(parser, TOKEN_RBRACE, "Expected '}' after import list");
        consume(parser, TOKEN_FROM, "Expected 'from' after import list");
    }
    
    Token path = consume(parser, TOKEN_STRING, "Expected import path");
    node->as.import_stmt.path = strdup(path.literal.as.string);
    
    return node;
}

static ASTNode* parse_export_statement(Parser* parser) {
    ASTNode* node = create_node(AST_EXPORT, previous(parser).line);
    
    if (match_p(parser, TOKEN_LBRACE)) {
        node->as.export_stmt.names = malloc(sizeof(char*) * MAX_ARGS);
        node->as.export_stmt.count = 0;
        do {
            Token name = consume(parser, TOKEN_IDENTIFIER, "Expected member name");
            node->as.export_stmt.names[node->as.export_stmt.count++] = strdup(name.lexeme);
        } while (match_p(parser, TOKEN_COMMA));
        consume(parser, TOKEN_RBRACE, "Expected '}' after export list");
    } else {
        node->as.export_stmt.names = malloc(sizeof(char*));
        node->as.export_stmt.count = 1;
        node->as.export_stmt.names[0] = strdup(parse_statement(parser)->as.var_decl.name);
    }
    
    return node;
}

static ASTNode* parse_class_declaration(Parser* parser) {
    Token name = consume(parser, TOKEN_IDENTIFIER, "Expected class name");
    ASTNode* node = create_node(AST_CLASS, name.line);
    node->as.class_decl.name = strdup(name.lexeme);
    node->as.class_decl.fields = malloc(sizeof(char*) * MAX_FIELDS);
    node->as.class_decl.field_count = 0;
    node->as.class_decl.methods = malloc(sizeof(ASTNode*) * MAX_FIELDS);
    node->as.class_decl.method_count = 0;
    
    consume(parser, TOKEN_LBRACE, "Expected '{' before class body");
    
    while (!check_p(parser, TOKEN_RBRACE) && !is_at_end_p(parser)) {
        if (match_p(parser, TOKEN_FIELD)) {
            Token field_name = consume(parser, TOKEN_IDENTIFIER, "Expected field name");
            node->as.class_decl.fields[node->as.class_decl.field_count++] = strdup(field_name.lexeme);
            
            // Optional type: name : type
            if (match_p(parser, TOKEN_COLON)) {
                if (check_p(parser, TOKEN_IDENTIFIER)) advance_p(parser);
            }
            
            // Optional initializer
            if (match_p(parser, TOKEN_EQ)) {
                parse_expression(parser); // Skip for now in basic impl
            }
        } else if (match_p(parser, TOKEN_METHOD) || match_p(parser, TOKEN_FUN)) {
            ASTNode* method = parse_function_declaration(parser);
            node->as.class_decl.methods[node->as.class_decl.method_count++] = method;
        } else {
            advance_p(parser); // Skip unknown class content
        }
    }
    
    consume(parser, TOKEN_RBRACE, "Expected '}' after class body");
    return node;
}

static ASTNode* parse_statement(Parser* parser) {
    if (match_p(parser, TOKEN_ID)) return parse_id_block(parser);
    if (match_p(parser, TOKEN_EGO)) return parse_ego_block(parser);
    if (match_p(parser, TOKEN_ACT)) return parse_act_block(parser);
    if (match_p(parser, TOKEN_IMPORT)) return parse_import_statement(parser);
    if (match_p(parser, TOKEN_EXPORT)) return parse_export_statement(parser);
    if (match_p(parser, TOKEN_CLASS)) return parse_class_declaration(parser);
    if (match_p(parser, TOKEN_VAR) || match_p(parser, TOKEN_CONST)) return parse_var_declaration(parser);
    if (match_p(parser, TOKEN_FUN)) return parse_function_declaration(parser);
    if (match_p(parser, TOKEN_WHEN)) return parse_when_statement(parser);
    if (match_p(parser, TOKEN_FOR)) return parse_for_statement(parser);
    if (match_p(parser, TOKEN_WHILE)) return parse_while_statement(parser);
    if (match_p(parser, TOKEN_IF)) return parse_if_statement(parser);
    if (match_p(parser, TOKEN_RETURN)) return parse_return_statement(parser);
    if (match_p(parser, TOKEN_BREAK)) return create_node(AST_BREAK, previous(parser).line);
    if (match_p(parser, TOKEN_CONTINUE)) return create_node(AST_CONTINUE, previous(parser).line);
    
    if (match_p(parser, TOKEN_TRY)) {
        consume(parser, TOKEN_LBRACE, "Expected '{' after try");
        ASTNode* try_block = parse_block(parser);
        consume(parser, TOKEN_CATCH, "Expected 'catch' after try block");
        consume(parser, TOKEN_IDENTIFIER, "Expected catch variable name");
        consume(parser, TOKEN_LBRACE, "Expected '{' after catch variable");
        parse_block(parser); // Skip catch block
        return try_block;
    }
    
    // Expression statement
    ASTNode* expr = parse_expression(parser);
    ASTNode* stmt = create_node(AST_EXPR_STMT, expr->line);
    stmt->as.block.statements = malloc(sizeof(ASTNode*));
    stmt->as.block.statements[0] = expr;
    stmt->as.block.stmt_count = 1;
    
    return stmt;
}

static ASTNode* parse_id_block(Parser* parser) {
    int line = previous(parser).line;
    consume(parser, TOKEN_LBRACE, "Expected '{' after ID");
    
    ASTNode* node = create_node(AST_ID_BLOCK, line);
    node->as.agentic_block.statements = malloc(sizeof(ASTNode*) * 100);
    node->as.agentic_block.count = 0;
    
    while (!check_p(parser, TOKEN_RBRACE) && !is_at_end_p(parser)) {
        if (match_p(parser, TOKEN_DRIVE)) {
            consume(parser, TOKEN_IDENTIFIER, "Expected identifier after 'drive'");
            char* name = strdup(previous(parser).lexeme);
            consume(parser, TOKEN_EQ, "Expected '=' after drive name");
            ASTNode* val = parse_expression(parser);
            ASTNode* decl = create_node(AST_DRIVE_DECL, previous(parser).line);
            decl->as.cognitive_decl.name = name;
            decl->as.cognitive_decl.value = val;
            node->as.agentic_block.statements[node->as.agentic_block.count++] = decl;
        } else if (match_p(parser, TOKEN_AFFECT)) {
            consume(parser, TOKEN_IDENTIFIER, "Expected identifier after 'affect'");
            char* name = strdup(previous(parser).lexeme);
            consume(parser, TOKEN_EQ, "Expected '=' after affect name");
            ASTNode* val = parse_expression(parser);
            ASTNode* decl = create_node(AST_AFFECT_DECL, previous(parser).line);
            decl->as.cognitive_decl.name = name;
            decl->as.cognitive_decl.value = val;
            node->as.agentic_block.statements[node->as.agentic_block.count++] = decl;
        } else {
            node->as.agentic_block.statements[node->as.agentic_block.count++] = parse_statement(parser);
        }
    }
    
    consume(parser, TOKEN_RBRACE, "Expected '}' after ID block");
    return node;
}

static ASTNode* parse_ego_block(Parser* parser) {
    int line = previous(parser).line;
    consume(parser, TOKEN_LBRACE, "Expected '{' after EGO");
    
    ASTNode* node = create_node(AST_EGO_BLOCK, line);
    node->as.agentic_block.statements = malloc(sizeof(ASTNode*) * 100);
    node->as.agentic_block.count = 0;
    
    while (!check_p(parser, TOKEN_RBRACE) && !is_at_end_p(parser)) {
        if (match_p(parser, TOKEN_FORBID)) {
            ASTNode* condition = parse_expression(parser);
            ASTNode* f_node = create_node(AST_FORBID, previous(parser).line);
            f_node->as.rule.condition = condition;
            node->as.agentic_block.statements[node->as.agentic_block.count++] = f_node;
        } else if (match_p(parser, TOKEN_BUDGET)) {
            ASTNode* limit = parse_expression(parser);
            ASTNode* b_node = create_node(AST_BUDGET, previous(parser).line);
            b_node->as.budget_stmt.limit = limit;
            node->as.agentic_block.statements[node->as.agentic_block.count++] = b_node;
        } else {
            node->as.agentic_block.statements[node->as.agentic_block.count++] = parse_statement(parser);
        }
    }
    
    consume(parser, TOKEN_RBRACE, "Expected '}' after EGO block");
    return node;
}

static ASTNode* parse_act_block(Parser* parser) {
    int line = previous(parser).line;
    consume(parser, TOKEN_LBRACE, "Expected '{' after ACT");
    
    ASTNode* node = create_node(AST_ACT_BLOCK, line);
    node->as.agentic_block.statements = malloc(sizeof(ASTNode*) * 100);
    node->as.agentic_block.count = 0;
    
    while (!check_p(parser, TOKEN_RBRACE) && !is_at_end_p(parser)) {
        node->as.agentic_block.statements[node->as.agentic_block.count++] = parse_statement(parser);
    }
    
    consume(parser, TOKEN_RBRACE, "Expected '}' after ACT block");
    return node;
}

/* ============================================================================
 * PUBLIC API
 * ============================================================================ */

Parser* parser_create(Token* tokens, int count) {
    Parser* parser = malloc(sizeof(Parser));
    parser->tokens = tokens;
    parser->token_count = count;
    parser->current = 0;
    return parser;
}

ASTNode* parser_parse(Parser* parser) {
    ASTNode* program = create_node(AST_PROGRAM, 1);
    program->as.block.statements = malloc(sizeof(ASTNode*) * MAX_STACK);
    program->as.block.stmt_count = 0;
    
    while (!is_at_end_p(parser)) {
        ASTNode* stmt = parse_statement(parser);
        if (stmt != NULL) {
            program->as.block.statements[program->as.block.stmt_count++] = stmt;
        }
    }
    
    return program;
}

void parser_free(Parser* parser) {
    if (parser != NULL) {
        free(parser);
    }
}

void ast_free(ASTNode* node) {
    // TODO: Implement proper AST cleanup
    (void)node;
}
