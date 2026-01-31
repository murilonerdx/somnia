package somnia.vm.stdlib

import somnia.vm.SomniaCallable
import somnia.vm.SomniaVM

/**
 * Standard Runtime Module (std/runtime)
 * VM introspection and GC insights.
 */
object StdRuntime {
    
    val gcRun = object : SomniaCallable {
        override val name = "gcRun"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            System.gc()
            return null
        }
    }
    
    val memoryUsed = object : SomniaCallable {
        override val name = "memoryUsed"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val runtime = Runtime.getRuntime()
            return runtime.totalMemory() - runtime.freeMemory()
        }
    }
    
    val memoryTotal = object : SomniaCallable {
        override val name = "memoryTotal"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = Runtime.getRuntime().totalMemory()
    }
    
    val memoryMax = object : SomniaCallable {
        override val name = "memoryMax"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = Runtime.getRuntime().maxMemory()
    }
    
    val memoryFree = object : SomniaCallable {
        override val name = "memoryFree"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = Runtime.getRuntime().freeMemory()
    }
    
    val processors = object : SomniaCallable {
        override val name = "processors"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = Runtime.getRuntime().availableProcessors()
    }
    
    val version = object : SomniaCallable {
        override val name = "version"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = "Somnia VM v0.1.0"
    }
    
    val osName = object : SomniaCallable {
        override val name = "osName"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = System.getProperty("os.name") ?: "Unknown"
    }
    
    val javaVersion = object : SomniaCallable {
        override val name = "javaVersion"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = System.getProperty("java.version") ?: "Unknown"
    }
    
    val exit = object : SomniaCallable {
        override val name = "exit"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val code = (args[0] as? Number)?.toInt() ?: 0
            System.exit(code)
            return null
        }
    }
    
    val env = object : SomniaCallable {
        override val name = "env"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val name = args[0]?.toString() ?: return null
            return System.getenv(name)
        }
    }
    
    val stackTrace = object : SomniaCallable {
        override val name = "stackTrace"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return Thread.currentThread().stackTrace.map { it.toString() }
        }
    }
    
    fun all(): List<SomniaCallable> = listOf(
        gcRun, memoryUsed, memoryTotal, memoryMax, memoryFree, 
        processors, version, osName, javaVersion, exit, env, stackTrace
    )
}
