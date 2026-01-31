package somnia.cli

import somnia.lang.Lexer
import somnia.lang.Parser
import somnia.lang.Expr
import somnia.lang.TokenType
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Somnia CLI v0.1")
        println("Usage: somnia run <file.somni>")
        return
    }

    val command = args[0]
    when (command) {
        "run" -> {
            if (args.size < 2) {
                println("Error: Missing file argument.")
                println("Usage: somnia run <file.somni>")
                return
            }
            val filePath = args[1]
            runFile(filePath)
        }
        "version" -> {
            println("Somnia Lang v0.1.0")
            println("The Psychological Architecture")
        }
        else -> {
            println("Unknown command: $command")
            println("Available commands: run, version")
        }
    }
}

fun runFile(path: String) {
    val file = File(path)
    if (!file.exists()) {
        println("Error: File not found: $path")
        return
    }

    val source = file.readText()
    
    try {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parse()
        
        val interpreter = CliInterpreter()
        
        // Load ID constants/variables into global scope
        for (const in program.id.consts) {
            val value = interpreter.evaluate(const.value)
            interpreter.globals.define(const.name, value, isConst = true)
        }
        for (v in program.id.vars) {
            val value = if (v.value != null) interpreter.evaluate(v.value!!) else null
            interpreter.globals.define(v.name, value)
        }
        
        println("[Somnia] Program loaded: ${program.id.consts.size} constants, ${program.id.vars.size} variables")
        
        // Print constants to demonstrate
        for (const in program.id.consts) {
            val value = interpreter.globals.get(const.name)
            println("  ${const.name} = $value")
        }
        
        println("[Somnia] Execution complete.")
        
    } catch (e: Exception) {
        println("[Somnia Error] ${e.message}")
    }
}

// Minimal embedded interpreter for CLI (no Spring dependencies)
class CliScope(val parent: CliScope? = null) {
    private val values = mutableMapOf<String, Any?>()
    private val consts = mutableSetOf<String>()

    fun define(name: String, value: Any?, isConst: Boolean = false) {
        values[name] = value
        if (isConst) consts.add(name)
    }

    fun get(name: String): Any? {
        if (values.containsKey(name)) return values[name]
        if (parent != null) return parent.get(name)
        throw RuntimeException("Undefined variable '$name'.")
    }
}

class CliInterpreter {
    val globals = CliScope()

    fun evaluate(expr: Expr): Any? {
        return when (expr) {
            is Expr.Literal -> expr.value
            is Expr.Variable -> globals.get(expr.name)
            is Expr.Binary -> evaluateBinary(expr)
            is Expr.Grouping -> evaluate(expr.expression)
            else -> null
        }
    }

    private fun evaluateBinary(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.op) {
            TokenType.PLUS -> {
                if (left is Number && right is Number) left.toDouble() + right.toDouble()
                else left.toString() + right.toString()
            }
            TokenType.MINUS -> (left as Number).toDouble() - (right as Number).toDouble()
            TokenType.STAR -> (left as Number).toDouble() * (right as Number).toDouble()
            TokenType.SLASH -> (left as Number).toDouble() / (right as Number).toDouble()
            TokenType.GT -> (left as Number).toDouble() > (right as Number).toDouble()
            TokenType.LT -> (left as Number).toDouble() < (right as Number).toDouble()
            TokenType.EQ_EQ -> left == right
            else -> null
        }
    }
}
