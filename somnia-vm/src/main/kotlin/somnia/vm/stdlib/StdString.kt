package somnia.vm.stdlib

import somnia.vm.SomniaCallable
import somnia.vm.SomniaVM

/**
 * Standard String Module (std/string)
 * String manipulation functions.
 */
object StdString {
    
    val length = object : SomniaCallable {
        override val name = "strlen"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return (args[0]?.toString() ?: "").length
        }
    }
    
    val upper = object : SomniaCallable {
        override val name = "upper"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return (args[0]?.toString() ?: "").uppercase()
        }
    }
    
    val lower = object : SomniaCallable {
        override val name = "lower"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return (args[0]?.toString() ?: "").lowercase()
        }
    }
    
    val trim = object : SomniaCallable {
        override val name = "trim"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return (args[0]?.toString() ?: "").trim()
        }
    }
    
    val split = object : SomniaCallable {
        override val name = "split"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val str = args[0]?.toString() ?: ""
            val delimiter = args[1]?.toString() ?: ","
            return str.split(delimiter)
        }
    }
    
    val join = object : SomniaCallable {
        override val name = "join"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val list = args[0] as? List<*> ?: return ""
            val delimiter = args[1]?.toString() ?: ","
            return list.joinToString(delimiter) { it?.toString() ?: "" }
        }
    }
    
    val replace = object : SomniaCallable {
        override val name = "replace"
        override val arity = 3
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val str = args[0]?.toString() ?: ""
            val old = args[1]?.toString() ?: ""
            val new = args[2]?.toString() ?: ""
            return str.replace(old, new)
        }
    }
    
    val contains = object : SomniaCallable {
        override val name = "contains"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val str = args[0]?.toString() ?: ""
            val substr = args[1]?.toString() ?: ""
            return str.contains(substr)
        }
    }
    
    val startsWith = object : SomniaCallable {
        override val name = "startsWith"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val str = args[0]?.toString() ?: ""
            val prefix = args[1]?.toString() ?: ""
            return str.startsWith(prefix)
        }
    }
    
    val endsWith = object : SomniaCallable {
        override val name = "endsWith"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val str = args[0]?.toString() ?: ""
            val suffix = args[1]?.toString() ?: ""
            return str.endsWith(suffix)
        }
    }
    
    val substring = object : SomniaCallable {
        override val name = "substring"
        override val arity = 3
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val str = args[0]?.toString() ?: ""
            val start = (args[1] as? Number)?.toInt() ?: 0
            val end = (args[2] as? Number)?.toInt() ?: str.length
            return str.substring(start.coerceAtLeast(0), end.coerceAtMost(str.length))
        }
    }
    
    val format = object : SomniaCallable {
        override val name = "format"
        override val arity = -1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            if (args.isEmpty()) return ""
            val template = args[0]?.toString() ?: ""
            var result = template
            args.drop(1).forEachIndexed { i, arg ->
                result = result.replace("{$i}", arg?.toString() ?: "null")
            }
            return result
        }
    }
    
    fun all(): List<SomniaCallable> = listOf(
        length, upper, lower, trim, split, join, replace, 
        contains, startsWith, endsWith, substring, format
    )
}
