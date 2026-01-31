# Somnia Native Runtime

Runtime nativo do Somnia escrito em C puro.

## Build

```bash
# Linux/Docker
make

# ou diretamente
gcc -O2 -o somnia src/*.c -lm
```

## Uso

```bash
./somnia run examples/hello.somnia
./somnia repl
```

## Estrutura

```
somnia-native/
├── src/
│   ├── main.c          # Entry point
│   ├── lexer.c         # Tokenizer
│   ├── parser.c        # AST builder
│   ├── interpreter.c   # Executor
│   ├── value.c         # Runtime values
│   ├── env.c           # Environment/scope
│   └── stdlib.c        # Built-in functions
├── include/
│   └── somnia.h        # Headers
├── Makefile
└── Dockerfile
```
