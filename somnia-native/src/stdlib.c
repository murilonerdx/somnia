/*
 * Somnia Programming Language
 * Standard Library Implementation
 */

#include "../include/somnia.h"
#include <sys/time.h>
#include <time.h>
#include <ctype.h>
#include <math.h>

/* ============================================================================
 * NATIVE FUNCTIONS
 * ============================================================================ */

static Value native_println(Value* args, int arg_count, Env* env) {
    (void)env;
    for (int i = 0; i < arg_count; i++) {
        value_print(args[i]);
        if (i < arg_count - 1) printf(" ");
    }
    printf("\n");
    fflush(stdout);
    return value_null();
}

static Value native_eprintln(Value* args, int arg_count, Env* env) {
    (void)env;
    for (int i = 0; i < arg_count; i++) {
        char* str = value_to_string(args[i]);
        fprintf(stderr, "%s", str);
        if (i < arg_count - 1) fprintf(stderr, " ");
        free(str);
    }
    fprintf(stderr, "\n");
    fflush(stderr);
    return value_null();
}

static Value native_print(Value* args, int arg_count, Env* env) {
    (void)env;
    for (int i = 0; i < arg_count; i++) {
        value_print(args[i]);
    }
    return value_null();
}

static Value native_len(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1) return value_number(0);
    
    switch (args[0].type) {
        case VAL_STRING:
            return value_number(strlen(args[0].as.string));
        case VAL_ARRAY:
            return value_number(args[0].as.array->count);
        case VAL_MAP:
            return value_number(args[0].as.map->count);
        default:
            return value_number(0);
    }
}

static Value native_type(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1) return value_string("null");
    
    switch (args[0].type) {
        case VAL_NULL: return value_string("null");
        case VAL_BOOL: return value_string("bool");
        case VAL_NUMBER: return value_string("number");
        case VAL_STRING: return value_string("string");
        case VAL_ARRAY: return value_string("array");
        case VAL_MAP: return value_string("map");
        case VAL_FUNCTION: return value_string("function");
        case VAL_NATIVE_FN: return value_string("native_function");
        case VAL_OBJECT: return value_string("object");
        default: return value_string("unknown");
    }
}

static Value native_to_string(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1) return value_string("");
    return value_string(value_to_string(args[0]));
}

static Value native_to_number(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1) return value_number(0);
    
    if (args[0].type == VAL_NUMBER) return args[0];
    if (args[0].type == VAL_STRING) return value_number(atof(args[0].as.string));
    if (args[0].type == VAL_BOOL) return value_number(args[0].as.boolean ? 1 : 0);
    
    return value_number(0);
}

static Value native_range(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 2) return value_array();
    
    int start = (int)args[0].as.number;
    int end = (int)args[1].as.number;
    int step = arg_count >= 3 ? (int)args[2].as.number : 1;
    
    Value arr = value_array();
    
    if (step > 0) {
        for (int i = start; i < end; i += step) {
            array_push(arr.as.array, value_number(i));
        }
    } else if (step < 0) {
        for (int i = start; i > end; i += step) {
            array_push(arr.as.array, value_number(i));
        }
    }
    
    return arr;
}

static Value native_get_fields(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_OBJECT) return value_map();
    
    Object* obj = args[0].as.object;
    Value map_val = value_map();
    Map* m = map_val.as.map;
    
    // Copy fields from object's environment to a new map
    Env* fields = obj->fields;
    for (int i = 0; i < fields->var_count; i++) {
        map_set(m, fields->vars[i].name, value_copy(fields->vars[i].value));
    }
    
    return map_val;
}

static Value native_push(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 2 || args[0].type != VAL_ARRAY) return value_null();
    
    array_push(args[0].as.array, value_copy(args[1]));
    return args[0];
}

static Value native_pop(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_ARRAY) return value_null();
    
    Array* arr = args[0].as.array;
    if (arr->count == 0) return value_null();
    
    return arr->items[--arr->count];
}

static Value native_keys(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_MAP) return value_array();
    
    Value arr = value_array();
    Map* m = args[0].as.map;
    
    for (int i = 0; i < m->count; i++) {
        array_push(arr.as.array, value_string(m->entries[i].key));
    }
    
    return arr;
}

static Value native_values(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_MAP) return value_array();
    
    Value arr = value_array();
    Map* m = args[0].as.map;
    
    for (int i = 0; i < m->count; i++) {
        array_push(arr.as.array, value_copy(m->entries[i].value));
    }
    
    return arr;
}

static Value native_time_ms(Value* args, int arg_count, Env* env) {
    (void)args; (void)arg_count; (void)env;
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return value_number((double)tv.tv_sec * 1000 + (double)tv.tv_usec / 1000);
}

static Value native_hash(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_STRING) return value_string("0000");
    unsigned long hash = 5381;
    int c;
    const char* str = args[0].as.string;
    while ((c = *str++))
        hash = ((hash << 5) + hash) + c;
    char buf[32];
    sprintf(buf, "%lx", hash);
    return value_string(buf);
}

static Value native_parse_number(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_STRING) return value_number(0);
    return value_number(atof(args[0].as.string));
}

static Value native_parse_timestamp(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_NUMBER) return value_null();
    time_t ts = (time_t)(args[0].as.number / 1000);
    struct tm* info = gmtime(&ts);
    if (!info) return value_null();
    
    Value m = value_map();
    map_set(m.as.map, "year", value_number(info->tm_year + 1900));
    map_set(m.as.map, "month", value_number(info->tm_mon + 1));
    map_set(m.as.map, "day", value_number(info->tm_mday));
    map_set(m.as.map, "hour", value_number(info->tm_hour));
    map_set(m.as.map, "minute", value_number(info->tm_min));
    map_set(m.as.map, "second", value_number(info->tm_sec));
    return m;
}

static Value native_fs_read(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_STRING) return value_null();
    
    FILE* file = fopen(args[0].as.string, "rb");
    if (!file) return value_null();
    
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    fseek(file, 0, SEEK_SET);
    
    char* buffer = malloc(size + 1);
    if (!buffer) {
        fclose(file);
        return value_null();
    }
    
    fread(buffer, 1, size, file);
    buffer[size] = '\0';
    fclose(file);
    
    Value v = value_string(buffer);
    free(buffer);
    return v;
}

static Value native_fs_write(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 2 || args[0].type != VAL_STRING || args[1].type != VAL_STRING) return value_bool(false);
    
    FILE* file = fopen(args[0].as.string, "wb");
    if (!file) return value_bool(false);
    
    size_t written = fwrite(args[1].as.string, 1, strlen(args[1].as.string), file);
    fclose(file);
    
    return value_bool(written == strlen(args[1].as.string));
}

static Value native_input(Value* args, int arg_count, Env* env) {
    (void)env;
    
    if (arg_count > 0) {
        value_print(args[0]);
    }
    
    char buf[1024];
    if (fgets(buf, sizeof(buf), stdin)) {
        size_t len = strlen(buf);
        if (len > 0 && buf[len-1] == '\n') {
            buf[len-1] = '\0';
        }
        return value_string(buf);
    }
    
    return value_string("");
}

static Value native_split(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 2 || args[0].type != VAL_STRING || args[1].type != VAL_STRING) {
        return value_array();
    }
    
    Value arr = value_array();
    char* str = args[0].as.string;
    char* delim = args[1].as.string;
    int delim_len = strlen(delim);
    
    if (delim_len == 0) {
        // Fallback or handle appropriately
        array_push(arr.as.array, value_copy(args[0]));
        return arr;
    }
    
    char* current = str;
    char* next;
    
    while ((next = strstr(current, delim)) != NULL) {
        int len = next - current;
        char* part = malloc(len + 1);
        strncpy(part, current, len);
        part[len] = '\0';
        array_push(arr.as.array, value_string(part));
        free(part);
        current = next + delim_len;
    }
    
    array_push(arr.as.array, value_string(current));
    
    return arr;
}

static Value native_join(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 2 || args[0].type != VAL_ARRAY || args[1].type != VAL_STRING) {
        return value_string("");
    }
    
    Array* arr = args[0].as.array;
    char* sep = args[1].as.string;
    
    if (arr->count == 0) return value_string("");
    
    char* result = malloc(MAX_STRING);
    result[0] = '\0';
    
    for (int i = 0; i < arr->count; i++) {
        if (i > 0) strcat(result, sep);
        char* s = value_to_string(arr->items[i]);
        strcat(result, s);
        free(s);
    }
    
    Value v = value_string(result);
    free(result);
    return v;
}

static Value native_trim(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_STRING) return value_string("");
    
    char* str = args[0].as.string;
    while (isspace((unsigned char)*str)) str++;
    
    if (*str == 0) return value_string("");
    
    char* end = str + strlen(str) - 1;
    while (end > str && isspace((unsigned char)*end)) end--;
    
    int len = (int)(end - str + 1);
    char* trimmed = malloc(len + 1);
    strncpy(trimmed, str, len);
    trimmed[len] = '\0';
    
    Value v = value_string(trimmed);
    free(trimmed);
    return v;
}

static Value native_substr(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 2 || args[0].type != VAL_STRING) return value_string("");
    
    char* str = args[0].as.string;
    int start = (int)args[1].as.number;
    int len = arg_count >= 3 ? (int)args[2].as.number : (int)strlen(str) - start;
    
    if (start < 0) start = 0;
    if (start >= (int)strlen(str)) return value_string("");
    if (len < 0) len = 0;
    
    char* result = malloc(len + 1);
    strncpy(result, str + start, len);
    result[len] = '\0';
    
    Value v = value_string(result);
    free(result);
    return v;
}

static Value native_floor(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_NUMBER) return value_number(0);
    return value_number(floor(args[0].as.number));
}

static Value native_ceil(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_NUMBER) return value_number(0);
    return value_number(ceil(args[0].as.number));
}

static Value native_abs(Value* args, int arg_count, Env* env) {
    (void)env;
    if (arg_count < 1 || args[0].type != VAL_NUMBER) return value_number(0);
    return value_number(fabs(args[0].as.number));
}

static Value native_random(Value* args, int arg_count, Env* env) {
    (void)args; (void)arg_count; (void)env;
    return value_number((double)rand() / RAND_MAX);
}

static Value native_uptime(Value* args, int arg_count, Env* env) {
    (void)args; (void)arg_count; (void)env;
    static time_t start_time = 0;
    if (start_time == 0) start_time = time(NULL);
    return value_number((double)(time(NULL) - start_time));
}

static Value native_gc(Value* args, int arg_count, Env* env) {
    (void)args; (void)arg_count;
    // Trigger Mark-and-Sweep
    gc_collect(env);
    return value_null();
}

/* ============================================================================
 * REGISTER STDLIB
 * ============================================================================ */

static void register_native(Env* env, const char* name, NativeFn fn) {
    Value v;
    v.type = VAL_NATIVE_FN;
    v.as.native_fn = fn;
    env_define(env, name, v, true);
}

void stdlib_register(Env* env) {
    // I/O
    register_native(env, "println", native_println);
    register_native(env, "eprintln", native_eprintln);
    register_native(env, "print", native_print);
    register_native(env, "input", native_input);
    
    // Type conversions
    register_native(env, "native_to_string", native_to_string);
    register_native(env, "native_type", native_type);
    register_native(env, "native_to_number", native_to_number);
    
    // Collections
    register_native(env, "len", native_len);
    register_native(env, "range", native_range);
    register_native(env, "push", native_push);
    register_native(env, "pop", native_pop);
    register_native(env, "native_keys", native_keys);
    register_native(env, "native_values", native_values);
    register_native(env, "native_get_fields", native_get_fields);
    
    // Strings
    register_native(env, "split", native_split);
    register_native(env, "join", native_join);
    register_native(env, "substr", native_substr);
    register_native(env, "trim", native_trim);
    
    // Math
    register_native(env, "floor", native_floor);
    register_native(env, "ceil", native_ceil);
    register_native(env, "abs", native_abs);
    register_native(env, "random", native_random);
    
    // System
    register_native(env, "native_time_ms", native_time_ms);
    register_native(env, "native_uptime", native_uptime);
    register_native(env, "native_hash", native_hash);
    register_native(env, "native_parse_number", native_parse_number);
    register_native(env, "native_parse_timestamp", native_parse_timestamp);
    register_native(env, "native_fs_read", native_fs_read);
    register_native(env, "native_fs_write", native_fs_write);
    register_native(env, "gc", native_gc);

    // Network
    register_native(env, "native_net_listen", native_net_listen);
    register_native(env, "native_net_accept", native_net_accept);
    register_native(env, "native_net_read", native_net_read);
    register_native(env, "native_net_write", native_net_write);
    register_native(env, "native_net_close", native_net_close);
    
    // SQL
    register_native(env, "native_sql_connect", native_sql_connect);
    register_native(env, "native_sql_query", native_sql_query);
    register_native(env, "native_sql_exec", native_sql_exec);
    
    // Initialize random seed
    srand((unsigned int)time(NULL));
}
