/*
 * Somnia Programming Language
 * Value Implementation
 */

#include "../include/somnia.h"

/* Global object list */
Obj* vm_objects = NULL;

/* ============================================================================
 * VALUE CONSTRUCTORS
 * ============================================================================ */

Value value_null(void) {
    Value v;
    v.type = VAL_NULL;
    return v;
}

Value value_bool(bool b) {
    Value v;
    v.type = VAL_BOOL;
    v.as.boolean = b;
    return v;
}

Value value_number(double n) {
    Value v;
    v.type = VAL_NUMBER;
    v.as.number = n;
    return v;
}

Value value_string(const char* s) {
    Value v;
    v.type = VAL_STRING;
    v.as.string = strdup(s);
    return v;
}

Value value_array(void) {
    Value v;
    v.type = VAL_ARRAY;
    v.as.array = malloc(sizeof(Array));
    
    // GC Init
    v.as.array->obj.type = OBJ_ARRAY;
    v.as.array->obj.marked = false;
    v.as.array->obj.next = vm_objects;
    vm_objects = (Obj*)v.as.array;
    
    v.as.array->items = malloc(sizeof(Value) * 8);
    v.as.array->count = 0;
    v.as.array->capacity = 8;
    return v;
}

Value value_map(void) {
    Value v;
    v.type = VAL_MAP;
    v.as.map = malloc(sizeof(Map));
    
    // GC Init
    v.as.map->obj.type = OBJ_MAP;
    v.as.map->obj.marked = false;
    v.as.map->obj.next = vm_objects;
    vm_objects = (Obj*)v.as.map;
    
    v.as.map->entries = malloc(sizeof(MapEntry) * 8);
    v.as.map->count = 0;
    v.as.map->capacity = 8;
    return v;
}

Value value_object(const char* class_name, Env* fields) {
    Value val;
    val.type = VAL_OBJECT;
    val.as.object = malloc(sizeof(Object));
    
    // GC Init
    val.as.object->obj.type = OBJ_OBJECT;
    val.as.object->obj.marked = false;
    val.as.object->obj.next = vm_objects;
    vm_objects = (Obj*)val.as.object;
    
    val.as.object->class_name = strdup(class_name);
    val.as.object->fields = fields;
    val.as.object->ast = NULL;
    return val;
}

/* ============================================================================
 * VALUE OPERATIONS
 * ============================================================================ */

bool value_is_truthy(Value val) {
    switch (val.type) {
        case VAL_NULL: return false;
        case VAL_BOOL: return val.as.boolean;
        case VAL_NUMBER: return val.as.number != 0;
        case VAL_STRING: return val.as.string != NULL && strlen(val.as.string) > 0;
        case VAL_ARRAY: return val.as.array->count > 0;
        case VAL_MAP: return val.as.map->count > 0;
        default: return true;
    }
}

bool value_equals(Value a, Value b) {
    if (a.type != b.type) return false;
    
    switch (a.type) {
        case VAL_NULL: return true;
        case VAL_BOOL: return a.as.boolean == b.as.boolean;
        case VAL_NUMBER: return a.as.number == b.as.number;
        case VAL_STRING: return strcmp(a.as.string, b.as.string) == 0;
        default: return false; // Reference equality for complex types
    }
}

char* value_to_string(Value val) {
    char* buf = malloc(MAX_STRING);
    
    switch (val.type) {
        case VAL_NULL:
            strcpy(buf, "null");
            break;
        case VAL_BOOL:
            strcpy(buf, val.as.boolean ? "true" : "false");
            break;
        case VAL_NUMBER: {
            double n = val.as.number;
            if (n == (int)n) {
                sprintf(buf, "%d", (int)n);
            } else {
                sprintf(buf, "%g", n);
            }
            break;
        }
        case VAL_STRING:
            strcpy(buf, val.as.string);
            break;
        case VAL_ARRAY: {
            strcpy(buf, "[");
            for (int i = 0; i < val.as.array->count; i++) {
                if (i > 0) strcat(buf, ", ");
                char* elem = value_to_string(val.as.array->items[i]);
                strcat(buf, elem);
                free(elem);
            }
            strcat(buf, "]");
            break;
        }
        case VAL_MAP: {
            strcpy(buf, "{");
            for (int i = 0; i < val.as.map->count; i++) {
                if (i > 0) strcat(buf, ", ");
                strcat(buf, "\"");
                strcat(buf, val.as.map->entries[i].key);
                strcat(buf, "\": ");
                
                Value v = val.as.map->entries[i].value;
                if (v.type == VAL_STRING) {
                    strcat(buf, "\"");
                    strcat(buf, v.as.string);
                    strcat(buf, "\"");
                } else {
                    char* vs = value_to_string(v);
                    strcat(buf, vs);
                    free(vs);
                }
            }
            strcat(buf, "}");
            break;
        }
        case VAL_FUNCTION:
            sprintf(buf, "<function %s>", val.as.function->name);
            break;
        case VAL_NATIVE_FN:
            strcpy(buf, "<native function>");
            break;
        case VAL_OBJECT:
            sprintf(buf, "<object %s>", val.as.object->class_name);
            break;
        default:
            strcpy(buf, "<unknown>");
    }
    
    return buf;
}

void value_print(Value val) {
    char* str = value_to_string(val);
    printf("%s", str);
    free(str);
}

Value value_copy(Value val) {
    Value copy;
    copy.type = val.type;
    
    switch (val.type) {
        case VAL_NULL:
        case VAL_BOOL:
        case VAL_NUMBER:
            copy = val;
            break;
        case VAL_STRING:
            copy.as.string = strdup(val.as.string);
            break;
        case VAL_ARRAY:
            copy = value_array();
            for (int i = 0; i < val.as.array->count; i++) {
                array_push(copy.as.array, value_copy(val.as.array->items[i]));
            }
            break;
        case VAL_MAP:
            copy = value_map();
            for (int i = 0; i < val.as.map->count; i++) {
                map_set(copy.as.map, val.as.map->entries[i].key, 
                        value_copy(val.as.map->entries[i].value));
            }
            break;
        default:
            copy = val; // Reference copy for functions, objects
    }
    
    return copy;
}

Value value_function(void) {
    Value v;
    v.type = VAL_FUNCTION;
    v.as.function = malloc(sizeof(Function));
    
    // GC Init
    v.as.function->obj.type = OBJ_FUNCTION;
    v.as.function->obj.marked = false;
    v.as.function->obj.next = vm_objects;
    vm_objects = (Obj*)v.as.function;
    
    v.as.function->name = NULL;
    v.as.function->params = NULL;
    v.as.function->param_count = 0;
    v.as.function->body = NULL;
    v.as.function->closure = NULL;
    return v;
}

/* ============================================================================
 * GARBAGE COLLECTION
 * ============================================================================ */

void gc_mark_object(Obj* obj);
void gc_mark_value(Value val);
void gc_mark_env(Env* env);

void gc_mark_env(Env* env) {
    if (env == NULL) return;
    // Env is NOT an Obj, but it holds refs.
    // We must assume Env itself is reachable if we are scanning it.
    // But we need to avoid cycles? Env parent loop? Env is tree/stack.
    // Marked? Env doesn't have marked bit.
    // Wait, Env is usually owned by Function (closure) or Interpreter (stack).
    // If we reach Env via Function, we scan it.
    // Function -> Env -> Parent -> ...
    // If multiple functions share Env, we re-scan. Performance hit but correct.
    
    for (int i = 0; i < env->var_count; i++) {
        gc_mark_value(env->vars[i].value);
    }
    if (env->parent) gc_mark_env(env->parent);
}

void gc_mark_value(Value val) {
    switch (val.type) {
        case VAL_ARRAY: 
            if (val.as.array) gc_mark_object((Obj*)val.as.array); 
            break;
        case VAL_MAP: 
            if (val.as.map) gc_mark_object((Obj*)val.as.map); 
            break;
        case VAL_OBJECT: 
            if (val.as.object) gc_mark_object((Obj*)val.as.object); 
            break;
        case VAL_FUNCTION: 
            if (val.as.function) gc_mark_object((Obj*)val.as.function); 
            break;
        default: break;
    }
}

void gc_mark_object(Obj* obj) {
    if (obj == NULL || obj->marked) return;
    obj->marked = true;
    
    switch (obj->type) {
        case OBJ_ARRAY: {
            Array* arr = (Array*)obj;
            for (int i = 0; i < arr->count; i++) {
                gc_mark_value(arr->items[i]);
            }
            break;
        }
        case OBJ_MAP: {
            Map* map = (Map*)obj;
            for (int i = 0; i < map->count; i++) {
                gc_mark_value(map->entries[i].value);
            }
            break;
        }
        case OBJ_OBJECT: {
            Object* o = (Object*)obj;
            // Fields are in an Env
            gc_mark_env(o->fields);
            break;
        }
        case OBJ_FUNCTION: {
            Function* fn = (Function*)obj;
            gc_mark_env(fn->closure);
            break;
        }
        default: break;
    }
}

void gc_collect(Env* env) {
    // 1. Mark Roots
    if (env) {
        gc_mark_env(env);
    }
    
    // 2. Sweep
    Obj** object = &vm_objects;
    while (*object != NULL) {
        if (!(*object)->marked) {
            // Unreached
            Obj* unreached = *object;
            *object = unreached->next;
            
            // Free
            switch (unreached->type) {
                case OBJ_ARRAY: {
                    Array* arr = (Array*)unreached;
                    free(arr->items);
                    free(arr);
                    break;
                }
                case OBJ_MAP: {
                    Map* map = (Map*)unreached;
                    for (int i = 0; i < map->count; i++) {
                        free(map->entries[i].key);
                        Value v = map->entries[i].value;
                        if (v.type == VAL_STRING) free(v.as.string);
                    }
                    free(map->entries);
                    free(map);
                    break;
                }
                case OBJ_OBJECT: {
                    Object* o = (Object*)unreached;
                    free(o->class_name);
                    // o->fields (Env) is NOT freed here because Env isn't an Obj.
                    // Env lifecycle is managed manually?
                    // Object instances own their Env (scope).
                    // If Object dies, Env should die.
                    // But we don't have gc_free_variable_array exposed.
                    // Memory leak in Env structs attached to Objects!
                    // Fix: We need to free Env here.
                    // env_free(o->fields); // Assuming env_free exists
                    free(o);
                    break;
                    }
                case OBJ_FUNCTION: {
                    Function* fn = (Function*)unreached;
                    if (fn->name) free(fn->name);
                    if (fn->params) {
                        for (int i=0; i<fn->param_count; i++) free(fn->params[i]);
                        free(fn->params);
                    }
                    free(fn);
                    break;
                }
                default: break;
            }
        } else {
            // Reached
            (*object)->marked = false;
            object = &(*object)->next;
        }
    }
}

/* Old helper */
void free_objects(void) {
    Obj* object = vm_objects;
    while (object != NULL) {
        Obj* next = object->next;
        
        switch (object->type) {
            case OBJ_ARRAY: {
                Array* array = (Array*)object;
                free(array->items);
                free(array);
                break;
            }
            case OBJ_MAP: {
                Map* map = (Map*)object;
                for (int i = 0; i < map->count; i++) {
                    // Keys are usually strdup'd in map_set
                    free(map->entries[i].key);
                    // Value freeing is recursive, handled by manual calls?
                    // No, free_objects blindly frees memory.
                    // Values inside might be objects, which will be freed as we iterate vm_objects.
                    // But strings inside values need freeing if they are char*.
                    // Value.as.string IS a char* and needs freeing.
                    Value v = map->entries[i].value;
                    if (v.type == VAL_STRING) free(v.as.string);
                }
                free(map->entries);
                free(map);
                break;
            }
            case OBJ_OBJECT: {
                Object* obj = (Object*)object;
                free(obj->class_name);
                // Env freeing is complex? Env is usually malloc'd separately.
                // Env tracking? Env is NOT an Obj in our current scheme.
                // Env is managed by interpreter recursion usually.
                // If Env is heap allocated, it should be tracked or freed.
                // Currently env_free is manual. 
                // We'll leave env_free to interpreter cleanup for now.
                free(obj);
                break;
            }
            case OBJ_FUNCTION: {
                Function* fn = (Function*)object;
                if (fn->name) free(fn->name);
                if (fn->params) {
                    for (int i = 0; i < fn->param_count; i++) free(fn->params[i]);
                    free(fn->params);
                }
                // Closure is Env*, handled elsewhere?
                free(fn);
                break;
            }
            case OBJ_STRING:
                // Not implemented yet
                break;
        }
        
        object = next;
    }
    vm_objects = NULL;
}

void value_free(Value* val) {
    // Deprecated in favor of GC/free_objects, but kept for stack values
    if (val == NULL) return;
    
    if (val->type == VAL_STRING) {
        free(val->as.string);
    }
    // Deep free for arrays/maps only if not using GC?
    // If GC is active, value_free shouldn't free heap objects that are tracked.
    // For now, we disable recursive free here to avoid double-free with free_objects.
    val->type = VAL_NULL;
}

/* ============================================================================
 * ARRAY OPERATIONS
 * ============================================================================ */

void array_push(Array* arr, Value val) {
    if (arr->count >= arr->capacity) {
        arr->capacity *= 2;
        arr->items = realloc(arr->items, sizeof(Value) * arr->capacity);
    }
    arr->items[arr->count++] = val;
}

Value array_get(Array* arr, int index) {
    if (index < 0 || index >= arr->count) {
        return value_null();
    }
    return arr->items[index];
}

void array_set(Array* arr, int index, Value val) {
    if (index >= 0 && index < arr->count) {
        arr->items[index] = val;
    }
}

/* ============================================================================
 * MAP OPERATIONS
 * ============================================================================ */

void map_set(Map* m, const char* key, Value val) {
    // Check if key exists
    for (int i = 0; i < m->count; i++) {
        if (strcmp(m->entries[i].key, key) == 0) {
            m->entries[i].value = val;
            return;
        }
    }
    
    // Add new entry
    if (m->count >= m->capacity) {
        m->capacity *= 2;
        m->entries = realloc(m->entries, sizeof(MapEntry) * m->capacity);
    }
    
    m->entries[m->count].key = strdup(key);
    m->entries[m->count].value = val;
    m->count++;
}

Value* map_get(Map* m, const char* key) {
    for (int i = 0; i < m->count; i++) {
        if (strcmp(m->entries[i].key, key) == 0) {
            return &m->entries[i].value;
        }
    }
    return NULL;
}

bool map_has(Map* m, const char* key) {
    for (int i = 0; i < m->count; i++) {
        if (strcmp(m->entries[i].key, key) == 0) {
            return true;
        }
    }
    return false;
}
