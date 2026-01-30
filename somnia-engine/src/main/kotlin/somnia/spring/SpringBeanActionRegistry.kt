package somnia.spring

import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import somnia.core.ActionResult
import somnia.core.ReflectionBridge
import java.lang.reflect.Method

@Component
class SpringBeanActionRegistry(private val context: ApplicationContext) {

    fun execute(actionName: String, args: List<Any?>): ActionResult {
        // 1. Dynamic Repository Interception
        if (actionName.contains(".")) {
            val parts = actionName.split(".")
            val repoName = parts[0]
            val methodName = parts[1]
            
            // Check if this is a Somnia Repo
            // Accessing runtime? We need access to Runtime to know about repositories.
            // But SpringBeanActionRegistry doesn't have Runtime injected yet.
            // Let's inject SomniaJpaRepository and try to handle it.
            // But we need the 'Program' definitions.
            // Circular dependency: Runtime depends on this Reg, this Reg depends on Runtime?
            // Solution: Pass program context or look up 'somniaRuntime' bean lazily.
        }

        if (!actionName.contains(".")) {
            try {
                val bean = context.getBean(actionName)
                return ActionResult(true, bean)
            } catch (e: Exception) {
                return ActionResult(false, "Bean not found: $actionName")
            }
        }

        val parts = actionName.split(".")
        val beanName = parts[0]
        val methodName = parts[1]
        
        // INTERCEPT: If bean is not found, check generic repo?
        val bean = try {
            context.getBean(beanName)
        } catch (e: Exception) {
            return ActionResult(false, "Bean not found: $beanName")
        }

        return try {
            // Simplified param count match
            val methods = bean.javaClass.methods.filter { it.name == methodName && it.parameterCount == args.size }
            
            if (methods.isEmpty()) return ActionResult(false, "Method $methodName not found on bean $beanName")
            
            val method = methods.first()
            val typedArgs = method.parameterTypes.zip(args).map { (type, arg) ->
                convertArg(type, arg)
            }.toTypedArray()

            val result = method.invoke(bean, *typedArgs)
            ActionResult(true, result)
        } catch (e: Exception) {
            ActionResult(false, "Spring Action Error: ${e.message}")
        }
    }

    private fun convertArg(type: Class<*>, arg: Any?): Any? {
        if (arg == null) return null
        if (type.isAssignableFrom(arg.javaClass)) return arg
        
        val str = arg.toString()
        return when (type) {
            Int::class.java, java.lang.Integer::class.java -> str.toIntOrNull() ?: 0
            Double::class.java, java.lang.Double::class.java -> str.toDoubleOrNull() ?: 0.0
            Boolean::class.java, java.lang.Boolean::class.java -> str.toBoolean()
            else -> arg // Try generic
        }
    }
}
