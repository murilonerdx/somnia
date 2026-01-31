#ifndef SOMNIA_AGENT_PARSER_H
#define SOMNIA_AGENT_PARSER_H

#include "common.h"
#include "runtime.h"

/**
 * Parser for .somnia agent definition files.
 * 
 * Syntax:
 *   module "name"
 *   
 *   ID {
 *       drive <name> = <intensity>
 *       affect <name> = <valence>
 *       when <condition> => propose <action>(<args>) @<weight>
 *   }
 *   
 *   EGO {
 *       forbid when <condition>
 *       budget "<action>" max <n> per <unit>
 *       select top <n>
 *   }
 *   
 *   ACT {
 *       action <name> { ... }
 *   }
 */

typedef struct {
    const char* source;
    const char* current;
    int line;
    bool hadError;
    char errorMessage[256];
} AgentParser;

// Initialize parser with source code
void initAgentParser(AgentParser* parser, const char* source);

// Parse source and populate runtime
bool parseAgentModule(AgentParser* parser, SomniaRuntime* runtime);

// Get error message if parsing failed
const char* getParseError(AgentParser* parser);

#endif // SOMNIA_AGENT_PARSER_H
