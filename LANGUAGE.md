# Somnia Language Reference

**The Cognitive Programming Language**

*"Code as the mind thinks"*

---

## 🧠 Philosophy

Somnia is a programming language designed around the psychological architecture of the human mind. Just as the mind has different layers of consciousness, Somnia programs are structured into three primary blocks:

| Block | Psychological Analog | Purpose |
|-------|---------------------|---------|
| **ID** | Unconscious (Id) | Memory, instincts, primal data |
| **EGO** | Subconscious (Ego) | Logic, processing, mediation |
| **ACT** | Conscious (Superego) | Actions, behavior, external interaction |

---

## 📖 Program Structure

Every Somnia program consists of three blocks:

```somnia
ID {
    # The unconscious: constants, variables, data structures
    const pi = 3.14159
    var counter = 0
}

EGO {
    # The subconscious: functions, logic, transformations
    fun calculate(x) {
        return x * pi
    }
}

ACT {
    # The conscious: actions, side effects, behavior
    action main {
        println(calculate(10))
    }
}
```

---

## 🔤 Data Types

| Type | Examples | Description |
|------|----------|-------------|
| `Int` | `42`, `-17` | Integer numbers |
| `Double` | `3.14`, `-0.5` | Floating-point numbers |
| `String` | `"hello"`, `'world'` | Text strings |
| `Boolean` | `true`, `false` | Logical values |
| `Null` | `null` | Absence of value |
| `List` | `[1, 2, 3]` | Ordered collections |
| `Map` | `{key: value}` | Key-value pairs |

---

## 💾 Variables & Constants

```somnia
ID {
    # Constants are immutable (like deep beliefs)
    const VERSION = "1.0.0"
    const MAX_SIZE = 100
    
    # Variables are mutable (like short-term memory)
    var count = 0
    var name = "Somnia"
    var items = []
}
```

---

## ⚡ Operators

### Arithmetic
```somnia
a + b    # Addition
a - b    # Subtraction
a * b    # Multiplication
a / b    # Division
a % b    # Modulo
-a       # Negation
```

### Comparison
```somnia
a == b   # Equal
a != b   # Not equal
a < b    # Less than
a <= b   # Less or equal
a > b    # Greater than
a >= b   # Greater or equal
```

### Logical
```somnia
a and b  # Logical AND
a or b   # Logical OR
not a    # Logical NOT
```

---

## 🎯 Functions

Functions live in the **EGO** block:

```somnia
EGO {
    # Simple function
    fun greet() {
        println("Hello!")
    }
    
    # Function with parameters
    fun add(a, b) {
        return a + b
    }
    
    # Function with default values
    fun multiply(x, factor = 2) {
        return x * factor
    }
}
```

---

## 🔄 Control Flow

### Conditionals
```somnia
if condition {
    # then branch
} else {
    # else branch
}
```

### Loops (future)
```somnia
for item in items {
    println(item)
}

while condition {
    # loop body
}
```

---

## 📦 Standard Library

### std/io - Input/Output
```somnia
print(args...)           # Print without newline
println(args...)         # Print with newline
readLine()               # Read line from stdin
readFile(path)           # Read file contents
writeFile(path, content) # Write to file
appendFile(path, content)# Append to file
fileExists(path)         # Check if file exists
```

### std/http - HTTP Client/Server
```somnia
httpGet(url)             # GET request
httpPost(url, body)      # POST request
httpPut(url, body)       # PUT request
httpDelete(url)          # DELETE request
httpServe(port, handler) # Start HTTP server
```

### std/math - Mathematics
```somnia
PI()                     # PI constant
E()                      # Euler's number
abs(n)                   # Absolute value
sqrt(n)                  # Square root
pow(base, exp)           # Power
floor(n), ceil(n)        # Rounding
sin(n), cos(n)           # Trigonometry
random()                 # Random 0.0-1.0
randomInt(min, max)      # Random integer
max(args...), min(args...)# Min/Max
```

### std/string - String Manipulation
```somnia
strlen(s)                # String length
upper(s), lower(s)       # Case conversion
trim(s)                  # Remove whitespace
split(s, delim)          # Split to list
join(list, delim)        # Join list to string
replace(s, old, new)     # Replace substring
contains(s, substr)      # Check contains
startsWith(s, prefix)    # Check prefix
endsWith(s, suffix)      # Check suffix
substring(s, start, end) # Extract substring
format(template, args...)# String formatting
```

### std/list - Collections
```somnia
size(list)               # List size
first(list), last(list)  # First/last element
get(list, index)         # Get by index
push(list, item)         # Add item
concat(list1, list2)     # Merge lists
reverse(list)            # Reverse order
range(start, end)        # Generate range
includes(list, item)     # Check contains
isEmpty(list)            # Check empty
sum(list), avg(list)     # Aggregations
```

### std/time - Date/Time
```somnia
now()                    # Current timestamp
nanos()                  # Nanosecond timer
sleep(ms)                # Sleep milliseconds
formatDate(timestamp)    # Format date
year(), month(), day()   # Current date parts
hour(), minute()         # Current time parts
elapsed(startNanos)      # Elapsed time in ms
```

### std/runtime - VM Introspection
```somnia
version()                # Somnia version
osName()                 # Operating system
javaVersion()            # JVM version
processors()             # CPU count
memoryUsed()             # Used memory
memoryTotal()            # Total memory
memoryFree()             # Free memory
memoryMax()              # Max memory
gcRun()                  # Force GC
env(name)                # Environment variable
exit(code)               # Exit program
stackTrace()             # Get stack trace
```

---

## 🚀 Running Programs

```bash
# Compile and run in one step
java -jar somnia.jar exec program.somni

# Compile to bytecode
java -jar somnia.jar compile program.somni -o program.sbc

# Run bytecode
java -jar somnia.jar run program.sbc

# Show version
java -jar somnia.jar version
```

---

## 📁 Project Structure

```
my-project/
├── soma.toml          # Project configuration
├── src/
│   ├── main.somni     # Entry point
│   └── lib/
│       └── utils.somni
├── test/
│   └── test_main.somni
└── out/
    └── main.sbc       # Compiled bytecode
```

### soma.toml
```toml
[project]
name = "my-project"
version = "1.0.0"
entry = "src/main.somni"

[dependencies]
"std/http" = "1.0"
```

---

## 🎨 Style Guide

1. **Block Organization**: Always order blocks as ID → EGO → ACT
2. **Naming**: Use `camelCase` for functions, `UPPER_CASE` for constants
3. **Comments**: Use `#` for single-line comments
4. **Indentation**: Use 4 spaces
5. **Constants First**: Define constants before variables in ID block

---

## 🔮 Future Features

- [ ] Pattern matching
- [ ] Async/await
- [ ] Modules and imports
- [ ] Macros
- [ ] REPL
- [ ] Package manager (soma)
- [ ] Native compilation
- [ ] JIT optimization  

---

**Somnia** - *The Language That Dreams*
