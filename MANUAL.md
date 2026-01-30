# Somnia Language - The Pure Module System Guide

**Version**: 1.0.0
**Date**: January 2026

---

## 1. Introduction

Welcome to the **Somnia Pure Module System**. This release represents a paradigm shift from hardcoded Kotlin logic to a unified, declarative architecture. 

In this system, you use **Somnia (`.somni`)** files to define everything:
- **Data Structures**: Entities, DTOs.
- **Logic**: Actions, Workflows (Transactions, API calls).
- **Patterns**: Reusable, parameterized macros (Templates).
- **Architecture**: Http Routes, Listeners.

The engine handles the "dirty work" (Database schemas, API endpoints, serialization) automatically.

### Why "Pure"?
Previously, adding a feature like "CRUD" required writing a Kotlin class (`StandardCrudAdapter`) and registering it in the engine. 
**Now, you can write that same logic in Somnia itself**, package it in a file, and import it anywhere.

---

## 2. Getting Started

### Prerequisites
- JDK 21+
- Gradle 8.5+
- Somnia CLI (or Boot Starter)

### Project Structure
A typical Somnia project looks like this:

```text
my-app/
├── build.gradle.kts
├── src/main/resources/
│   └── somnia/
│       ├── main.somni        # Entry point
│       └── std/              # Standard Library
│           ├── crud.somni    # Reusable CRUD Pattern
│           └── auth.somni    # Reusable Auth Pattern
```

---

## 3. The Core Concepts

### 3.1. Imports
Imports allow you to load other `.somni` files. The engine resolves them relative to the current file or checks the classpath.

**Syntax:**
```somnia
import "std/crud.somni"
import "modules/users.somni"
```

### 3.2. Patterns (Macros)
A `pattern` is a template. It takes parameters (like an Entity name) and generates code when "used".

**Definition (The "Library" side):**
```somnia
// Define a pattern named 'StandardCrud' that accepts an Entity E
pattern StandardCrud(E) {
    // Inside here, use ${E} to inject the parameter
    repository "${E}Repository" : jpa <E, UUID> { 
        fun save(item: E): E 
    }
    
    action "${E}s.create"(req: "Create${E}Request") returns E
        bind repo "${E}Repository".save(req)
}
```

**Usage (The "User" side):**
```somnia
// Define your specific Entity
entity Product { ... }
dto CreateProductRequest { ... }

// Apply the pattern
pattern StandardCrud(Product)
```

**What happens?**
The engine expands `StandardCrud(Product)` into:
1. `ProductRepository`
2. `Products.create` Action

---

## 4. Step-by-Step Implementation Guide

### Scenario: Building a "Todo List" API from Scratch

#### Step 1: Create the Entry Point
Create `src/main/resources/somnia/todo_app.somni`.

```somnia
app "todo-app" {
    package "com.example.todo"
    spring boot "3.2.0"
}

// We will create this library file in Step 2, so import it now.
import "std/crud.somni"

act {
    // 1. Define your Data Model
    entity Todo table "todos" {
        id: UUID
        title: String
        completed: Boolean
    }

    // 2. Define data transfer object (Input)
    dto CreateTodoRequest {
        title: String
        completed: Boolean
    }

    // 3. Use the Standard Library Pattern
    pattern StandardCrud(Todo)
}
```

#### Step 2: Create the Reusable Library
Create `src/main/resources/somnia/std/crud.somni`.

```somnia
package somnia.std

// Defines a generic CRUD interface
pattern StandardCrud(Entity) {
    
    // -- 1. Persistence Layer --
    // Creates a named repository interface dynamically
    repository "${Entity}Repository" : jpa <Entity, UUID> {
        fun save(item: Entity): Entity
        fun findAll(): List
        fun deleteById(id: UUID): Void
    }

    // -- 2. Business Logic Layer --
    
    // Create Action
    action "${Entity}s.create"(req: "Create${Entity}Request") permission("db.write") returns Entity
        bind tx required {
             bind repo "${Entity}Repository".save(req)
        }

    // List Action
    action "${Entity}s.list"() permission("db.read") returns Json
        bind sql "select * from ${Entity}s"

    // -- 3. Presentation Layer (HTTP) --
    
    http {
        on POST "/${Entity}s" as intent("${Entity}s.create")
            body "Create${Entity}Request"
            
        on GET "/${Entity}s" as intent("${Entity}s.list")
    }

    // -- 4. Connect Render Logic --
    render "${Entity}s.create" using "${Entity}s.create"
    render "${Entity}s.list" using "${Entity}s.list"
}
```

#### Step 3: Run It
Run your Main class (e.g. `SomniaApp`).

The engine will:
1. Parse `todo_app.somni`.
2. See the `import`. Load `std/crud.somni`.
3. Parse `entity Todo`.
4. Encounter `pattern StandardCrud(Todo)`.
5. Look up the definition in the imported library.
6. **Expand (Substitute)** `${Entity}` with `Todo` throughout the template.
7. Generate `TodoRepository`, `Todos.create`, `/Todos` route.
8. Boot Spring with these dynamic beans.

**You now have a working API at `POST /Todos` without writing Java/Kotlin code.**

---

## 5. Advanced Examples

### 5.1. Customizing the Repository Query
You can pass SQL fragments as parameters too!

**Definition:**
```somnia
pattern AdvancedSearch(Entity, TableName, FilterCol) {
    action "${Entity}.search"(val: String) returns List
       bind sql "select * from ${TableName} where ${FilterCol} = ?" (val)
}
```

**Usage:**
```somnia
pattern AdvancedSearch(User, "users", "email")
```

### 5.2. Composition (The "T-Architecture")
You can stack patterns.

```somnia
import "std/crud.somni"
import "domain/audit.somni"

act {
    entity Order { ... }
    
    // 1. Add CRUD capabilities
    pattern StandardCrud(Order)
    
    // 2. Add Audit logging to the same entity
    pattern AuditLog(Order)
}
```

---

## 6. Troubleshooting

### "Pattern definition not found"
- Check your `import` path. It is relative to the file.
- Verify the pattern name matches exactly.

### "Expects N args but got M"
- The pattern definition `pattern Name(A, B)` requires exactly 2 arguments.
- Check if you forgot an argument in your usage `pattern Name(A)`.

### Properties not found in generated code
- Ensure your DTO names in the `usage` file implementation match what the `pattern` expects.
- Ex: If pattern expects `Create${E}Request`, you MUST define `dto CreateProductRequest` if E=Product.

---

**Developed by the Somnia Team**
