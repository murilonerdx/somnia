package somnia.vm.stdlib

import somnia.vm.SomniaCallable
import somnia.vm.SomniaVM

/**
 * Standard Time Module (std/time)
 * Time and date functions.
 */
object StdTime {
    
    val now = object : SomniaCallable {
        override val name = "now"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = System.currentTimeMillis()
    }
    
    val nanos = object : SomniaCallable {
        override val name = "nanos"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = System.nanoTime()
    }
    
    val sleep = object : SomniaCallable {
        override val name = "sleep"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val ms = (args[0] as? Number)?.toLong() ?: 0
            Thread.sleep(ms)
            return null
        }
    }
    
    val formatDate = object : SomniaCallable {
        override val name = "formatDate"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val millis = (args[0] as? Number)?.toLong() ?: System.currentTimeMillis()
            val date = java.util.Date(millis)
            return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)
        }
    }
    
    val year = object : SomniaCallable {
        override val name = "year"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = java.time.Year.now().value
    }
    
    val month = object : SomniaCallable {
        override val name = "month"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = java.time.LocalDate.now().monthValue
    }
    
    val day = object : SomniaCallable {
        override val name = "day"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = java.time.LocalDate.now().dayOfMonth
    }
    
    val hour = object : SomniaCallable {
        override val name = "hour"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = java.time.LocalTime.now().hour
    }
    
    val minute = object : SomniaCallable {
        override val name = "minute"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = java.time.LocalTime.now().minute
    }
    
    val elapsed = object : SomniaCallable {
        override val name = "elapsed"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val startNanos = (args[0] as? Number)?.toLong() ?: return 0L
            return (System.nanoTime() - startNanos) / 1_000_000 // Convert to ms
        }
    }
    
    fun all(): List<SomniaCallable> = listOf(
        now, nanos, sleep, formatDate, year, month, day, hour, minute, elapsed
    )
}
