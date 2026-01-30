# Somnia: The Metaprogrammable Logic Language

![Somnia Logo](https://via.placeholder.com/150?text=Somnia)

Somnia is a domain-specific language (DSL) and engine designed to **eliminate backend boilerplate** through pure metaprogramming. It allows you to define your application's architecture (Entities, Architecture Patterns, API Routes) as dynamic templates, creating a codebase that is 100% declarative and 0% repetitive.

## 🚀 The Problem
Modern backend development suffers from the "Boilerplate Plateau":
- **Repetition**: To add one "Product" entity, you write a generic Entity class, a generic Repository interface, a generic DTO, and a generic Controller.
- **Rigidity**: "Generic" code in Java/Kotlin often requires complex reflection, annotation processing, or code generation tools that are hard to debug.
- **Maintenance**: Changing a pattern (e.g., adding Audit Logging to all optimized repos) requires refactoring dozens of files.

## 💡 The Solution: Pure Somnia Modules
Somnia introduces a **Pure Module System** where patterns are defined in the language itself, not hardcoded in the engine.

### Key Features
- **Patterns as Macros**: Define a `standard_crud` pattern once, and apply it to any entity. The engine expands it into actual Actions, Repositories, and Routes at runtime.
- **Zero-Code Implementation**: You can build full-featured APIs without writing a single line of host language (Java/Kotlin) code for the business logic.
- **Dynamic Schema**: Tables and database structures are automatically managed based on your Somnia definitions.

## 📦 Usage

### Importing from GitHub (JitPack)
You can use Somnia libraries directly from this repository using JitPack.

**Step 1. Add the JitPack repository to your build file**
```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

**Step 2. Add the dependency**
```kotlin
dependencies {
    implementation("com.github.murilonerdx:somnia:v1.0.0")
}
```

### Writing Your First Somnia App

**1. Create `app.somni`:**

```somnia
app "task-manager"

// Import the Standard Library (included in the repo)
import "std/crud.somni"

act {
  // Define your domain entity
  entity Task table "tasks" {
    id: UUID
    title: String
    is_done: Boolean
  }

  // Define input DTO
  dto CreateTaskRequest {
    title: String
    is_done: Boolean
  }

  // Apply the CRUD Pattern
  // This single line generates:
  // - TaskRepository (Database)
  // - Tasks.create (Action)
  // - Tasks.list (Action)
  // - POST /tasks (HTTP Route)
  // - GET /tasks (HTTP Route)
  pattern StandardCrud(Task)
}
```

**2. Run with Somnia Engine:**
The engine parses your script, resolves the `StandardCrud` macro, and spins up the Spring Boot application with all dynamic endpoints active.

## 🛠 Building from Source

```bash
git clone https://github.com/murilonerdx/somnia.git
cd somnia
./gradlew build
```

## 📄 Documentation
For a deep dive into creating your own Patterns and Modules, see the [Manual](MANUAL.md).

## 🤝 Contributing
Issues and Pull Requests are welcome! Please ensure you follow the code style and add tests for new patterns.

## 📜 License
MIT
