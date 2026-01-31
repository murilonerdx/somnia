package somnia.bootstrap

import java.time.Instant
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil

/**
 * Somnia Value - Universal value type for the VM
 */
sealed class SomniaValue {
    abstract val type: String
    abstract fun isTruthy(): Boolean
    abstract fun toSomniaString(): String
    
    data class SNull(val unit: Unit = Unit) : SomniaValue() {
        override val type = "null"
        override fun isTruthy() = false
        override fun toSomniaString() = "null"
    }
    
    data class SBool(val value: Boolean) : SomniaValue() {
        override val type = "bool"
        override fun isTruthy() = value
        override fun toSomniaString() = value.toString()
    }
    
    data class SNumber(val value: Double) : SomniaValue() {
        override val type = "number"
        override fun isTruthy() = value != 0.0
        override fun toSomniaString() = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
    
    data class SString(val value: String) : SomniaValue() {
        override val type = "string"
        override fun isTruthy() = value.isNotEmpty()
        override fun toSomniaString() = value
    }
    
    data class SList(val items: MutableList<SomniaValue>) : SomniaValue() {
        override val type = "list"
        override fun isTruthy() = items.isNotEmpty()
        override fun toSomniaString() = items.joinToString(", ", "[", "]") { it.toSomniaString() }
    }
    
    data class SMap(val entries: MutableMap<String, SomniaValue>) : SomniaValue() {
        override val type = "map"
        override fun isTruthy() = entries.isNotEmpty()
        override fun toSomniaString() = entries.entries.joinToString(", ", "{", "}") { 
            "\"${it.key}\": ${it.value.toSomniaString()}" 
        }
    }
    
    data class SFunction(
        val name: String,
        val params: List<String>,
        val body: Any,  // AST node
        val closure: Environment
    ) : SomniaValue() {
        override val type = "function"
        override fun isTruthy() = true
        override fun toSomniaString() = "<fn $name>"
    }
    
    data class SClass(
        val name: String,
        val fields: Map<String, SomniaValue>,
        val methods: Map<String, SFunction>
    ) : SomniaValue() {
        override val type = "class"
        override fun isTruthy() = true
        override fun toSomniaString() = "<class $name>"
    }
    
    data class SInstance(
        val className: String,
        val fields: MutableMap<String, SomniaValue>
    ) : SomniaValue() {
        override val type = "object"
        override fun isTruthy() = true
        override fun toSomniaString() = "$className { ... }"
    }
    
    data class SNative(
        val name: String,
        val handler: (List<SomniaValue>) -> SomniaValue
    ) : SomniaValue() {
        override val type = "native"
        override fun isTruthy() = true
        override fun toSomniaString() = "<native $name>"
    }
    
    companion object {
        val NULL = SNull()
        fun bool(v: Boolean) = SBool(v)
        fun num(v: Number) = SNumber(v.toDouble())
        fun str(v: String) = SString(v)
        fun list(vararg items: SomniaValue) = SList(items.toMutableList())
        fun map(vararg pairs: Pair<String, SomniaValue>) = SMap(pairs.toMap().toMutableMap())
    }
}

/**
 * Native functions - Core implementations
 */
object Natives {
    fun register(env: Environment) {
        // Time
        env.define("native_time_ms", SomniaValue.SNative("native_time_ms") { 
            SomniaValue.num(System.currentTimeMillis())
        })
        
        env.define("native_sleep", SomniaValue.SNative("native_sleep") { args ->
            val ms = (args.firstOrNull() as? SomniaValue.SNumber)?.value?.toLong() ?: 0L
            Thread.sleep(ms)
            SomniaValue.NULL
        })
        
        // Logging
        env.define("native_log", SomniaValue.SNative("native_log") { args ->
            val level = (args.getOrNull(0) as? SomniaValue.SString)?.value ?: "INFO"
            val message = (args.getOrNull(1) as? SomniaValue.SString)?.value ?: ""
            println("[$level] $message")
            SomniaValue.NULL
        })
        
        // Type introspection
        env.define("native_type", SomniaValue.SNative("native_type") { args ->
            SomniaValue.str(args.firstOrNull()?.type ?: "null")
        })
        
        env.define("native_to_string", SomniaValue.SNative("native_to_string") { args ->
            SomniaValue.str(args.firstOrNull()?.toSomniaString() ?: "null")
        })
        
        // Collections
        env.define("native_keys", SomniaValue.SNative("native_keys") { args ->
            when (val map = args.firstOrNull()) {
                is SomniaValue.SMap -> SomniaValue.SList(
                    map.entries.keys.map { SomniaValue.str(it) }.toMutableList()
                )
                else -> SomniaValue.list()
            }
        })
        
        env.define("native_sort", SomniaValue.SNative("native_sort") { args ->
            // Simple sort - in real impl would call the comparator function
            val list = (args.firstOrNull() as? SomniaValue.SList)?.items?.toMutableList() ?: mutableListOf()
            SomniaValue.SList(list)
        })
        
        env.define("native_compare", SomniaValue.SNative("native_compare") { args ->
            val a = (args.getOrNull(0) as? SomniaValue.SString)?.value ?: ""
            val b = (args.getOrNull(1) as? SomniaValue.SString)?.value ?: ""
            SomniaValue.num(a.compareTo(b))
        })
        
        env.define("native_hash", SomniaValue.SNative("native_hash") { args ->
            SomniaValue.num(args.firstOrNull()?.hashCode() ?: 0)
        })
        
        // JSON
        env.define("native_to_json", SomniaValue.SNative("native_to_json") { args ->
            SomniaValue.str(toJson(args.firstOrNull() ?: SomniaValue.NULL))
        })
        
        env.define("native_parse_json", SomniaValue.SNative("native_parse_json") { args ->
            val json = (args.firstOrNull() as? SomniaValue.SString)?.value ?: "{}"
            parseJson(json)
        })
        
        // String extensions
        env.define("string_starts_with", SomniaValue.SNative("string_starts_with") { args ->
            val s = (args.getOrNull(0) as? SomniaValue.SString)?.value ?: ""
            val prefix = (args.getOrNull(1) as? SomniaValue.SString)?.value ?: ""
            SomniaValue.bool(s.startsWith(prefix))
        })
        
        env.define("string_ends_with", SomniaValue.SNative("string_ends_with") { args ->
            val s = (args.getOrNull(0) as? SomniaValue.SString)?.value ?: ""
            val suffix = (args.getOrNull(1) as? SomniaValue.SString)?.value ?: ""
            SomniaValue.bool(s.endsWith(suffix))
        })
        
        env.define("string_substring", SomniaValue.SNative("string_substring") { args ->
            val s = (args.getOrNull(0) as? SomniaValue.SString)?.value ?: ""
            val start = (args.getOrNull(1) as? SomniaValue.SNumber)?.value?.toInt() ?: 0
            SomniaValue.str(s.substring(start.coerceIn(0, s.length)))
        })
        
        env.define("string_split", SomniaValue.SNative("string_split") { args ->
            val s = (args.getOrNull(0) as? SomniaValue.SString)?.value ?: ""
            val delimiter = (args.getOrNull(1) as? SomniaValue.SString)?.value ?: ""
            SomniaValue.SList(s.split(delimiter).map { SomniaValue.str(it) }.toMutableList())
        })
        
        env.define("string_length", SomniaValue.SNative("string_length") { args ->
            val s = (args.getOrNull(0) as? SomniaValue.SString)?.value ?: ""
            SomniaValue.num(s.length)
        })
        
        env.define("list_length", SomniaValue.SNative("list_length") { args ->
            val list = (args.firstOrNull() as? SomniaValue.SList)?.items ?: listOf<SomniaValue>()
            SomniaValue.num(list.size)
        })
        
        env.define("len", SomniaValue.SNative("len") { args ->
            val obj = args.firstOrNull()
            when (obj) {
                is SomniaValue.SString -> SomniaValue.num(obj.value.length)
                is SomniaValue.SList -> SomniaValue.num(obj.items.size)
                is SomniaValue.SMap -> SomniaValue.num(obj.entries.size)
                else -> SomniaValue.num(0)
            }
        })
        
        // Math
        env.define("math_pow", SomniaValue.SNative("math_pow") { args ->
            val base = (args.getOrNull(0) as? SomniaValue.SNumber)?.value ?: 0.0
            val exp = (args.getOrNull(1) as? SomniaValue.SNumber)?.value ?: 0.0
            SomniaValue.num(base.pow(exp))
        })
        
        env.define("math_sqrt", SomniaValue.SNative("math_sqrt") { args ->
            val n = (args.firstOrNull() as? SomniaValue.SNumber)?.value ?: 0.0
            SomniaValue.num(sqrt(n))
        })
        
        env.define("math_abs", SomniaValue.SNative("math_abs") { args ->
            val n = (args.firstOrNull() as? SomniaValue.SNumber)?.value ?: 0.0
            SomniaValue.num(abs(n))
        })
        
        env.define("math_floor", SomniaValue.SNative("math_floor") { args ->
            val n = (args.firstOrNull() as? SomniaValue.SNumber)?.value ?: 0.0
            SomniaValue.num(floor(n))
        })
        
        env.define("math_ceil", SomniaValue.SNative("math_ceil") { args ->
            val n = (args.firstOrNull() as? SomniaValue.SNumber)?.value ?: 0.0
            SomniaValue.num(ceil(n))
        })
        
        env.define("math_random", SomniaValue.SNative("math_random") { 
            SomniaValue.num(Math.random())
        })
        
        // Response (placeholder)
        var currentResponse: SomniaValue = SomniaValue.NULL
        
        env.define("native_set_response", SomniaValue.SNative("native_set_response") { args ->
            currentResponse = args.getOrNull(1) ?: SomniaValue.NULL
            SomniaValue.NULL
        })
        
        env.define("native_get_response", SomniaValue.SNative("native_get_response") { 
            currentResponse
        })
        
        // FFI (placeholder)
        env.define("native_ffi_call", SomniaValue.SNative("native_ffi_call") { args ->
            SomniaValue.NULL // TODO: Implement FFI
        })
        
        env.define("native_call_with_timeout", SomniaValue.SNative("native_call_with_timeout") { args ->
            SomniaValue.NULL // TODO: Implement with timeout
        })
        
        // Assert (for tests)
        env.define("assert", SomniaValue.SNative("assert") { args ->
            val condition = args.firstOrNull()?.isTruthy() ?: false
            if (!condition) {
                throw AssertionError("Assertion failed")
            }
            SomniaValue.NULL
        })
        
        // Print
        env.define("println", SomniaValue.SNative("println") { args ->
            println(args.firstOrNull()?.toSomniaString() ?: "")
            SomniaValue.NULL
        })
        
        env.define("print", SomniaValue.SNative("print") { args ->
            print(args.firstOrNull()?.toSomniaString() ?: "")
            SomniaValue.NULL
        })
    }
    
    private fun toJson(value: SomniaValue): String = when (value) {
        is SomniaValue.SNull -> "null"
        is SomniaValue.SBool -> value.value.toString()
        is SomniaValue.SNumber -> value.toSomniaString()
        is SomniaValue.SString -> "\"${value.value.replace("\"", "\\\"")}\""
        is SomniaValue.SList -> value.items.joinToString(",", "[", "]") { toJson(it) }
        is SomniaValue.SMap -> value.entries.entries.joinToString(",", "{", "}") { 
            "\"${it.key}\":${toJson(it.value)}" 
        }
        else -> "null"
    }
    
    private fun parseJson(json: String): SomniaValue {
        // Simplified JSON parser
        val trimmed = json.trim()
        return when {
            trimmed == "null" -> SomniaValue.NULL
            trimmed == "true" -> SomniaValue.bool(true)
            trimmed == "false" -> SomniaValue.bool(false)
            trimmed.startsWith("\"") -> SomniaValue.str(trimmed.removeSurrounding("\""))
            trimmed.startsWith("[") -> SomniaValue.list() // TODO: Parse list
            trimmed.startsWith("{") -> SomniaValue.map()  // TODO: Parse map
            trimmed.toDoubleOrNull() != null -> SomniaValue.num(trimmed.toDouble())
            else -> SomniaValue.NULL
        }
    }
}
