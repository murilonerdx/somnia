/*
 * Somnia Programming Language
 * Native Postgres Driver (libpq wrapper)
 */

#include "../include/somnia.h"
#include <libpq-fe.h>

/* native_sql_connect(dsn: string) -> handle: number */
Value native_sql_connect(Value* args, int arg_count, Env* env) {
    if (arg_count < 1 || args[0].type != VAL_STRING) {
        return value_number(-1);
    }
    
    PGconn* conn = PQconnectdb(args[0].as.string);
    if (PQstatus(conn) != CONNECTION_OK) {
        fprintf(stderr, "[SQL ERROR] Connection failed: %s\n", PQerrorMessage(conn));
        PQfinish(conn);
        return value_number(-1);
    }
    
    // We store the pointer as a number for now (hacky, but works for bridge)
    // Professional way: VAL_NATIVE_OBJECT
    return value_number((uintptr_t)conn);
}

/* native_sql_query(handle: number, sql: string, params: array) -> ResultSet */
Value native_sql_query(Value* args, int arg_count, Env* env) {
    if (arg_count < 3 || args[0].type != VAL_NUMBER || args[1].type != VAL_STRING) {
        return value_null();
    }
    
    PGconn* conn = (PGconn*)(uintptr_t)args[0].as.number;
    const char* sql = args[1].as.string;
    Array* params = args[2].as.array;
    
    // Convert Somnia params to C strings for PQexecParams
    const char** param_values = malloc(sizeof(char*) * params->count);
    for (int i = 0; i < params->count; i++) {
        param_values[i] = value_to_string(params->items[i]);
    }
    
    PGresult* res = PQexecParams(conn, sql, params->count, NULL, param_values, NULL, NULL, 0);
    
    // Free param strings
    for (int i = 0; i < params->count; i++) {
        free((void*)param_values[i]);
    }
    free(param_values);
    
    if (PQresultStatus(res) != PGRES_TUPLES_OK) {
        fprintf(stderr, "[SQL ERROR] Query failed: %s\n", PQerrorMessage(conn));
        PQclear(res);
        return value_null();
    }
    
    int rows = PQntuples(res);
    int cols = PQnfields(res);
    
    Value s_array = value_array();
    for (int i = 0; i < rows; i++) {
        Value row_map = value_map();
        for (int j = 0; j < cols; j++) {
            const char* field_name = PQfname(res, j);
            const char* val = PQgetvalue(res, i, j);
            map_set(row_map.as.map, field_name, PQgetisnull(res, i, j) ? value_null() : value_string(val));
        }
        array_push(s_array.as.array, row_map);
    }
    
    PQclear(res);
    
    Value result_obj = value_map(); // We should return a Map that looks like ResultSet
    map_set(result_obj.as.map, "rows", s_array);
    map_set(result_obj.as.map, "affected_count", value_number(rows));
    
    return result_obj;
}

/* native_sql_exec(handle: number, sql: string, params: array) -> number */
Value native_sql_exec(Value* args, int arg_count, Env* env) {
    if (arg_count < 3 || args[0].type != VAL_NUMBER || args[1].type != VAL_STRING) {
        return value_number(-1);
    }
    
    PGconn* conn = (PGconn*)(uintptr_t)args[0].as.number;
    const char* sql = args[1].as.string;
    Array* params = args[2].as.array;
    
    const char** param_values = malloc(sizeof(char*) * params->count);
    for (int i = 0; i < params->count; i++) {
        param_values[i] = value_to_string(params->items[i]);
    }
    
    PGresult* res = PQexecParams(conn, sql, params->count, NULL, param_values, NULL, NULL, 0);
    
    for (int i = 0; i < params->count; i++) {
        free((void*)param_values[i]);
    }
    free(param_values);
    
    ExecStatusType status = PQresultStatus(res);
    if (status != PGRES_COMMAND_OK) {
        fprintf(stderr, "[SQL ERROR] Exec failed: %s\n", PQerrorMessage(conn));
        PQclear(res);
        return value_number(-1);
    }
    
    int affected = atoi(PQcmdTuples(res));
    PQclear(res);
    return value_number(affected);
}
