package somnia.core

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * The Universal Bridge.
 * Allows SOMNIA to act as a full JVM language by calling Java/Kotlin classes dynamically.
 */
object ReflectionBridge {
    
    fun call(className: String, methodName: String, args: List<Any?>): ActionResult {
        try {
            // 1. Find Class
            val clazz = Class.forName(className)
            
            // 2. Find Method
            val methods = clazz.methods.filter { it.name == methodName && it.parameterCount == args.size }
            
            if (methods.isEmpty()) return ActionResult(false, "Method '$methodName' with ${args.size} args not found in $className")
            
            val method = methods.first() // Take best match
            
            // 3. Prepare Arguments
            val typedArgs = method.parameterTypes.zip(args).map { (type, arg) ->
                convertArg(type, arg)
            }.toTypedArray()
            
            // 4. Invoke
            val result = if (Modifier.isStatic(method.modifiers)) {
                method.invoke(null, *typedArgs)
            } else {
                val instance = clazz.getDeclaredConstructor().newInstance()
                method.invoke(instance, *typedArgs)
            }
            
            return ActionResult(true, result)
            
        } catch (e: Exception) {
            return ActionResult(false, "Reflection Error: ${e.cause?.message ?: e.message}")
        }
    }
    
    private fun convertArg(type: Class<*>, arg: Any?): Any? {
        if (arg == null) return null
        if (type.isAssignableFrom(arg.javaClass)) return arg
        
        // Basic String conversions if needed
        val str = arg.toString()
        return when (type) {
            Int::class.java, Integer::class.java -> str.toIntOrNull() ?: 0
            Double::class.java, java.lang.Double::class.java -> str.toDoubleOrNull() ?: 0.0
            Boolean::class.java, java.lang.Boolean::class.java -> str.toBoolean()
            else -> arg // Attempt to pass as is
        }
    }
}
