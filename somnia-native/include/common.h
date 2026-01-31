#ifndef SOMNIA_COMMON_H
#define SOMNIA_COMMON_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Version
#define SOMNIA_VERSION_MAJOR 0
#define SOMNIA_VERSION_MINOR 1
#define SOMNIA_VERSION_PATCH 0

// Debug flags
#define DEBUG_TRACE_EXECUTION 0
#define DEBUG_PRINT_CODE 0
#define DEBUG_STRESS_GC 0
#define DEBUG_LOG_GC 0

// VM limits
#define UINT8_COUNT (UINT8_MAX + 1)
#define FRAMES_MAX 64
#define STACK_MAX (FRAMES_MAX * UINT8_COUNT)

// Platform detection
#if defined(_WIN32) || defined(_WIN64)
    #define SOMNIA_WINDOWS
#elif defined(__linux__)
    #define SOMNIA_LINUX
#elif defined(__APPLE__)
    #define SOMNIA_MACOS
#endif

#endif // SOMNIA_COMMON_H
