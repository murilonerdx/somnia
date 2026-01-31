#ifndef SOMNIA_COMPILER_H
#define SOMNIA_COMPILER_H

#include "common.h"
#include "object.h"

ObjFunction* compile(const char* source);
void markCompilerRoots(void);

#endif // SOMNIA_COMPILER_H
