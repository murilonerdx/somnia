package somnia.core.runtime

import somnia.lang.*
import somnia.lang.Statement.*
import somnia.lang.Expr.*

class Interpreter {
    // Global scope
    val globals = Scope()
    private var currentScope = globals
    
    // Native function registry
    val nativeFunctions = mutableMapOf<String, SomniaCallable>()
    
    init {
        // Register built-in native functions
        nativeFunctions["print"] = object : SomniaCallable {
            override fun arity() = -1 // Variadic
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                println(arguments.joinToString(" ") { it?.toString() ?: "null" })
                return null
            }
        }
        
        nativeFunctions["time"] = object : SomniaCallable {
            override fun arity() = 0
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return System.currentTimeMillis()
            }
        }
        
        nativeFunctions["str"] = object : SomniaCallable {
            override fun arity() = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return arguments[0]?.toString() ?: "null"
            }
        }
    }

    fun execute(statements: List<Statement>) {
        for (stmt in statements) {
            executeStatement(stmt)
        }
    }

    private fun executeStatement(stmt: Statement) {
        when (stmt) {
            is Statement.Expression -> evaluate(stmt.expr)
            is Statement.Var -> {
                val value = if (stmt.initializer != null) evaluate(stmt.initializer!!) else null
                currentScope.define(stmt.name, value)
            }
            is Statement.Assign -> {
                val value = evaluate(stmt.value)
                currentScope.assign(stmt.name, value)
            }
            is Statement.If -> {
                if (isTruthy(evaluate(stmt.condition))) {
                    executeBlock(stmt.thenBranch, Scope(currentScope))
                } else if (stmt.elseBranch != null) {
                    executeBlock(stmt.elseBranch!!, Scope(currentScope))
                }
            }
            is Statement.Return -> throw ReturnException(evaluate(stmt.value))
        }
    }

    private fun executeBlock(statements: List<Statement>, scope: Scope) {
        val previous = currentScope
        try {
            currentScope = scope
            for (stmt in statements) {
                executeStatement(stmt)
            }
        } finally {
            currentScope = previous
        }
    }

    fun evaluate(expr: Expr): Any? {
        return when (expr) {
            is Literal -> expr.value
            is Variable -> currentScope.get(expr.name)
            is Grouping -> evaluate(expr.expression)
            is Unary -> {
                val right = evaluate(expr.right)
                when (expr.op) {
                    TokenType.MINUS -> -(right as? Double ?: ((right as? Int)?.toDouble()) ?: throw RuntimeException("Operand must be a number"))
                    TokenType.KEYWORD_NOT -> !isTruthy(right)
                    else -> null
                }
            }
            is Binary -> evaluateBinary(expr)
            is Call -> {
                val callee = evaluate(expr.callee)
                val arguments = expr.args.map { evaluate(it) }
                
                if (callee is SomniaCallable) {
                    return callee.call(this, arguments)
                }
                
                // Check if it's a native function by name
                if (expr.callee is Variable) {
                    val name = (expr.callee as Variable).name
                    val native = nativeFunctions[name]
                    if (native != null) {
                        return native.call(this, arguments)
                    }
                }
                
                throw RuntimeException("Can only call functions. Got: $callee")
            }
            else -> null
        }
    }

    private fun evaluateBinary(expr: Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.op) {
            TokenType.PLUS -> {
                if (left is Double && right is Double) left + right
                else if (left is Int && right is Int) left + right
                else if (left is String || right is String) left.toString() + right.toString()
                else if (left is Double && right is Number) left + right.toDouble()
                else if (left is Int && right is Double) left.toDouble() + right
                else throw RuntimeException("Operands must be numbers or strings")
            }
            TokenType.MINUS -> checkNumbers(left, right) { a, b -> a - b }
            TokenType.STAR -> checkNumbers(left, right) { a, b -> a * b }
            TokenType.SLASH -> checkNumbers(left, right) { a, b -> a / b }
            
            TokenType.GT -> checkCompare(left, right) { a, b -> a > b }
            TokenType.GT_EQ -> checkCompare(left, right) { a, b -> a >= b }
            TokenType.LT -> checkCompare(left, right) { a, b -> a < b }
            TokenType.LT_EQ -> checkCompare(left, right) { a, b -> a <= b }
            
            TokenType.EQ_EQ, TokenType.EQ -> if (left == null && right == null) true else left == right
            TokenType.BANG_EQ -> if (left == null && right == null) false else left != right
            
            else -> null
        }
    }

    private fun checkNumbers(left: Any?, right: Any?, op: (Double, Double) -> Double): Any {
        if (left is Number && right is Number) {
            val result = op(left.toDouble(), right.toDouble())
            // Return Int if result is integer? For now keep simple
            if (result % 1.0 == 0.0) return result.toInt()
            return result
        }
        throw RuntimeException("Operands must be numbers")
    }

    private fun checkCompare(left: Any?, right: Any?, op: (Double, Double) -> Boolean): Boolean {
        if (left is Number && right is Number) {
            return op(left.toDouble(), right.toDouble())
        }
        throw RuntimeException("Operands must be numbers")
    }

    private fun isTruthy(obj: Any?): Boolean {
        if (obj == null) return false
        if (obj is Boolean) return obj
        return true
    }

    class ReturnException(val value: Any?) : RuntimeException(null, null, false, false)
}
