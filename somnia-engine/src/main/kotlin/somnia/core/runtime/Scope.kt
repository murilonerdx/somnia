package somnia.core.runtime

class Scope(val parent: Scope? = null) {
    private val values = mutableMapOf<String, Any?>()
    private val consts = mutableSetOf<String>()

    fun define(name: String, value: Any?, isConst: Boolean = false) {
        if (values.containsKey(name)) {
            throw RuntimeException("Variable '$name' is already defined in this scope.")
        }
        values[name] = value
        if (isConst) consts.add(name)
    }

    fun assign(name: String, value: Any?) {
        if (values.containsKey(name)) {
            if (consts.contains(name)) {
                throw RuntimeException("Cannot reassign constant '$name'.")
            }
            values[name] = value
            return
        }
        
        if (parent != null) {
            parent.assign(name, value)
            return
        }

        throw RuntimeException("Undefined variable '$name'.")
    }

    fun get(name: String): Any? {
        if (values.containsKey(name)) {
            return values[name]
        }
        if (parent != null) return parent.get(name)
        throw RuntimeException("Undefined variable '$name'.")
    }
}
