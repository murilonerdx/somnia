#include "act.h"
#include "memory.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#ifdef _WIN32
#include <windows.h>
#define SLEEP_MS(ms) Sleep(ms)
#else
#include <unistd.h>
#define SLEEP_MS(ms) usleep((ms) * 1000)
#endif

// ============================================================================
// ACTION REGISTRY
// ============================================================================

void initActionRegistry(ActionRegistry* registry) {
    registry->actions = NULL;
    registry->count = 0;
    registry->capacity = 0;
}

void freeActionRegistry(ActionRegistry* registry) {
    free(registry->actions);
    initActionRegistry(registry);
}

void registerAction(ActionRegistry* registry, ActionDef action) {
    if (registry->capacity < registry->count + 1) {
        int oldCapacity = registry->capacity;
        registry->capacity = GROW_CAPACITY(oldCapacity);
        registry->actions = (ActionDef*)realloc(registry->actions, 
            sizeof(ActionDef) * registry->capacity);
    }
    registry->actions[registry->count] = action;
    registry->count++;
}

ActionDef* findAction(ActionRegistry* registry, const char* name) {
    for (int i = 0; i < registry->count; i++) {
        if (strcmp(registry->actions[i].name->chars, name) == 0) {
            return &registry->actions[i];
        }
    }
    return NULL;
}

// ============================================================================
// ACT
// ============================================================================

void initAct(Act* act) {
    initActionRegistry(&act->registry);
    act->config.defaultTimeoutMs = 5000;
    act->config.maxConcurrent = 10;
    act->config.cancelOnError = false;
}

void freeAct(Act* act) {
    freeActionRegistry(&act->registry);
}

void setActConfig(Act* act, ActConfig config) {
    act->config = config;
}

// ============================================================================
// BUILT-IN ACTIONS
// ============================================================================

// log(level, message) or log(message)
ActionResult actionLog(Table* args, void* userData) {
    (void)userData;
    ActionResult result;
    result.type = ACTION_SUCCESS;
    result.errorMessage = NULL;
    
    // Get message
    Value msgVal = NULL_VAL;
    Value levelVal = NULL_VAL;
    
    ObjString* msgKey = copyString("message", 7);
    ObjString* levelKey = copyString("level", 5);
    
    tableGet(args, msgKey, &msgVal);
    tableGet(args, levelKey, &levelVal);
    
    const char* level = "INFO";
    if (IS_STRING(levelVal)) {
        level = AS_CSTRING(levelVal);
    }
    
    if (IS_STRING(msgVal)) {
        printf("[%s] %s\n", level, AS_CSTRING(msgVal));
    } else {
        printf("[%s] ", level);
        printValue(msgVal);
        printf("\n");
    }
    
    result.result = BOOL_VAL(true);
    result.durationMs = 0.1;
    return result;
}

// sleep(ms)
ActionResult actionSleep(Table* args, void* userData) {
    (void)userData;
    ActionResult result;
    result.type = ACTION_SUCCESS;
    result.errorMessage = NULL;
    
    // Get duration
    Value msVal = NULL_VAL;
    ObjString* msKey = copyString("ms", 2);
    tableGet(args, msKey, &msVal);
    
    int ms = 1000;  // default 1 second
    if (IS_INT(msVal)) {
        ms = (int)AS_INT(msVal);
    } else if (IS_DOUBLE(msVal)) {
        ms = (int)AS_DOUBLE(msVal);
    }
    
    clock_t start = clock();
    SLEEP_MS(ms);
    clock_t end = clock();
    
    result.result = INT_VAL(ms);
    result.durationMs = ((double)(end - start) / CLOCKS_PER_SEC) * 1000.0;
    return result;
}

// http.get(url) - Placeholder, would need libcurl for real implementation
ActionResult actionHttpGet(Table* args, void* userData) {
    (void)userData;
    ActionResult result;
    result.type = ACTION_SUCCESS;
    result.errorMessage = NULL;
    
    // Get URL
    Value urlVal = NULL_VAL;
    ObjString* urlKey = copyString("url", 3);
    tableGet(args, urlKey, &urlVal);
    
    if (!IS_STRING(urlVal)) {
        result.type = ACTION_ERROR_FATAL;
        result.errorMessage = copyString("URL must be a string", 20);
        result.result = NULL_VAL;
        return result;
    }
    
    printf("[HTTP.GET] %s\n", AS_CSTRING(urlVal));
    
    // Simulated response
    result.result = OBJ_VAL(copyString("{\"status\": \"ok\"}", 16));
    result.durationMs = 100.0;  // Simulated
    return result;
}

// http.post(url, body)
ActionResult actionHttpPost(Table* args, void* userData) {
    (void)userData;
    ActionResult result;
    result.type = ACTION_SUCCESS;
    result.errorMessage = NULL;
    
    // Get URL and body
    Value urlVal = NULL_VAL;
    Value bodyVal = NULL_VAL;
    ObjString* urlKey = copyString("url", 3);
    ObjString* bodyKey = copyString("body", 4);
    tableGet(args, urlKey, &urlVal);
    tableGet(args, bodyKey, &bodyVal);
    
    if (!IS_STRING(urlVal)) {
        result.type = ACTION_ERROR_FATAL;
        result.errorMessage = copyString("URL must be a string", 20);
        result.result = NULL_VAL;
        return result;
    }
    
    printf("[HTTP.POST] %s\n", AS_CSTRING(urlVal));
    if (IS_STRING(bodyVal)) {
        printf("[HTTP.POST] Body: %s\n", AS_CSTRING(bodyVal));
    }
    
    // Simulated response
    result.result = OBJ_VAL(copyString("{\"status\": \"created\"}", 21));
    result.durationMs = 150.0;  // Simulated
    return result;
}

// respond(status, body) - for web host
ActionResult actionRespond(Table* args, void* userData) {
    (void)userData;
    ActionResult result;
    result.type = ACTION_SUCCESS;
    result.errorMessage = NULL;
    
    // Get status and body
    Value statusVal = NULL_VAL;
    Value bodyVal = NULL_VAL;
    ObjString* statusKey = copyString("status", 6);
    ObjString* bodyKey = copyString("body", 4);
    tableGet(args, statusKey, &statusVal);
    tableGet(args, bodyKey, &bodyVal);
    
    int status = 200;
    if (IS_INT(statusVal)) {
        status = (int)AS_INT(statusVal);
    }
    
    printf("[RESPOND] Status: %d\n", status);
    if (IS_STRING(bodyVal)) {
        printf("[RESPOND] Body: %s\n", AS_CSTRING(bodyVal));
    }
    
    result.result = INT_VAL(status);
    result.durationMs = 0.5;
    return result;
}

// Register all built-in actions
void registerBuiltinActions(Act* act) {
    ActionDef logAction = {
        .name = copyString("log", 3),
        .handler = actionLog,
        .userData = NULL,
        .timeoutMs = 100,
        .maxRetries = 0,
        .retryable = false
    };
    registerAction(&act->registry, logAction);
    
    ActionDef sleepAction = {
        .name = copyString("sleep", 5),
        .handler = actionSleep,
        .userData = NULL,
        .timeoutMs = 60000,
        .maxRetries = 0,
        .retryable = false
    };
    registerAction(&act->registry, sleepAction);
    
    ActionDef httpGetAction = {
        .name = copyString("http.get", 8),
        .handler = actionHttpGet,
        .userData = NULL,
        .timeoutMs = 30000,
        .maxRetries = 3,
        .retryable = true
    };
    registerAction(&act->registry, httpGetAction);
    
    ActionDef httpPostAction = {
        .name = copyString("http.post", 9),
        .handler = actionHttpPost,
        .userData = NULL,
        .timeoutMs = 30000,
        .maxRetries = 2,
        .retryable = true
    };
    registerAction(&act->registry, httpPostAction);
    
    ActionDef respondAction = {
        .name = copyString("respond", 7),
        .handler = actionRespond,
        .userData = NULL,
        .timeoutMs = 100,
        .maxRetries = 0,
        .retryable = false
    };
    registerAction(&act->registry, respondAction);
}

// ============================================================================
// EXECUTION
// ============================================================================

ActionResult executeProposal(Act* act, Proposal* proposal) {
    ActionResult result;
    
    // Find action handler
    ActionDef* actionDef = findAction(&act->registry, proposal->action->chars);
    if (actionDef == NULL) {
        result.type = ACTION_ERROR_FATAL;
        result.errorMessage = copyString("Unknown action", 14);
        result.result = NULL_VAL;
        result.durationMs = 0;
        return result;
    }
    
    // Execute with retries
    int attempts = 0;
    int maxAttempts = actionDef->maxRetries + 1;
    
    while (attempts < maxAttempts) {
        clock_t start = clock();
        result = actionDef->handler(&proposal->args, actionDef->userData);
        clock_t end = clock();
        
        result.durationMs = ((double)(end - start) / CLOCKS_PER_SEC) * 1000.0;
        
        if (result.type == ACTION_SUCCESS) {
            return result;
        }
        
        if (!actionDef->retryable || result.type == ACTION_ERROR_FATAL) {
            return result;
        }
        
        attempts++;
        
        // Exponential backoff
        if (attempts < maxAttempts) {
            int backoffMs = 100 * (1 << attempts);  // 200, 400, 800...
            SLEEP_MS(backoffMs);
        }
    }
    
    return result;
}

ActionResult* executeAll(Act* act, SelectedProposal* proposals, int count, int* resultCount) {
    ActionResult* results = (ActionResult*)malloc(sizeof(ActionResult) * count);
    *resultCount = count;
    
    for (int i = 0; i < count; i++) {
        results[i] = executeProposal(act, &proposals[i].proposal);
        
        // Check if we should cancel on error
        if (act->config.cancelOnError && results[i].type != ACTION_SUCCESS) {
            // Fill remaining with cancelled
            for (int j = i + 1; j < count; j++) {
                results[j].type = ACTION_CANCELLED;
                results[j].errorMessage = copyString("Cancelled due to previous error", 31);
                results[j].result = NULL_VAL;
                results[j].durationMs = 0;
            }
            break;
        }
    }
    
    return results;
}
