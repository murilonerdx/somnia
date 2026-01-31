package somnia.vm.stdlib

import somnia.vm.SomniaCallable

/**
 * Standard Library Registry
 * Aggregates all stdlib modules and provides easy access.
 */
object Stdlib {
    
    fun all(): Map<String, SomniaCallable> {
        val functions = mutableMapOf<String, SomniaCallable>()
        
        // IO
        StdIO.all().forEach { functions[it.name] = it }
        
        // HTTP
        StdHttp.all().forEach { functions[it.name] = it }
        
        // Math
        StdMath.all().forEach { functions[it.name] = it }
        
        // String
        StdString.all().forEach { functions[it.name] = it }
        
        // List
        StdList.all().forEach { functions[it.name] = it }
        
        // Time
        StdTime.all().forEach { functions[it.name] = it }
        
        // Runtime
        StdRuntime.all().forEach { functions[it.name] = it }
        
        return functions
    }
    
    fun getModule(name: String): List<SomniaCallable> {
        return when (name) {
            "io" -> StdIO.all()
            "http" -> StdHttp.all()
            "math" -> StdMath.all()
            "string" -> StdString.all()
            "list" -> StdList.all()
            "time" -> StdTime.all()
            "runtime" -> StdRuntime.all()
            else -> emptyList()
        }
    }
    
    fun listModules(): List<String> = listOf(
        "io", "http", "math", "string", "list", "time", "runtime"
    )
}
