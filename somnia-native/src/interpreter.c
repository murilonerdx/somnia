/*
 * Somnia Programming Language
 * Interpreter Implementation
 */

#include "../include/somnia.h"

/* Forward declarations */
static Value evaluate(Interpreter* interp, ASTNode* node);
static void execute(Interpreter* interp, ASTNode* node);

/* ============================================================================
 * INTERPRETER CREATION
 * ============================================================================ */

Interpreter* interpreter_create(void) {
    Interpreter* interp = malloc(sizeof(Interpreter));
    interp->global_env = env_create(NULL);
    interp->current_env = interp->global_env;
    interp->had_error = false;
    interp->returning = false;
    interp->breaking = false;
    interp->continuing = false;
    interp->return_value = value_null();
    interp->recur_depth = 0;
    interp->temp_count = 0;
    interp->cognitive_state = env_create(NULL);
    
    // Register standard library
    stdlib_register(interp->global_env);
    
    return interp;
}

void interpreter_free(Interpreter* interp) {
    if (interp != NULL) {
        env_free(interp->global_env);
        free_objects();
        free(interp);
    }
}

/* ============================================================================
 * GC SHADOW STACK HELPERS
 * ============================================================================ */

static void gc_push_temp(Interpreter* interp, Value v) {
    if (interp->temp_count < MAX_TEMP_STACK) {
        interp->temp_stack[interp->temp_count++] = v;
    }
}

static void gc_pop_temp(Interpreter* interp) {
    if (interp->temp_count > 0) {
        interp->temp_count--;
    }
}

/* ============================================================================
 * EXPRESSION EVALUATION
 * ============================================================================ */

static Value eval_binary(Interpreter* interp, ASTNode* node) {
    Value left;
    left = evaluate(interp, node->as.binary.left);
    
    // Short-circuit for 'and' and 'or'
    if (node->as.binary.op == TOKEN_AND) {
        if (!value_is_truthy(left)) return value_bool(false);
        return value_bool(value_is_truthy(evaluate(interp, node->as.binary.right)));
    }
    if (node->as.binary.op == TOKEN_OR) {
        if (value_is_truthy(left)) return value_bool(true);
        return value_bool(value_is_truthy(evaluate(interp, node->as.binary.right)));
    }
    
    Value right;
    right = evaluate(interp, node->as.binary.right);
    
    switch (node->as.binary.op) {
        case TOKEN_PLUS:
            if (left.type == VAL_NUMBER && right.type == VAL_NUMBER) {
                return value_number(left.as.number + right.as.number);
            }
            if (left.type == VAL_STRING || right.type == VAL_STRING) {
                char* ls = value_to_string(left);
                char* rs = value_to_string(right);
                char* result = malloc(strlen(ls) + strlen(rs) + 1);
                strcpy(result, ls);
                strcat(result, rs);
                Value v;
                v = value_string(result);
                free(ls); free(rs); free(result);
                return v;
            }
            if (left.type == VAL_ARRAY && right.type == VAL_ARRAY) {
                Value arr;
                arr = value_array();
                for (int i = 0; i < left.as.array->count; i++) {
                    array_push(arr.as.array, value_copy(left.as.array->items[i]));
                }
                for (int i = 0; i < right.as.array->count; i++) {
                    array_push(arr.as.array, value_copy(right.as.array->items[i]));
                }
                return arr;
            }
            break;
        
        case TOKEN_MINUS:
            return value_number(left.as.number - right.as.number);
        
        case TOKEN_STAR:
            return value_number(left.as.number * right.as.number);
        
        case TOKEN_SLASH:
            if (right.as.number == 0) {
                fprintf(stderr, "[ERROR] Division by zero\n");
                return value_number(0);
            }
            return value_number(left.as.number / right.as.number);
        
        case TOKEN_PERCENT:
            return value_number(fmod(left.as.number, right.as.number));
        
        case TOKEN_LT:
            return value_bool(left.as.number < right.as.number);
        
        case TOKEN_GT:
            return value_bool(left.as.number > right.as.number);
        
        case TOKEN_LTE:
            return value_bool(left.as.number <= right.as.number);
        
        case TOKEN_GTE:
            return value_bool(left.as.number >= right.as.number);
        
        case TOKEN_EQEQ:
            return value_bool(value_equals(left, right));
        
        case TOKEN_NEQ:
            return value_bool(!value_equals(left, right));
        
        case TOKEN_IN:
            if (right.type == VAL_ARRAY) {
                for (int i = 0; i < right.as.array->count; i++) {
                    if (value_equals(left, right.as.array->items[i])) {
                        return value_bool(true);
                    }
                }
                return value_bool(false);
            }
            if (right.type == VAL_MAP && left.type == VAL_STRING) {
                return value_bool(map_has(right.as.map, left.as.string));
            }
            if (right.type == VAL_STRING && left.type == VAL_STRING) {
                return value_bool(strstr(right.as.string, left.as.string) != NULL);
            }
            break;
        
        default:
            break;
    }
    
    return value_null();
}

static Value eval_unary(Interpreter* interp, ASTNode* node) {
    Value operand;
    operand = evaluate(interp, node->as.unary.operand);
    
    switch (node->as.unary.op) {
        case TOKEN_MINUS:
            return value_number(-operand.as.number);
        case TOKEN_NOT:
            return value_bool(!value_is_truthy(operand));
        default:
            break;
    }
    
    return value_null();
}

static Value eval_call(Interpreter* interp, ASTNode* node) {
    Value callee;
    Value self_val;
    bool has_self = false;
    
    self_val = value_null();

    // Detect method call for self-binding
    if (node->as.call.callee->type == AST_GET) {
        self_val = evaluate(interp, node->as.call.callee->as.get_expr.object);
        if (self_val.type == VAL_OBJECT) {
            Value* method_ptr = env_get(self_val.as.object->fields, node->as.call.callee->as.get_expr.property);
            if (method_ptr != NULL) {
                callee = *method_ptr;
                has_self = true;
            } else {
                callee = value_null();
            }
        } else if (self_val.type == VAL_MAP) {
            Value* method_ptr = map_get(self_val.as.map, node->as.call.callee->as.get_expr.property);
            callee = (method_ptr != NULL) ? *method_ptr : value_null();
        } else {
            callee = evaluate(interp, node->as.call.callee);
        }
    } else {
        callee = evaluate(interp, node->as.call.callee);
    }
    
    // Evaluate arguments
    Value args[MAX_ARGS];
    int arg_count = node->as.call.arg_count;
    if (arg_count > MAX_ARGS) arg_count = MAX_ARGS;
    for (int i = 0; i < arg_count; i++) {
        args[i] = evaluate(interp, node->as.call.args[i]);
    }
    
    if (callee.type == VAL_NATIVE_FN) {
        return callee.as.native_fn(args, arg_count, interp->current_env);
    }
    
    if (callee.type == VAL_FUNCTION) {
        Function* fn = callee.as.function;
        Env* fn_env = env_create(fn->closure);
        
        // Bind self/this for method calls
        if (has_self) {
            env_define(fn_env, "self", self_val, false);
            env_define(fn_env, "this", self_val, false);
        }
        
        for (int i = 0; i < fn->param_count && i < arg_count; i++) {
            env_define(fn_env, fn->params[i], args[i], false);
        }
        
        Env* previous = interp->current_env;
        interp->current_env = fn_env;
        execute(interp, fn->body);
        Value result = interp->return_value;
        interp->returning = false;
        interp->return_value = value_null();
        interp->current_env = previous;
        env_free(fn_env);
        return result;
    }
    
    fprintf(stderr, "[ERROR] Cannot call non-function value\n");
    return value_null();
}

static Value eval_index(Interpreter* interp, ASTNode* node) {
    Value object;
    Value index;
    object = evaluate(interp, node->as.index_expr.object);
    index = evaluate(interp, node->as.index_expr.index);
    
    if (object.type == VAL_ARRAY && index.type == VAL_NUMBER) {
        int idx = (int)index.as.number;
        if (idx < 0 || idx >= object.as.array->count) {
            return value_null();
        }
        return object.as.array->items[idx];
    }
    
    if (object.type == VAL_MAP && index.type == VAL_STRING) {
        Value* val = map_get(object.as.map, index.as.string);
        return val != NULL ? *val : value_null();
    }
    
    if (object.type == VAL_STRING && index.type == VAL_NUMBER) {
        int idx = (int)index.as.number;
        if (idx < 0 || idx >= (int)strlen(object.as.string)) {
            return value_string("");
        }
        char buf[2] = { object.as.string[idx], '\0' };
        return value_string(buf);
    }
    
    return value_null();
}

static Value evaluate_impl(Interpreter* interp, ASTNode* node);

static Value evaluate(Interpreter* interp, ASTNode* node) {
    if (interp->recur_depth > MAX_RECURSION_DEPTH) {
        fprintf(stderr, "[ERROR] Stack overflow (recursion depth > %d)\n", MAX_RECURSION_DEPTH);
        if (node) fprintf(stderr, "At line %d\n", node->line);
        interp->had_error = true;
        // In a real VM we would longjmp here, but for now just return null and propagate error
        return value_null();
    }
    
    interp->recur_depth++;
    Value val;
    val = evaluate_impl(interp, node);
    interp->recur_depth--;
    return val;
}

static Value evaluate_impl(Interpreter* interp, ASTNode* node) {
    if (node == NULL) return value_null();
    
    switch (node->type) {
        case AST_LITERAL:
            return value_copy(node->as.literal);
        
        case AST_VARIABLE: {
            Value* val = env_get(interp->current_env, node->as.var_name);
            if (val == NULL) {
                fprintf(stderr, "[ERROR] Undefined variable '%s'\n", node->as.var_name);
                return value_null();
            }
            return *val;
        }
        
        case AST_OBJECT: {
            Value* klass_ptr = env_get(interp->current_env, node->as.obj_inst.class_name);
            if (klass_ptr != NULL && klass_ptr->type == VAL_OBJECT && klass_ptr->as.object->ast != NULL) {
                ASTNode* class_node = klass_ptr->as.object->ast;
                Value obj;
                obj = value_object(class_node->as.class_decl.name, env_create(interp->global_env));
                
                // 1. Initialize registered fields to null
                for (int i = 0; i < class_node->as.class_decl.field_count; i++) {
                    env_define(obj.as.object->fields, class_node->as.class_decl.fields[i], value_null(), false);
                }
                
                // 2. Assign constructor literal values: Class { field: val }
                for (int i = 0; i < node->as.obj_inst.count; i++) {
                    Value val = evaluate(interp, node->as.obj_inst.values[i]);
                    if (!env_set(obj.as.object->fields, node->as.obj_inst.fields[i], val)) {
                        env_define(obj.as.object->fields, node->as.obj_inst.fields[i], val, false);
                    }
                }
                
                for (int i = 0; i < class_node->as.class_decl.method_count; i++) {
                    ASTNode* m_node = class_node->as.class_decl.methods[i];
                    Value m_val;
                    m_val = value_function();
                    Function* fn = m_val.as.function;
                    
                    fn->name = strdup(m_node->as.fun_decl.name);
                    fn->params = m_node->as.fun_decl.params;
                    fn->param_count = m_node->as.fun_decl.param_count;
                    fn->body = m_node->as.fun_decl.body;
                    fn->closure = interp->current_env;
                    
                    env_define(obj.as.object->fields, m_node->as.fun_decl.name, m_val, true);
                }
                return obj;
            }
            fprintf(stderr, "[ERROR] Class '%s' not found\n", node->as.obj_inst.class_name);
            return value_null();
        }
        
        case AST_INDEX_SET: {
            Value obj;
            Value idx;
            Value val;
            obj = evaluate(interp, node->as.index_set.object);
            idx = evaluate(interp, node->as.index_set.index);
            val = evaluate(interp, node->as.index_set.value);
            
            if (obj.type == VAL_ARRAY && idx.type == VAL_NUMBER) {
                array_set(obj.as.array, (int)idx.as.number, val);
            } else if (obj.type == VAL_MAP && idx.type == VAL_STRING) {
                map_set(obj.as.map, idx.as.string, val);
            }
            return val;
        }
        
        case AST_BINARY:
            return eval_binary(interp, node);
        
        case AST_UNARY:
            return eval_unary(interp, node);
        
        case AST_CALL:
            return eval_call(interp, node);
        
        case AST_INDEX:
            return eval_index(interp, node);
        
        case AST_ASSIGN: {
            Value val;
            val = evaluate(interp, node->as.assign.value);
            if (!env_set(interp->current_env, node->as.assign.name, val)) {
                fprintf(stderr, "[ERROR] Undefined variable '%s'\n", node->as.assign.name);
            }
            return val;
        }
        
        case AST_ARRAY: {
            Value arr;
            arr = value_array();
            for (int i = 0; i < node->as.array_lit.count; i++) {
                array_push(arr.as.array, evaluate(interp, node->as.array_lit.elements[i]));
            }
            return arr;
        }
        
        case AST_MAP: {
            Value m;
            m = value_map();
            for (int i = 0; i < node->as.map_lit.count; i++) {
                Value val = evaluate(interp, node->as.map_lit.values[i]);
                map_set(m.as.map, node->as.map_lit.keys[i], val);
            }
            return m;
        }
        
        case AST_GET: {
            Value obj;
            obj = evaluate(interp, node->as.get_expr.object);
            if (obj.type == VAL_MAP) {
                Value* val = map_get(obj.as.map, node->as.get_expr.property);
                return val != NULL ? *val : value_null();
            }
            if (obj.type == VAL_OBJECT) {
                Value* val = env_get(obj.as.object->fields, node->as.get_expr.property);
                return val != NULL ? *val : value_null();
            }
            return value_null();
        }
        
        case AST_SET: {
            Value obj;
            Value val;
            obj = evaluate(interp, node->as.set_expr.object);
            val = evaluate(interp, node->as.set_expr.value);
            
            if (obj.type == VAL_MAP) {
                map_set(obj.as.map, node->as.set_expr.property, val);
            } else if (obj.type == VAL_OBJECT) {
                // Try to set existing field, if not found, define it
                if (!env_set(obj.as.object->fields, node->as.set_expr.property, val)) {
                    env_define(obj.as.object->fields, node->as.set_expr.property, val, false);
                }
            }
            return val;
        }
        
        case AST_FUN_DECL: {
            Value val;
            val = value_function();
            Function* fn = val.as.function;
            
            fn->name = node->as.fun_decl.name ? strdup(node->as.fun_decl.name) : strdup("anonymous");
            fn->params = malloc(sizeof(char*) * node->as.fun_decl.param_count);
            fn->param_count = node->as.fun_decl.param_count;
            for (int i = 0; i < fn->param_count; i++) {
                fn->params[i] = strdup(node->as.fun_decl.params[i]);
            }
            fn->body = node->as.fun_decl.body;
            fn->closure = interp->current_env;
            
            if (node->as.fun_decl.name) {
                env_define(interp->current_env, fn->name, val, false);
            }
            return val;
        }
        
        default:
            return value_null();
    }
}

/* ============================================================================
 * STATEMENT EXECUTION
 * ============================================================================ */

static void execute(Interpreter* interp, ASTNode* node) {
    if (node == NULL || interp->returning || interp->breaking) return;
    
    switch (node->type) {
        case AST_PROGRAM:
        case AST_BLOCK:
            for (int i = 0; i < node->as.block.stmt_count; i++) {
                execute(interp, node->as.block.statements[i]);
                if (interp->returning || interp->breaking || interp->continuing) break;
            }
            break;
        
        case AST_IMPORT: {
            char* path = node->as.import_stmt.path;
            char full_path[1024];
            snprintf(full_path, sizeof(full_path), "%s.somnia", path);
            
            // If not already loaded, load and execute it
            if (env_get(interp->global_env, full_path) == NULL) {
                // Mark as loaded
                env_define(interp->global_env, full_path, value_bool(true), true);
                
                char* source = read_file(full_path);
                if (source) {
                    Lexer* lexer = lexer_create(source);
                    lexer_scan_tokens(lexer);
                    Parser* parser = parser_create(lexer->tokens, lexer->token_count);
                    ASTNode* program = parser_parse(parser);
                    
                    if (program) {
                        Env* old_env = interp->current_env;
                        interp->current_env = interp->global_env; // Run in global scope
                        execute(interp, program);
                        interp->current_env = old_env;
                    }
                    free(source);
                }
            }
            
            // Binding: always pull from global to current if named import
            if (node->as.import_stmt.count > 0) {
                for (int i = 0; i < node->as.import_stmt.count; i++) {
                    char* name = node->as.import_stmt.names[i];
                    Value* val_ptr = env_get(interp->global_env, name);
                    if (val_ptr != NULL) {
                        env_define(interp->current_env, name, *val_ptr, false);
                    } else {
                        fprintf(stderr, "[IMPORT ERROR] Member '%s' not found in global scope after importing '%s'\n", name, path);
                    }
                }
            }
            return;
        }

        case AST_ID_BLOCK:
        case AST_EGO_BLOCK:
        case AST_ACT_BLOCK: {
            for (int i = 0; i < node->as.agentic_block.count; i++) {
                execute(interp, node->as.agentic_block.statements[i]);
                if (interp->returning || interp->breaking || interp->continuing) break;
            }
            break;
        }
        
        case AST_DRIVE_DECL:
        case AST_AFFECT_DECL: {
            Value val = evaluate(interp, node->as.cognitive_decl.value);
            env_define(interp->cognitive_state, node->as.cognitive_decl.name, val, false);
            break;
        }
        
        case AST_FORBID:
            // printf("[EGO] Forbid rule added\n");
            break;
            
        case AST_BUDGET:
            // printf("[EGO] Budget set\n");
            break;
        
        case AST_EXPORT: {
            // Simplified: just execute the child statements if any
            // (The names are already parsed but for now we just run)
            return;
        }
        
        case AST_CLASS: {
            Value class_val;
            class_val = value_object(node->as.class_decl.name, env_create(interp->global_env));
            class_val.as.object->ast = node;
            env_define(interp->current_env, node->as.class_decl.name, class_val, true);
            break;
        }
        
        case AST_VAR_DECL: {
            Value val;
            val = value_null();
            if (node->as.var_decl.initializer != NULL) {
                val = evaluate(interp, node->as.var_decl.initializer);
            }
            env_define(interp->current_env, node->as.var_decl.name, val, false);
            break;
        }
        
        case AST_FUN_DECL: {
            evaluate(interp, node);
            break;
        }
        
        case AST_RETURN:
            interp->return_value = node->as.return_stmt.value != NULL 
                ? evaluate(interp, node->as.return_stmt.value)
                : value_null();
            interp->returning = true;
            break;
        
        case AST_WHEN: {
            Value cond;
            cond = evaluate(interp, node->as.when_stmt.condition);
            if (value_is_truthy(cond)) {
                execute(interp, node->as.when_stmt.body);
            }
            break;
        }
        
        case AST_IF: {
            Value cond;
            cond = evaluate(interp, node->as.if_stmt.condition);
            if (value_is_truthy(cond)) {
                execute(interp, node->as.if_stmt.then_branch);
            } else if (node->as.if_stmt.else_branch != NULL) {
                execute(interp, node->as.if_stmt.else_branch);
            }
            break;
        }
        
        case AST_FOR: {
            Value iterable;
            iterable = evaluate(interp, node->as.for_stmt.iterable);
            
            Env* loop_env = env_create(interp->current_env);
            Env* previous = interp->current_env;
            interp->current_env = loop_env;
            
            if (iterable.type == VAL_ARRAY) {
                for (int i = 0; i < iterable.as.array->count; i++) {
                    env_define(loop_env, node->as.for_stmt.var_name, 
                              iterable.as.array->items[i], false);
                    execute(interp, node->as.for_stmt.body);
                    
                    if (interp->breaking) {
                        interp->breaking = false;
                        break;
                    }
                    if (interp->continuing) {
                        interp->continuing = false;
                    }
                    if (interp->returning) break;
                }
            }
            
            interp->current_env = previous;
            env_free(loop_env);
            break;
        }
        
        case AST_WHILE: {
            Env* loop_env = env_create(interp->current_env);
            Env* previous = interp->current_env;
            interp->current_env = loop_env;
            
            while (value_is_truthy(evaluate(interp, node->as.while_stmt.condition))) {
                execute(interp, node->as.while_stmt.body);
                
                if (interp->breaking) {
                    interp->breaking = false;
                    break;
                }
                if (interp->continuing) {
                    interp->continuing = false;
                }
                if (interp->returning) break;
            }
            
            interp->current_env = previous;
            env_free(loop_env);
            break;
        }
        
        case AST_BREAK:
            interp->breaking = true;
            break;
        
        case AST_CONTINUE:
            interp->continuing = true;
            break;
        
        case AST_EXPR_STMT:
            if (node->as.block.stmt_count > 0) {
                evaluate(interp, node->as.block.statements[0]);
            }
            break;
        
        default:
            break;
    }
}

/* ============================================================================
 * PUBLIC API
 * ============================================================================ */

Value interpreter_run(Interpreter* interp, ASTNode* program) {
    execute(interp, program);
    return interp->return_value;
}
