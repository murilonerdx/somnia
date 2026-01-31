# Somnia JSON Extension

Pure Somnia JSON parsing and serialization library.

## Installation

Add to your `somnia.deps`:

```somnia
deps my-project {
    require "somnia-json" version "1.0.0"
}
```

## Usage

### Basic JSON Parsing

```somnia
import { json_parse, json_stringify } from "somnia-json"

# Parse JSON string
var data = json_parse('{"name": "John", "age": 30}')

# Access values
println(data.get("name").as_string())  # "John"
println(data.get("age").as_number())   # 30

# Serialize back to JSON
var json_str = json_stringify(data)
```

### Object Mapper (Jackson-style)

```somnia
import { create_object_mapper, serialize, deserialize } from "somnia-json/serialization"

# Create mapper with config
var mapper = create_object_mapper()

# Serialize any value
var user = { name: "Alice", email: "alice@example.com" }
var json = mapper.write_value_as_string(user)

# Deserialize JSON
var parsed = mapper.read_value(json)
```

### Pretty Printing

```somnia
import { json_stringify_pretty, serialize_pretty } from "somnia-json"

var data = { users: [{ name: "A" }, { name: "B" }] }
var pretty = serialize_pretty(data)
println(pretty)
# {
#   "users": [
#     {"name": "A"},
#     {"name": "B"}
#   ]
# }
```

### Snake Case Conversion

```somnia
import { create_object_mapper_with_config, default_serialize_config } from "somnia-json/serialization"

var config = default_serialize_config()
config.snake_case = true

var mapper = create_object_mapper_with_config(config)

var user = { firstName: "John", lastName: "Doe" }
var json = mapper.write_value_as_string(user)
# {"first_name": "John", "last_name": "Doe"}
```

## API Reference

### Core Functions

| Function | Description |
|----------|-------------|
| `json_parse(str)` | Parse JSON string to JsonValue |
| `json_stringify(val)` | Serialize JsonValue to string |
| `json_stringify_pretty(val)` | Serialize with formatting |
| `to_json(any)` | Convert any value to JsonValue |
| `from_json(val)` | Convert JsonValue to native |

### JsonValue Factory

| Function | Description |
|----------|-------------|
| `json_string(str)` | Create string value |
| `json_number(num)` | Create number value |
| `json_bool(bool)` | Create boolean value |
| `json_null()` | Create null value |
| `json_array(list)` | Create array value |
| `json_object(map)` | Create object value |

### Serialization

| Function | Description |
|----------|-------------|
| `serialize(any)` | Serialize any value to JSON |
| `serialize_pretty(any)` | Serialize with pretty print |
| `deserialize(str)` | Deserialize JSON to native |
| `create_object_mapper()` | Create ObjectMapper |
