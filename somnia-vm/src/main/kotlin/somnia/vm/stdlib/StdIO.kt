package somnia.vm.stdlib

import somnia.vm.SomniaCallable
import somnia.vm.SomniaVM
import java.io.File

/**
 * Standard IO Module (std/io)
 * Functions for input/output operations.
 */
object StdIO {
    
    val print = object : SomniaCallable {
        override val name = "print"
        override val arity = -1 // Variadic
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            print(args.joinToString(" ") { it?.toString() ?: "null" })
            return null
        }
    }
    
    val println = object : SomniaCallable {
        override val name = "println"
        override val arity = -1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            println(args.joinToString(" ") { it?.toString() ?: "null" })
            return null
        }
    }
    
    val readLine = object : SomniaCallable {
        override val name = "readLine"
        override val arity = 0
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            return readln()
        }
    }
    
    val readFile = object : SomniaCallable {
        override val name = "readFile"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val path = args[0] as? String ?: throw RuntimeException("readFile expects a string path")
            return try {
                File(path).readText()
            } catch (e: Exception) {
                throw RuntimeException("Failed to read file: ${e.message}")
            }
        }
    }
    
    val writeFile = object : SomniaCallable {
        override val name = "writeFile"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val path = args[0] as? String ?: throw RuntimeException("writeFile expects path as first arg")
            val content = args[1]?.toString() ?: ""
            return try {
                File(path).writeText(content)
                true
            } catch (e: Exception) {
                throw RuntimeException("Failed to write file: ${e.message}")
            }
        }
    }
    
    val appendFile = object : SomniaCallable {
        override val name = "appendFile"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val path = args[0] as? String ?: throw RuntimeException("appendFile expects path as first arg")
            val content = args[1]?.toString() ?: ""
            return try {
                File(path).appendText(content)
                true
            } catch (e: Exception) {
                throw RuntimeException("Failed to append file: ${e.message}")
            }
        }
    }
    
    val fileExists = object : SomniaCallable {
        override val name = "fileExists"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val path = args[0] as? String ?: return false
            return File(path).exists()
        }
    }
    
    fun all(): List<SomniaCallable> = listOf(
        print, println, readLine, readFile, writeFile, appendFile, fileExists
    )
}
