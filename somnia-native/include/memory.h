#ifndef SOMNIA_MEMORY_H
#define SOMNIA_MEMORY_H

#include "common.h"
#include "value.h"
#include "object.h"

/**
 * Memory Allocation Macros
 */
#define ALLOCATE(type, count) \
    (type*)reallocate(NULL, 0, sizeof(type) * (count))

#define FREE(type, pointer) \
    reallocate(pointer, sizeof(type), 0)

#define GROW_CAPACITY(capacity) \
    ((capacity) < 8 ? 8 : (capacity) * 2)

#define GROW_ARRAY(type, pointer, oldCount, newCount) \
    (type*)reallocate(pointer, sizeof(type) * (oldCount), \
        sizeof(type) * (newCount))

#define FREE_ARRAY(type, pointer, oldCount) \
    reallocate(pointer, sizeof(type) * (oldCount), 0)

/**
 * Core memory functions
 */
void* reallocate(void* pointer, size_t oldSize, size_t newSize);
void freeObject(Obj* object);

/**
 * Garbage Collector
 */
void collectGarbage(void);
void markValue(Value value);
void markObject(Obj* object);

/**
 * GC Metrics (for introspection)
 */
typedef struct {
    size_t bytesAllocated;
    size_t nextGC;
    size_t totalCollections;
    size_t totalFreed;
} GCMetrics;

GCMetrics* getGCMetrics(void);

#endif // SOMNIA_MEMORY_H
