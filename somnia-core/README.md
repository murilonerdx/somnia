# Somnia Core Library

Self-hosted core library for the Somnia cognitive agent runtime.

## Overview

This is the **pure Somnia** implementation of the agent runtime, featuring:

- **ID Engine** (The Unconscious) - Rule-based proposal generation
- **EGO** (The Subconscious) - Policy filtering and selection
- **ACT** (The Conscious) - Action execution with timeout/retry
- **FFI Bridge** - Integration with JVM/Native code

## Installation

```bash
somnia add somnia-core@1.0.0
```

## Quick Start

```somnia
import "somnia-core"

# Create a complete agent
var agent = RUNTIME()
    .with_id(ID()
        .drive("efficiency", 0.8)
        .add(rule("greet")
            .when_intent("greet")
            .propose("log")
            .with_args({ "message": value_string("Hello!") })
            .weight(0.9)
            .build())
        .build())
    .with_ego(EGO()
        .forbid_when(cond_fact("maintenance"), "System in maintenance")
        .budget("log", 100, 60)
        .select_top(1)
        .build())
    .with_act(ACT()
        .with_builtins()
        .build())
    .build()

# Run a cycle
var result = agent.run_cycle(create_intent("greet", {}))
```

## Modules

| Module | Description |
|--------|-------------|
| `types.somnia` | Core value types (Value, Intent, Fact, Drive, Affect) |
| `proposal.somnia` | Proposal and selection types |
| `condition.somnia` | Condition system for rule matching |
| `context.somnia` | Execution context for cycles |
| `rule.somnia` | Rule definitions with fluent DSL |
| `id_engine.somnia` | ID (Unconscious) - rule engine |
| `ego.somnia` | EGO (Subconscious) - policy layer |
| `act.somnia` | ACT (Conscious) - action execution |
| `runtime.somnia` | Full runtime with cycle execution |
| `ffi.somnia` | FFI bridge for external integration |
| `natives.somnia` | Native function declarations |

## Testing

```bash
somnia test somnia-core/test/test_runner.somnia
```

## API Reference

### Types

```somnia
# Value - Universal value wrapper
Value { type: string, data: any }

# Intent - External trigger
Intent { name: string, args: map }

# Fact - World state
Fact { key: string, value: Value }

# Drive - Motivation
Drive { name: string, intensity: number }

# Affect - Emotional state
Affect { name: string, valence: number }
```

### DSL

```somnia
# ID DSL
ID()
    .drive("efficiency", 0.8)
    .affect("calm", 0.5)
    .add(rule("r1").when_intent("x").propose("y").build())
    .build()

# EGO DSL
EGO()
    .forbid_when(condition, "reason")
    .forbid_action("action", condition, "reason")
    .budget("action", 100, 60)
    .select_top(3)
    .min_weight(0.1)
    .build()

# ACT DSL
ACT()
    .with_builtins()
    .action("custom", handler)
    .build()

# Runtime DSL
RUNTIME()
    .with_id(engine)
    .with_ego(ego)
    .with_act(act)
    .seed(12345)
    .with_fact("key", value)
    .build()
```

## License

MIT
