# Somnia Native Runtime - Makefile

CC = gcc
CFLAGS = -Wall -Wextra -O2 -std=c99
LDFLAGS = -lm

SRC_DIR = src
INC_DIR = include
BUILD_DIR = build

SOURCES = $(wildcard $(SRC_DIR)/*.c)
OBJECTS = $(patsubst $(SRC_DIR)/%.c,$(BUILD_DIR)/%.o,$(SOURCES))

TARGET = somnia

.PHONY: all clean run test

all: $(BUILD_DIR) $(TARGET)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(TARGET): $(OBJECTS)
	$(CC) $(OBJECTS) -o $(TARGET) $(LDFLAGS)
	@echo ""
	@echo "Build complete: ./$(TARGET)"
	@echo ""

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c $(INC_DIR)/somnia.h
	$(CC) $(CFLAGS) -I$(INC_DIR) -c $< -o $@

clean:
	rm -rf $(BUILD_DIR) $(TARGET)

run: $(TARGET)
	./$(TARGET) run examples/hello.somnia

test: $(TARGET)
	./$(TARGET) run examples/test.somnia

repl: $(TARGET)
	./$(TARGET) repl

# Debug build
debug: CFLAGS += -g -DDEBUG
debug: clean all

# Windows build (using mingw)
windows: CC = x86_64-w64-mingw32-gcc
windows: TARGET = somnia.exe
windows: clean all
