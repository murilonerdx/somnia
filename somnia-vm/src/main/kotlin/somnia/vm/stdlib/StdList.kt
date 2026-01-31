package somnia.vm.stdlib

import somnia.vm.SomniaCallable
import somnia.vm.SomniaVM

/**
 * Standard List Module (std/list)
 * Functional list operations.
 */
object StdList {
    
    val size = object : SomniaCallable {
        override val name = "size"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return when (val list = args[0]) {
                is List<*> -> list.size
                is String -> list.length
                else -> 0
            }
        }
    }
    
    val first = object : SomniaCallable {
        override val name = "first"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            return (args[0] as? List<*>)?.firstOrNull()
        }
    }
    
    val last = object : SomniaCallable {
        override val name = "last"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            return (args[0] as? List<*>)?.lastOrNull()
        }
    }
    
    val get = object : SomniaCallable {
        override val name = "get"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val list = args[0] as? List<*> ?: return null
            val index = (args[1] as? Number)?.toInt() ?: return null
            return if (index in list.indices) list[index] else null
        }
    }
    
    val push = object : SomniaCallable {
        override val name = "push"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val list = (args[0] as? List<*>)?.toMutableList() ?: mutableListOf()
            list.add(args[1])
            return list
        }
    }
    
    val concat = object : SomniaCallable {
        override val name = "concat"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val list1 = (args[0] as? List<*>) ?: emptyList<Any?>()
            val list2 = (args[1] as? List<*>) ?: emptyList<Any?>()
            return list1 + list2
        }
    }
    
    val reverse = object : SomniaCallable {
        override val name = "reverse"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return (args[0] as? List<*>)?.reversed() ?: emptyList<Any?>()
        }
    }
    
    val range = object : SomniaCallable {
        override val name = "range"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val start = (args[0] as? Number)?.toInt() ?: 0
            val end = (args[1] as? Number)?.toInt() ?: 10
            return (start until end).toList()
        }
    }
    
    val includes = object : SomniaCallable {
        override val name = "includes"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val list = args[0] as? List<*> ?: return false
            return list.contains(args[1])
        }
    }
    
    val isEmpty = object : SomniaCallable {
        override val name = "isEmpty"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            return when (val v = args[0]) {
                is List<*> -> v.isEmpty()
                is String -> v.isEmpty()
                null -> true
                else -> false
            }
        }
    }
    
    val sum = object : SomniaCallable {
        override val name = "sum"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val list = args[0] as? List<*> ?: return 0.0
            return list.filterIsInstance<Number>().sumOf { it.toDouble() }
        }
    }
    
    val avg = object : SomniaCallable {
        override val name = "avg"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any {
            val list = (args[0] as? List<*>)?.filterIsInstance<Number>() ?: return 0.0
            if (list.isEmpty()) return 0.0
            return list.sumOf { it.toDouble() } / list.size
        }
    }
    
    fun all(): List<SomniaCallable> = listOf(
        size, first, last, get, push, concat, reverse, range, includes, isEmpty, sum, avg
    )
}
