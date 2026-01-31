#ifndef SOMNIA_TABLE_H
#define SOMNIA_TABLE_H

#include "common.h"
#include "value.h"

/**
 * Table Entry
 */
typedef struct {
    ObjString* key;
    Value value;
} Entry;

/**
 * Hash Table
 * Open addressing with linear probing.
 */
typedef struct Table {
    int count;
    int capacity;
    Entry* entries;
} Table;

void initTable(Table* table);
void freeTable(Table* table);
bool tableGet(Table* table, ObjString* key, Value* value);
bool tableSet(Table* table, ObjString* key, Value value);
bool tableDelete(Table* table, ObjString* key);
void tableAddAll(Table* from, Table* to);
ObjString* tableFindString(Table* table, const char* chars, int length, uint32_t hash);
void tableRemoveWhite(Table* table);
void markTable(Table* table);

#endif // SOMNIA_TABLE_H
