#include "agent_parser.h"
#include "memory.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

// ============================================================================
// LEXER HELPERS
// ============================================================================

void initAgentParser(AgentParser* parser, const char* source) {
    parser->source = source;
    parser->current = source;
    parser->line = 1;
    parser->hadError = false;
    parser->errorMessage[0] = '\0';
}

static void setError(AgentParser* parser, const char* message) {
    parser->hadError = true;
    snprintf(parser->errorMessage, sizeof(parser->errorMessage),
             "[line %d] Error: %s", parser->line, message);
}

const char* getParseError(AgentParser* parser) {
    return parser->errorMessage;
}

static bool isAtEnd(AgentParser* parser) {
    return *parser->current == '\0';
}

static char advance(AgentParser* parser) {
    parser->current++;
    return parser->current[-1];
}

static char peek(AgentParser* parser) {
    return *parser->current;
}

static char peekNext(AgentParser* parser) {
    if (isAtEnd(parser)) return '\0';
    return parser->current[1];
}

static void skipWhitespace(AgentParser* parser) {
    for (;;) {
        char c = peek(parser);
        switch (c) {
            case ' ':
            case '\r':
            case '\t':
                advance(parser);
                break;
            case '\n':
                parser->line++;
                advance(parser);
                break;
            case '#':
                while (peek(parser) != '\n' && !isAtEnd(parser)) {
                    advance(parser);
                }
                break;
            default:
                return;
        }
    }
}

static bool match(AgentParser* parser, char expected) {
    if (isAtEnd(parser)) return false;
    if (*parser->current != expected) return false;
    parser->current++;
    return true;
}

static bool matchKeyword(AgentParser* parser, const char* keyword) {
    skipWhitespace(parser);
    int len = (int)strlen(keyword);
    if (strncmp(parser->current, keyword, len) == 0 &&
        !isalnum(parser->current[len]) && parser->current[len] != '_') {
        parser->current += len;
        return true;
    }
    return false;
}

static char* parseIdentifier(AgentParser* parser) {
    skipWhitespace(parser);
    const char* start = parser->current;
    
    if (!isalpha(peek(parser)) && peek(parser) != '_') {
        return NULL;
    }
    
    while (isalnum(peek(parser)) || peek(parser) == '_' || peek(parser) == '.') {
        advance(parser);
    }
    
    int length = (int)(parser->current - start);
    char* identifier = (char*)malloc(length + 1);
    memcpy(identifier, start, length);
    identifier[length] = '\0';
    return identifier;
}

static char* parseString(AgentParser* parser) {
    skipWhitespace(parser);
    if (peek(parser) != '"') return NULL;
    advance(parser);  // Opening quote
    
    const char* start = parser->current;
    while (peek(parser) != '"' && !isAtEnd(parser)) {
        if (peek(parser) == '\n') parser->line++;
        advance(parser);
    }
    
    if (isAtEnd(parser)) {
        setError(parser, "Unterminated string");
        return NULL;
    }
    
    int length = (int)(parser->current - start);
    char* str = (char*)malloc(length + 1);
    memcpy(str, start, length);
    str[length] = '\0';
    
    advance(parser);  // Closing quote
    return str;
}

static double parseNumber(AgentParser* parser) {
    skipWhitespace(parser);
    const char* start = parser->current;
    
    if (peek(parser) == '-') advance(parser);
    
    while (isdigit(peek(parser))) advance(parser);
    
    if (peek(parser) == '.' && isdigit(peekNext(parser))) {
        advance(parser);
        while (isdigit(peek(parser))) advance(parser);
    }
    
    char buffer[64];
    int len = (int)(parser->current - start);
    if (len >= 64) len = 63;
    memcpy(buffer, start, len);
    buffer[len] = '\0';
    
    return atof(buffer);
}

static bool expect(AgentParser* parser, char c) {
    skipWhitespace(parser);
    if (peek(parser) != c) {
        char msg[64];
        snprintf(msg, sizeof(msg), "Expected '%c'", c);
        setError(parser, msg);
        return false;
    }
    advance(parser);
    return true;
}

// ============================================================================
// CONDITION PARSING
// ============================================================================

static Condition* parseCondition(AgentParser* parser);

static Condition* parsePrimaryCondition(AgentParser* parser) {
    skipWhitespace(parser);
    
    // intent("name")
    if (matchKeyword(parser, "intent")) {
        if (!expect(parser, '(')) return NULL;
        char* name = parseString(parser);
        if (name == NULL) {
            setError(parser, "Expected intent name");
            return NULL;
        }
        if (!expect(parser, ')')) { free(name); return NULL; }
        
        Condition* cond = createIntentCondition(copyString(name, (int)strlen(name)));
        free(name);
        return cond;
    }
    
    // fact("key")
    if (matchKeyword(parser, "fact")) {
        if (!expect(parser, '(')) return NULL;
        char* key = parseString(parser);
        if (key == NULL) {
            setError(parser, "Expected fact key");
            return NULL;
        }
        if (!expect(parser, ')')) { free(key); return NULL; }
        
        Condition* cond = createFactCondition(copyString(key, (int)strlen(key)));
        free(key);
        return cond;
    }
    
    // drive(name) > threshold
    if (matchKeyword(parser, "drive")) {
        if (!expect(parser, '(')) return NULL;
        char* name = parseIdentifier(parser);
        if (name == NULL) {
            setError(parser, "Expected drive name");
            return NULL;
        }
        if (!expect(parser, ')')) { free(name); return NULL; }
        
        skipWhitespace(parser);
        bool gt = true;
        if (peek(parser) == '>') {
            gt = true;
            advance(parser);
        } else if (peek(parser) == '<') {
            gt = false;
            advance(parser);
        }
        
        double threshold = parseNumber(parser);
        
        Condition* cond = createDriveCondition(
            copyString(name, (int)strlen(name)), threshold, gt);
        free(name);
        return cond;
    }
    
    // affect(name) > threshold
    if (matchKeyword(parser, "affect")) {
        if (!expect(parser, '(')) return NULL;
        char* name = parseIdentifier(parser);
        if (name == NULL) {
            setError(parser, "Expected affect name");
            return NULL;
        }
        if (!expect(parser, ')')) { free(name); return NULL; }
        
        skipWhitespace(parser);
        bool gt = true;
        if (peek(parser) == '>') {
            gt = true;
            advance(parser);
        } else if (peek(parser) == '<') {
            gt = false;
            advance(parser);
        }
        
        double threshold = parseNumber(parser);
        
        Condition* cond = createAffectCondition(
            copyString(name, (int)strlen(name)), threshold, gt);
        free(name);
        return cond;
    }
    
    // not <condition>
    if (matchKeyword(parser, "not")) {
        Condition* operand = parsePrimaryCondition(parser);
        if (operand == NULL) return NULL;
        return createNotCondition(operand);
    }
    
    // ( <condition> )
    if (match(parser, '(')) {
        Condition* inner = parseCondition(parser);
        if (!expect(parser, ')')) {
            freeCondition(inner);
            return NULL;
        }
        return inner;
    }
    
    // true / false
    if (matchKeyword(parser, "true")) {
        Condition* cond = (Condition*)malloc(sizeof(Condition));
        cond->type = COND_TRUE;
        return cond;
    }
    
    if (matchKeyword(parser, "false")) {
        Condition* cond = (Condition*)malloc(sizeof(Condition));
        cond->type = COND_FALSE;
        return cond;
    }
    
    setError(parser, "Expected condition");
    return NULL;
}

static Condition* parseCondition(AgentParser* parser) {
    Condition* left = parsePrimaryCondition(parser);
    if (left == NULL) return NULL;
    
    skipWhitespace(parser);
    
    // and / or
    while (!isAtEnd(parser)) {
        if (matchKeyword(parser, "and")) {
            Condition* right = parsePrimaryCondition(parser);
            if (right == NULL) {
                freeCondition(left);
                return NULL;
            }
            left = createAndCondition(left, right);
        } else if (matchKeyword(parser, "or")) {
            Condition* right = parsePrimaryCondition(parser);
            if (right == NULL) {
                freeCondition(left);
                return NULL;
            }
            left = createOrCondition(left, right);
        } else {
            break;
        }
        skipWhitespace(parser);
    }
    
    return left;
}

// ============================================================================
// BLOCK PARSING
// ============================================================================

static bool parseIdBlock(AgentParser* parser, SomniaRuntime* runtime) {
    if (!expect(parser, '{')) return false;
    
    int ruleCount = 0;
    
    while (!isAtEnd(parser)) {
        skipWhitespace(parser);
        if (peek(parser) == '}') break;
        
        // drive <name> = <intensity>
        if (matchKeyword(parser, "drive")) {
            char* name = parseIdentifier(parser);
            if (name == NULL) {
                setError(parser, "Expected drive name");
                return false;
            }
            
            if (!expect(parser, '=')) { free(name); return false; }
            double intensity = parseNumber(parser);
            
            printf("[PARSER] Drive: %s = %.2f\n", name, intensity);
            // Drives are added to context at runtime, not stored in engine
            free(name);
            continue;
        }
        
        // affect <name> = <valence>
        if (matchKeyword(parser, "affect")) {
            char* name = parseIdentifier(parser);
            if (name == NULL) {
                setError(parser, "Expected affect name");
                return false;
            }
            
            if (!expect(parser, '=')) { free(name); return false; }
            double valence = parseNumber(parser);
            
            printf("[PARSER] Affect: %s = %.2f\n", name, valence);
            free(name);
            continue;
        }
        
        // when <condition> => propose <action>(<args>) @<weight>
        if (matchKeyword(parser, "when")) {
            Condition* cond = parseCondition(parser);
            if (cond == NULL) return false;
            
            skipWhitespace(parser);
            if (!matchKeyword(parser, "=>")) {
                if (!(peek(parser) == '=' && peekNext(parser) == '>')) {
                    setError(parser, "Expected '=>'");
                    freeCondition(cond);
                    return false;
                }
                advance(parser);
                advance(parser);
            }
            
            if (!matchKeyword(parser, "propose")) {
                setError(parser, "Expected 'propose'");
                freeCondition(cond);
                return false;
            }
            
            char* action = parseIdentifier(parser);
            if (action == NULL) {
                setError(parser, "Expected action name");
                freeCondition(cond);
                return false;
            }
            
            // Parse optional args
            skipWhitespace(parser);
            Table args;
            initTable(&args);
            
            if (peek(parser) == '(') {
                advance(parser);
                while (peek(parser) != ')' && !isAtEnd(parser)) {
                    skipWhitespace(parser);
                    char* argName = parseIdentifier(parser);
                    if (argName == NULL) break;
                    
                    skipWhitespace(parser);
                    if (peek(parser) == ':') advance(parser);
                    
                    skipWhitespace(parser);
                    Value argValue = NULL_VAL;
                    
                    if (peek(parser) == '"') {
                        char* strVal = parseString(parser);
                        if (strVal) {
                            argValue = OBJ_VAL(copyString(strVal, (int)strlen(strVal)));
                            free(strVal);
                        }
                    } else if (isdigit(peek(parser)) || peek(parser) == '-') {
                        argValue = DOUBLE_VAL(parseNumber(parser));
                    } else if (matchKeyword(parser, "true")) {
                        argValue = BOOL_VAL(true);
                    } else if (matchKeyword(parser, "false")) {
                        argValue = BOOL_VAL(false);
                    }
                    
                    tableSet(&args, copyString(argName, (int)strlen(argName)), argValue);
                    free(argName);
                    
                    skipWhitespace(parser);
                    if (peek(parser) == ',') advance(parser);
                }
                if (peek(parser) == ')') advance(parser);
            }
            
            // Parse weight @0.8
            double weight = 0.5;  // default
            skipWhitespace(parser);
            if (peek(parser) == '@') {
                advance(parser);
                weight = parseNumber(parser);
            }
            
            // Create rule
            char ruleId[32];
            snprintf(ruleId, sizeof(ruleId), "rule_%d", ++ruleCount);
            
            Rule rule;
            rule.id = copyString(ruleId, (int)strlen(ruleId));
            rule.line = parser->line;
            rule.condition = cond;
            rule.action = copyString(action, (int)strlen(action));
            rule.actionArgs = args;
            rule.baseWeight = weight;
            
            addRule(&runtime->id, rule);
            
            printf("[PARSER] Rule: %s -> %s @%.2f\n", ruleId, action, weight);
            free(action);
            continue;
        }
        
        // Skip unknown
        advance(parser);
    }
    
    if (!expect(parser, '}')) return false;
    return true;
}

static bool parseEgoBlock(AgentParser* parser, SomniaRuntime* runtime) {
    if (!expect(parser, '{')) return false;
    
    while (!isAtEnd(parser)) {
        skipWhitespace(parser);
        if (peek(parser) == '}') break;
        
        // forbid when <condition>
        if (matchKeyword(parser, "forbid")) {
            if (!matchKeyword(parser, "when")) {
                setError(parser, "Expected 'when' after 'forbid'");
                return false;
            }
            
            Condition* cond = parseCondition(parser);
            if (cond == NULL) return false;
            
            char* actionPattern = NULL;
            skipWhitespace(parser);
            if (matchKeyword(parser, "action")) {
                skipWhitespace(parser);
                if (peek(parser) == '=') {
                    advance(parser);
                    advance(parser);  // skip ==
                }
                actionPattern = parseString(parser);
            }
            
            static int forbidCount = 0;
            char policyId[32];
            snprintf(policyId, sizeof(policyId), "forbid_%d", ++forbidCount);
            
            ForbidPolicy policy = createForbidPolicy(policyId, cond, actionPattern);
            addForbidPolicy(&runtime->ego, policy);
            
            printf("[PARSER] Forbid: %s\n", policyId);
            if (actionPattern) free(actionPattern);
            continue;
        }
        
        // budget "<action>" max <n> per <unit>
        if (matchKeyword(parser, "budget")) {
            char* action = parseString(parser);
            if (action == NULL) {
                setError(parser, "Expected action name");
                return false;
            }
            
            if (!matchKeyword(parser, "max")) {
                setError(parser, "Expected 'max'");
                free(action);
                return false;
            }
            
            int maxCount = (int)parseNumber(parser);
            
            if (!matchKeyword(parser, "per")) {
                setError(parser, "Expected 'per'");
                free(action);
                return false;
            }
            
            int windowSeconds = 60;  // default: per minute
            if (matchKeyword(parser, "second")) {
                windowSeconds = 1;
            } else if (matchKeyword(parser, "minute")) {
                windowSeconds = 60;
            } else if (matchKeyword(parser, "hour")) {
                windowSeconds = 3600;
            }
            
            BudgetPolicy policy = createBudgetPolicy(action, maxCount, windowSeconds);
            addBudgetPolicy(&runtime->ego, policy);
            
            printf("[PARSER] Budget: %s max %d per %ds\n", action, maxCount, windowSeconds);
            free(action);
            continue;
        }
        
        // select top <n>
        if (matchKeyword(parser, "select")) {
            if (!matchKeyword(parser, "top")) {
                setError(parser, "Expected 'top'");
                return false;
            }
            
            int n = (int)parseNumber(parser);
            runtime->ego.config.selectTopN = n;
            
            printf("[PARSER] Select top: %d\n", n);
            continue;
        }
        
        // on_tie use <method>
        if (matchKeyword(parser, "on_tie")) {
            if (!matchKeyword(parser, "use")) {
                setError(parser, "Expected 'use'");
                return false;
            }
            
            if (matchKeyword(parser, "rule_order")) {
                runtime->ego.config.tieBreaker = TIE_RULE_ORDER;
            } else if (matchKeyword(parser, "alphabetical")) {
                runtime->ego.config.tieBreaker = TIE_ALPHABETICAL;
            } else if (matchKeyword(parser, "hash")) {
                runtime->ego.config.tieBreaker = TIE_HASH_BASED;
            }
            
            printf("[PARSER] Tie breaker set\n");
            continue;
        }
        
        // Skip unknown
        advance(parser);
    }
    
    if (!expect(parser, '}')) return false;
    return true;
}

static bool parseActBlock(AgentParser* parser, SomniaRuntime* runtime) {
    (void)runtime;  // Actions are pre-registered
    
    if (!expect(parser, '{')) return false;
    
    while (!isAtEnd(parser)) {
        skipWhitespace(parser);
        if (peek(parser) == '}') break;
        
        // action <name> { ... }
        if (matchKeyword(parser, "action")) {
            char* name = parseIdentifier(parser);
            if (name == NULL) {
                setError(parser, "Expected action name");
                return false;
            }
            
            printf("[PARSER] Action declared: %s\n", name);
            
            // Skip action body for now (actions are pre-registered in C)
            skipWhitespace(parser);
            if (peek(parser) == '{') {
                int depth = 1;
                advance(parser);
                while (depth > 0 && !isAtEnd(parser)) {
                    if (peek(parser) == '{') depth++;
                    if (peek(parser) == '}') depth--;
                    if (peek(parser) == '\n') parser->line++;
                    advance(parser);
                }
            }
            
            free(name);
            continue;
        }
        
        // Skip unknown
        advance(parser);
    }
    
    if (!expect(parser, '}')) return false;
    return true;
}

// ============================================================================
// MAIN PARSER
// ============================================================================

bool parseAgentModule(AgentParser* parser, SomniaRuntime* runtime) {
    printf("[PARSER] Parsing agent module...\n");
    
    while (!isAtEnd(parser)) {
        skipWhitespace(parser);
        if (isAtEnd(parser)) break;
        
        // module "name"
        if (matchKeyword(parser, "module")) {
            char* name = parseString(parser);
            if (name) {
                printf("[PARSER] Module: %s\n", name);
                free(name);
            }
            continue;
        }
        
        // version "x.y.z"
        if (matchKeyword(parser, "version")) {
            char* version = parseString(parser);
            if (version) {
                printf("[PARSER] Version: %s\n", version);
                free(version);
            }
            continue;
        }
        
        // ID { ... }
        if (matchKeyword(parser, "ID")) {
            if (!parseIdBlock(parser, runtime)) {
                return false;
            }
            continue;
        }
        
        // EGO { ... }
        if (matchKeyword(parser, "EGO")) {
            if (!parseEgoBlock(parser, runtime)) {
                return false;
            }
            continue;
        }
        
        // ACT { ... }
        if (matchKeyword(parser, "ACT")) {
            if (!parseActBlock(parser, runtime)) {
                return false;
            }
            continue;
        }
        
        // Skip unknown characters
        if (!isAtEnd(parser)) {
            advance(parser);
        }
    }
    
    printf("[PARSER] Done. Rules: %d\n", runtime->id.rules.count);
    return !parser->hadError;
}
