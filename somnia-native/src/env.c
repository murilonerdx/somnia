/*
 * Somnia Programming Language
 * Environment (Scope) Implementation
 */

#include "../include/somnia.h"

/* ============================================================================
 * ENVIRONMENT
 * ============================================================================ */

Env* env_create(Env* parent) {
    Env* env = malloc(sizeof(Env));
    env->vars = malloc(sizeof(Variable) * 64);
    env->var_count = 0;
    env->var_capacity = 64;
    env->parent = parent;
    return env;
}

void env_define(Env* env, const char* name, Value value, bool is_const) {
    // Check if already defined in this scope
    for (int i = 0; i < env->var_count; i++) {
        if (strcmp(env->vars[i].name, name) == 0) {
            env->vars[i].value = value;
            return;
        }
    }
    
    // Grow if needed
    if (env->var_count >= env->var_capacity) {
        env->var_capacity *= 2;
        env->vars = realloc(env->vars, sizeof(Variable) * env->var_capacity);
    }
    
    // Add new variable
    env->vars[env->var_count].name = strdup(name);
    env->vars[env->var_count].value = value;
    env->vars[env->var_count].is_const = is_const;
    env->var_count++;
}

Value* env_get(Env* env, const char* name) {
    // Search in current scope
    for (int i = 0; i < env->var_count; i++) {
        if (strcmp(env->vars[i].name, name) == 0) {
            return &env->vars[i].value;
        }
    }
    
    // Search in parent scope
    if (env->parent != NULL) {
        return env_get(env->parent, name);
    }
    
    return NULL;
}

bool env_set(Env* env, const char* name, Value value) {
    // Search in current scope
    for (int i = 0; i < env->var_count; i++) {
        if (strcmp(env->vars[i].name, name) == 0) {
            if (env->vars[i].is_const) {
                fprintf(stderr, "[ERROR] Cannot reassign constant '%s'\n", name);
                return false;
            }
            env->vars[i].value = value;
            return true;
        }
    }
    
    // Search in parent scope
    if (env->parent != NULL) {
        return env_set(env->parent, name, value);
    }
    
    return false;
}

void env_free(Env* env) {
    if (env == NULL) return;
    
    for (int i = 0; i < env->var_count; i++) {
        free(env->vars[i].name);
        // Don't free values as they might be shared
    }
    
    free(env->vars);
    free(env);
}
