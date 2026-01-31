# Somnia CLI

Command-line interface for the Somnia programming language.

## Installation

```batch
# Run the installer
cd somnia-cli
install.bat
```

The installer will:
1. Copy files to `%USERPROFILE%\.somnia`
2. Add `somnia` to your PATH
3. Set `SOMNIA_HOME` environment variable

After installation, restart your terminal/command prompt.

## Commands

| Command | Description |
|---------|-------------|
| `somnia run <file>` | Run a Somnia file |
| `somnia repl` | Start interactive REPL |
| `somnia test [path]` | Run tests |
| `somnia init [name]` | Create new project |
| `somnia deps install` | Install dependencies |
| `somnia build` | Build project |
| `somnia version` | Show version |
| `somnia help` | Show help |

## Quick Start

```batch
# Create a new project
somnia init my-project
cd my-project

# Run the main file
somnia run src/main.somnia

# Run tests
somnia test
```

## Project Structure

```
my-project/
├── somnia.config      # Application configuration
├── somnia.deps        # Dependencies
├── src/
│   └── main.somnia    # Entry point
├── lib/               # Local libraries
└── test/              # Tests
```

## Configuration

### somnia.config
```somnia
config App {
    name = "my-app"
    version = "1.0.0"
    main = "src/main.somnia"
    
    env {
        PORT = 8080
        DEBUG = true
    }
}
```

### somnia.deps
```somnia
deps my-project {
    version "1.0.0"
    
    require "somnia-core" version "1.0.0"
    require "somnia-json" version "1.0.0"
    
    dev {
        require "somnia-test" version "1.0.0"
    }
}
```

## Extensions

Available extensions:
- **somnia-core** - Core library (types, runtime, FFI)
- **somnia-json** - JSON parsing and serialization
