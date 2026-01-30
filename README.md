# Somnia: The Metaprogrammable Logic Language

> **"Code that writes code, so you don't have to."**

![Somnia Banner](https://via.placeholder.com/800x200?text=Somnia+Language)

## 📖 The Origin Story: Why Somnia?

### The "Boilerplate Plateau"
We started this project with a simple observation: **Backend development has hit a wall of repetition.** 
To build a simple feature like "Create User", a developer typically writes 5-7 files in Java/Kotlin:
1. `User.java` (Entity)
2. `UserRepository.java` (Interface)
3. `UserDTO.java` (Data Transfer Object)
4. `UserController.java` (HTTP Endpoint)
5. `UserService.java` (Business Logic)
6. `UserMapper.java` (Conversion logic)

This is what we call the **"Vertical Slice Problem"**. You are writing the same "shape" of code over and over again, just changing the nouns.

### The Evolution of Somnia
1. **Phase 1: The Interpreter**: We built a simple parser that could read a text file and print "Hello".
2. **Phase 2: The Hybrid Adapter**: We connected Somnia to Spring Boot. You could define `action "create_user"`, but the logic was still hardcoded in Kotlin (`StandardCrudAdapter.kt`). This was efficient but not flexible—adding a new pattern meant recompiling the engine.
3. **Phase 3: The Pure Module System (Current)**: We realized the language itself needed to be extensible. We introduced **Macros (`pattern`)**. Now, the logic isn't in the engine; it's in the script. You can define your own "CRUD" or "Audit" patterns in Somnia and import them.

---

## 🧠 Core Concepts & Terminology

Here is the deep-dive into the language keywords and what they actually represent.

| Keyword | Concept | Technical Reality |
| :--- | :--- | :--- |
| **`app`** | **Identity** | The entry point. Defines the Spring Application Context, base package, and configuration roots. |
| **`act`** | **The Stage** | The "Activity Block". This is where behavioral logic lives. It maps to the *Service Layer* in traditional architectures. |
| **`entity`** | **Data Model** | A domain object. In the engine, this translates dynamically to a JPA `@Entity` with bytecode generation or a JDBC Table Schema. |
| **`pattern`** | **The Macro** | A template function. It accepts AST nodes (like an Entity name) and **injects** new code into the program. It is a "Compiler Plugin" written in Somnia. |
| **`bind`** | **The Glue** | Connects an abstract intent ("I want to save") to a concrete implementation ("Call JDBC"). It supports `sql`, `repo`, `tx` (transaction), and more. |
| **`import`** | **The Link** | Loads external `.somni` files. The `ModuleLoader` parses them and merges their AST into your main program, resolving relative paths. |

---

## ⚙️ How It Works: The "T-Architecture"

Somnia uses a **Metadata-Driven Architecture**. usage
Instead of compiling to `.class` files immediately, it builds an execution plan.

1. **Parsing**: Use `Lexer` and `Parser` to convert text into an Abstract Syntax Tree (AST).
2. **Macro Expansion**: The `PatternEngine` looks for `pattern Usage(...)`. It finds the definition, clones the template, substitutes variables (e.g., replaces `${E}` with `Product`), and injects the result back into the AST.
3. **Bootstrapping**:
   - **Database**: The engine scans `entity` nodes and executes `CREATE TABLE IF NOT EXISTS` via JDBC.
   - **Routes**: The engine registers `RequestMappingHandlerMapping` endpoints dynamically for every `http` block.
   - **Logic**: When a request hits `POST /products`, the engine looks up the `action`, creates a context, and executes the `bind` steps sequentially.

---

## 🛠 Real-World Example: "Todo List"

### 1. The Definitions (`std/crud.somni`)
This is the "Library" code. You usually write this once or download it.

```somnia
// This is a TEMPLATE. "E" is a variable.
pattern StandardCrud(E) {
    // 1. Define how to save 'E'
    repository "${E}Repo" : jpa <E, UUID> {
        fun save(item: E): E
    }

    // 2. Define the Business Logic
    action "${E}.create"(req: "Create${E}Request") returns E
        // "bind" connects this action to the repo defined above
        bind repo "${E}Repo".save(req)

    // 3. Define the HTTP Interface
    http {
        on POST "/${E}s" as intent("${E}.create")
    }
}
```

### 2. The Application (`main.somni`)
This is your actual app code. It is incredibly clean.

```somnia
app "todo-app"

import "std/crud.somni" // Load the library

act {
    // Define Data
    entity Todo {
        id: UUID
        title: String
        done: Boolean
    }

    // Define Input
    dto CreateTodoRequest {
        title: String
        done: Boolean
    }

    // THE MAGIC: Apply the pattern
    pattern StandardCrud(Todo)
}
```

### 3. Execution
When you run this:
1. `StandardCrud(Todo)` expands.
2. `repository "TodoRepo"` is created.
3. `action "Todo.create"` is generated.
4. `http POST "/Todos"` is registered.
5. **Result**: You have a working API.

---

## 🚀 How to Execute

### Option A: Using the CLI (Recommended)
1. Clone this repo.
2. Build the project:
   ```bash
   ./gradlew build
   ```
3. Run the "CRUD App" example:
   ```bash
   java -jar somnia-crud-app/build/libs/somnia-crud-app-1.0.0.jar
   ```

### Option B: Importing via JitPack
Add to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.murilonerdx:somnia:v1.0.0")
}
```

Then create a Spring Boot app and add a `.somni` file to your `src/main/resources/somnia/` folder. The `SomniaAutoConfiguration` will detect it and boot the engine.

---

## 🔮 The Future
We are moving towards:
- **LSP Support**: VS Code plugin for syntax highlighting and autocomplete.
- **Visual Editor**: A GUI to drag-and-drop patterns.
- **Native Compilation**: Compiling Somnia directly to GraalVM Native Image.

---

**Project Maintainer**: MuriloNerdx
**License**: MIT
