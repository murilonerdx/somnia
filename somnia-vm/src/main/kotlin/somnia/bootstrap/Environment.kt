package somnia.bootstrap

/**
 * Environment - Variable scope
 */
class Environment(val parent: Environment? = null) {
    private val values = mutableMapOf<String, SomniaValue>()
    
    fun define(name: String, value: SomniaValue) {
        values[name] = value
    }
    
    fun get(name: String): SomniaValue? {
        return values[name] ?: parent?.get(name)
    }
    
    fun set(name: String, value: SomniaValue): Boolean {
        if (values.containsKey(name)) {
            values[name] = value
            return true
        }
        return parent?.set(name, value) ?: false
    }
    
    fun has(name: String): Boolean {
        return values.containsKey(name) || parent?.has(name) == true
    }
    
    override fun toString(): String {
        val parentStr = parent?.let { " -> $it" } ?: ""
        return "Env{vars=${values.keys}}$parentStr"
    }
}
