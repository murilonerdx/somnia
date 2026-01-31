package somnia.vm.stdlib

import somnia.vm.SomniaCallable
import somnia.vm.SomniaVM
import kotlin.math.*
import kotlin.random.Random

/**
 * Standard Math Module (std/math)
 * Mathematical functions and constants.
 */
object StdMath {
    
    val PI = object : SomniaCallable {
        override val name = "PI"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = kotlin.math.PI
    }
    
    val E = object : SomniaCallable {
        override val name = "E"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = kotlin.math.E
    }
    
    val abs = object : SomniaCallable {
        override val name = "abs"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val n = (args[0] as? Number)?.toDouble() ?: 0.0
            return kotlin.math.abs(n)
        }
    }
    
    val sqrt = object : SomniaCallable {
        override val name = "sqrt"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val n = (args[0] as? Number)?.toDouble() ?: 0.0
            return kotlin.math.sqrt(n)
        }
    }
    
    val pow = object : SomniaCallable {
        override val name = "pow"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val base = (args[0] as? Number)?.toDouble() ?: 0.0
            val exp = (args[1] as? Number)?.toDouble() ?: 1.0
            return base.pow(exp)
        }
    }
    
    val floor = object : SomniaCallable {
        override val name = "floor"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val n = (args[0] as? Number)?.toDouble() ?: 0.0
            return kotlin.math.floor(n).toInt()
        }
    }
    
    val ceil = object : SomniaCallable {
        override val name = "ceil"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val n = (args[0] as? Number)?.toDouble() ?: 0.0
            return kotlin.math.ceil(n).toInt()
        }
    }
    
    val round = object : SomniaCallable {
        override val name = "round"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val n = (args[0] as? Number)?.toDouble() ?: 0.0
            return kotlin.math.round(n).toInt()
        }
    }
    
    val random = object : SomniaCallable {
        override val name = "random"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any = Random.nextDouble()
    }
    
    val randomInt = object : SomniaCallable {
        override val name = "randomInt"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val min = (args[0] as? Number)?.toInt() ?: 0
            val max = (args[1] as? Number)?.toInt() ?: 100
            return Random.nextInt(min, max)
        }
    }
    
    val sin = object : SomniaCallable {
        override val name = "sin"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val n = (args[0] as? Number)?.toDouble() ?: 0.0
            return kotlin.math.sin(n)
        }
    }
    
    val cos = object : SomniaCallable {
        override val name = "cos"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val n = (args[0] as? Number)?.toDouble() ?: 0.0
            return kotlin.math.cos(n)
        }
    }
    
    val max = object : SomniaCallable {
        override val name = "max"
        override val arity = -1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            return args.filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
        }
    }
    
    val min = object : SomniaCallable {
        override val name = "min"
        override val arity = -1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            return args.filterIsInstance<Number>().minOfOrNull { it.toDouble() }
        }
    }
    
    fun all(): List<SomniaCallable> = listOf(
        PI, E, abs, sqrt, pow, floor, ceil, round, random, randomInt, sin, cos, max, min
    )
}
