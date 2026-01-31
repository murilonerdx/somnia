package somnia.core.value

/**
 * SomniaValue - All value types in Somnia.
 * Immutable, type-safe representation of data.
 */
sealed interface SomniaValue {
    
    /** String value */
    @JvmInline
    value class SString(val value: String) : SomniaValue {
        override fun toString() = "\"$value\""
    }
    
    /** Numeric value (unified int/double) */
    @JvmInline
    value class SNumber(val value: Double) : SomniaValue {
        constructor(value: Int) : this(value.toDouble())
        constructor(value: Long) : this(value.toDouble())
        
        val isInteger: Boolean get() = value == value.toLong().toDouble()
        fun toInt(): Int = value.toInt()
        fun toLong(): Long = value.toLong()
        
        override fun toString() = if (isInteger) value.toLong().toString() else value.toString()
    }
    
    /** Boolean value */
    @JvmInline
    value class SBool(val value: Boolean) : SomniaValue {
        override fun toString() = value.toString()
    }
    
    /** Null value */
    data object SNull : SomniaValue {
        override fun toString() = "null"
    }
    
    /** List of values */
    @JvmInline
    value class SList(val items: List<SomniaValue>) : SomniaValue {
        constructor(vararg items: SomniaValue) : this(items.toList())
        
        operator fun get(index: Int): SomniaValue = items.getOrElse(index) { SNull }
        val size: Int get() = items.size
        fun isEmpty(): Boolean = items.isEmpty()
        
        override fun toString() = items.joinToString(", ", "[", "]")
    }
    
    /** Map of string keys to values */
    @JvmInline
    value class SMap(val entries: Map<String, SomniaValue>) : SomniaValue {
        constructor(vararg pairs: Pair<String, SomniaValue>) : this(pairs.toMap())
        
        operator fun get(key: String): SomniaValue = entries[key] ?: SNull
        operator fun contains(key: String): Boolean = key in entries
        val size: Int get() = entries.size
        fun isEmpty(): Boolean = entries.isEmpty()
        val keys: Set<String> get() = entries.keys
        
        override fun toString() = entries.entries.joinToString(", ", "{", "}") { "${it.key}: ${it.value}" }
    }
    
    /** Symbol (keyword-like identifier) */
    @JvmInline
    value class SSymbol(val name: String) : SomniaValue {
        override fun toString() = ":$name"
    }
    
    companion object {
        // Factory methods for easier creation
        fun string(value: String) = SString(value)
        fun number(value: Double) = SNumber(value)
        fun number(value: Int) = SNumber(value)
        fun number(value: Long) = SNumber(value)
        fun bool(value: Boolean) = SBool(value)
        fun list(vararg items: SomniaValue) = SList(items.toList())
        fun list(items: List<SomniaValue>) = SList(items)
        fun map(vararg pairs: Pair<String, SomniaValue>) = SMap(pairs.toMap())
        fun map(entries: Map<String, SomniaValue>) = SMap(entries)
        fun symbol(name: String) = SSymbol(name)
        val NULL = SNull
        val TRUE = SBool(true)
        val FALSE = SBool(false)
    }
}

/** Check if value is truthy */
fun SomniaValue.isTruthy(): Boolean = when (this) {
    is SomniaValue.SNull -> false
    is SomniaValue.SBool -> value
    is SomniaValue.SNumber -> value != 0.0
    is SomniaValue.SString -> value.isNotEmpty()
    is SomniaValue.SList -> items.isNotEmpty()
    is SomniaValue.SMap -> entries.isNotEmpty()
    is SomniaValue.SSymbol -> true
}

/** Convert any Kotlin value to SomniaValue */
fun Any?.toSomniaValue(): SomniaValue = when (this) {
    null -> SomniaValue.SNull
    is SomniaValue -> this
    is String -> SomniaValue.SString(this)
    is Boolean -> SomniaValue.SBool(this)
    is Int -> SomniaValue.SNumber(this)
    is Long -> SomniaValue.SNumber(this)
    is Double -> SomniaValue.SNumber(this)
    is Float -> SomniaValue.SNumber(this.toDouble())
    is List<*> -> SomniaValue.SList(this.map { it.toSomniaValue() })
    is Map<*, *> -> SomniaValue.SMap(this.entries.associate { 
        it.key.toString() to it.value.toSomniaValue() 
    })
    else -> SomniaValue.SString(this.toString())
}

/** Convert SomniaValue to native Kotlin type */
fun SomniaValue.toNative(): Any? = when (this) {
    is SomniaValue.SNull -> null
    is SomniaValue.SBool -> value
    is SomniaValue.SNumber -> if (isInteger) toLong() else value
    is SomniaValue.SString -> value
    is SomniaValue.SList -> items.map { it.toNative() }
    is SomniaValue.SMap -> entries.mapValues { it.value.toNative() }
    is SomniaValue.SSymbol -> name
}
