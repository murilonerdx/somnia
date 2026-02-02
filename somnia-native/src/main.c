/*
 * Somnia Programming Language
 * Main Entry Point
 */

#include "../include/somnia.h"

/* ============================================================================
 * UTILITIES
 * ============================================================================ */

char* read_file(const char* path) {
    FILE* file = fopen(path, "rb");
    if (file == NULL) {
        fprintf(stderr, "[ERROR] Could not open file: %s\n", path);
        return NULL;
    }
    
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    fseek(file, 0, SEEK_SET);
    
    char* buffer = malloc(size + 1);
    if (buffer == NULL) {
        fprintf(stderr, "[ERROR] Not enough memory to read file\n");
        fclose(file);
        return NULL;
    }
    
    size_t read = fread(buffer, 1, size, file);
    buffer[read] = '\0';
    
    fclose(file);
    return buffer;
}

#include <signal.h>
#include <unistd.h>
// #include <execinfo.h> // Commented out to avoid build errors if missing, purely optional

/* Global interpreter for signal handling */
static Interpreter* global_interp_for_signals = NULL;

void handle_signal(int sig) {
    fprintf(stderr, "\n");
    fprintf(stderr, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
    fprintf(stderr, "   CRASH DETECTED (Signal %d)\n", sig);
    fprintf(stderr, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
    
    if (global_interp_for_signals != NULL) {
        fprintf(stderr, "Recursion Depth: %d\n", global_interp_for_signals->recur_depth);
        // We could print more state here safely?
        // Allocating memory in signal handler is unsafe, so use raw fprintf
    }
    
    fprintf(stderr, "Stack Trace:\n");
    // void* array[20];
    // size_t size;
    // size = backtrace(array, 20);
    // backtrace_symbols_fd(array, size, STDERR_FILENO);
    
    fprintf(stderr, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
    exit(1);
}

void somnia_error(const char* message, int line) {
    fprintf(stderr, "[ERROR] Line %d: %s\n", line, message);
}

/* ============================================================================
 * RUN FILE
 * ============================================================================ */

static int run_file(const char* path) {
    printf("\n");
    printf("   _____  ____  __  __ _   _ _____          \n");
    printf("  / ____|/ __ \\|  \\/  | \\ | |_   _|   /\\    \n");
    printf(" | (___ | |  | | \\  / |  \\| | | |    /  \\   \n");
    printf("  \\___ \\| |  | | |\\/| | . ` | | |   / /\\ \\  \n");
    printf("  ____) | |__| | |  | | |\\  |_| |_ / ____ \\ \n");
    printf(" |_____/ \\____/|_|  |_|_| \\_|_____/_/    \\_\\\n");
    printf("\n");
    printf("     SOMNIA NATIVE v%s - High Performance Runtime\n", SOMNIA_VERSION);
    printf("     Built with Pure C - (c) 2026 Somnia Team\n");
    printf("----------------------------------------------------------------------\n");
    printf("[EXECUTING] %s\n\n", path);
    fflush(stdout);
    
    char* source = read_file(path);
    if (source == NULL) return 1;
    
    // Lexer
    Lexer* lexer = lexer_create(source);
    lexer_scan_tokens(lexer);
    
    // Parser
    Parser* parser = parser_create(lexer->tokens, lexer->token_count);
    ASTNode* program = parser_parse(parser);
    
    // Interpreter
    Interpreter* interp = interpreter_create();
    global_interp_for_signals = interp;
    interpreter_run(interp, program);
    
    // Cleanup
    interpreter_free(interp);
    ast_free(program);
    parser_free(parser);
    lexer_free(lexer);
    free(source);
    
    printf("\n[DONE] Execution complete\n");
    
    return 0;
}

static int run_bundle(const char* path) {
    printf("[BUNDLE] Loading %s...\n", path);
    fflush(stdout);
    FILE* file = fopen(path, "rb");
    if (!file) {
        fprintf(stderr, "[ERROR] Could not open bundle: %s\n", path);
        return 1;
    }

    char header[7];
    if (fread(header, 1, 7, file) != 7 || memcmp(header, "SOMNIA", 6) != 0) {
        fprintf(stderr, "[ERROR] Not a valid .som bundle\n");
        fclose(file);
        return 1;
    }

    uint16_t entry_len;
    if (fread(&entry_len, 2, 1, file) != 1) { fclose(file); return 1; }
    char* entry_point = malloc(entry_len + 1);
    fread(entry_point, 1, entry_len, file);
    entry_point[entry_len] = '\0';
    
    printf("[BUNDLE] Entry point: %s\n", entry_point);
    fflush(stdout);

    uint32_t file_count;
    if (fread(&file_count, 4, 1, file) != 1) { free(entry_point); fclose(file); return 1; }
    
    printf("[BUNDLE] Files found: %u\n", file_count);
    fflush(stdout);

    Interpreter* interp = interpreter_create();
    global_interp_for_signals = interp;

    for (uint32_t i = 0; i < file_count; i++) {
        uint16_t p_len;
        if (fread(&p_len, 2, 1, file) != 1) break;
        char* p_str = malloc(p_len + 1);
        fread(p_str, 1, p_len, file);
        p_str[p_len] = '\0';

        uint32_t c_len;
        if (fread(&c_len, 4, 1, file) != 1) { free(p_str); break; }
        char* c_str = malloc(c_len + 1);
        fread(c_str, 1, c_len, file);
        c_str[c_len] = '\0';

        map_set(interp->vfs, p_str, value_string(c_str));
        free(p_str);
        free(c_str);
    }
    fclose(file);

    printf("[BUNDLE] VFS populated. Starting execution...\n\n");
    fflush(stdout);

    // Run the entry point from VFS
    Value* entry_source_val = map_get(interp->vfs, entry_point);
    if (!entry_source_val || entry_source_val->type != VAL_STRING) {
        fprintf(stderr, "[ERROR] Entry point %s not found in bundle\n", entry_point);
        free(entry_point);
        interpreter_free(interp);
        return 1;
    }

    Lexer* lexer = lexer_create(entry_source_val->as.string);
    lexer_scan_tokens(lexer);
    Parser* parser = parser_create(lexer->tokens, lexer->token_count);
    ASTNode* program = parser_parse(parser);

    interpreter_run(interp, program);

    // Cleanup
    interpreter_free(interp);
    ast_free(program);
    parser_free(parser);
    lexer_free(lexer);
    free(entry_point);

    printf("\n[DONE] Bundle execution complete\n");
    return 0;
}

/* ============================================================================
 * REPL
 * ============================================================================ */

static void run_repl(void) {
    printf("\n");
    printf("======================================================================\n");
    printf("     SOMNIA NATIVE REPL v%s                                     \n", SOMNIA_VERSION);
    printf("     Type 'exit' to quit                                          \n");
    printf("======================================================================\n");
    printf("\n");
    
    Interpreter* interp = interpreter_create();
    char line[4096];
    
    while (1) {
        printf("somnia> ");
        fflush(stdout);
        
        if (fgets(line, sizeof(line), stdin) == NULL) {
            printf("\n");
            break;
        }
        
        // Remove newline
        size_t len = strlen(line);
        if (len > 0 && line[len-1] == '\n') {
            line[len-1] = '\0';
        }
        
        // Check for exit
        if (strcmp(line, "exit") == 0 || strcmp(line, "quit") == 0) {
            break;
        }
        
        if (strlen(line) == 0) continue;
        
        // Lexer
        Lexer* lexer = lexer_create(line);
        lexer_scan_tokens(lexer);
        
        // Parser
        Parser* parser = parser_create(lexer->tokens, lexer->token_count);
        ASTNode* program = parser_parse(parser);
        
        // Execute
        Value result = interpreter_run(interp, program);
        
        // Print result if not null
        if (result.type != VAL_NULL) {
            printf("=> ");
            value_print(result);
            printf("\n");
        }
        
        // Cleanup
        ast_free(program);
        parser_free(parser);
        lexer_free(lexer);
    }
    
    interpreter_free(interp);
    printf("\nGoodbye!\n");
}

/* ============================================================================
 * MAIN
 * ============================================================================ */

static void print_usage(const char* prog) {
    printf("Usage: %s <command> [options]\n", prog);
    printf("\n");
    printf("Commands:\n");
    printf("  run <file.somnia>   Execute a Somnia file\n");
    printf("  repl                Start interactive REPL\n");
    printf("  version             Show version info\n");
    printf("  help                Show this help\n");
}

int main(int argc, char* argv[]) {
    signal(SIGSEGV, handle_signal);
    signal(SIGABRT, handle_signal);
    signal(SIGINT, handle_signal);

    printf("[BOOT] Somnia Runtime Starting...\n");
    fflush(stdout);
    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }
    
    const char* command = argv[1];
    
    if (strcmp(command, "run") == 0) {
        if (argc < 3) {
            fprintf(stderr, "Usage: %s run <file.somnia|.som>\n", argv[0]);
            return 1;
        }
        if (strstr(argv[2], ".som") != NULL && strstr(argv[2], ".somnia") == NULL) {
            return run_bundle(argv[2]);
        }
        return run_file(argv[2]);
    }
    
    if (strcmp(command, "repl") == 0) {
        run_repl();
        return 0;
    }
    
    if (strcmp(command, "version") == 0) {
        printf("Somnia Native v%s\n", SOMNIA_VERSION);
        printf("Pure C Runtime\n");
        return 0;
    }
    
    if (strcmp(command, "help") == 0) {
        print_usage(argv[0]);
        return 0;
    }
    
    // If command looks like a file, run it directly
    if (strstr(command, ".som") != NULL) {
        if (strstr(command, ".somnia") != NULL) return run_file(command);
        return run_bundle(command);
    }
    
    fprintf(stderr, "Unknown command: %s\n", command);
    print_usage(argv[0]);
    return 1;
}
