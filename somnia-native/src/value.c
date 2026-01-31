/*
 * Somnia Programming Language
 * Value Implementation
 */

#include "../include/somnia.h"

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
    v.as.array->items = malloc(sizeof(Value) * 8);
    v.as.array->count = 0;
    v.as.array->capacity = 8;
    return v;
}

Value value_map(void) {
    Value v;
    v.type = VAL_MAP;
    v.as.map = malloc(sizeof(Map));
    v.as.map->entries = malloc(sizeof(MapEntry) * 8);
    v.as.map->count = 0;
    v.as.map->capacity = 8;
    return v;
}

Value value_object(const char* class_name, Env* fields) {
    Value val;
    val.type = VAL_OBJECT;
    val.as.object = malloc(sizeof(Object));
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

void value_free(Value* val) {
    if (val == NULL) return;
    
    switch (val->type) {
        case VAL_STRING:
            free(val->as.string);
            break;
        case VAL_ARRAY:
            for (int i = 0; i < val->as.array->count; i++) {
                value_free(&val->as.array->items[i]);
            }
            free(val->as.array->items);
            free(val->as.array);
            break;
        case VAL_MAP:
            for (int i = 0; i < val->as.map->count; i++) {
                free(val->as.map->entries[i].key);
                value_free(&val->as.map->entries[i].value);
            }
            free(val->as.map->entries);
            free(val->as.map);
            break;
        default:
            break;
    }
    
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
