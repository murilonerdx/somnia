package somnia.vm

import somnia.lang.*
import java.io.ByteArrayOutputStream

/**
 * Somnia Bytecode Compiler
 * Transforms Somnia AST into bytecode for the SVM.
 */
class Compiler {
    private val bcFile = BytecodeFile()
    private val currentCode = ByteArrayOutputStream()
    private var localVars = mutableMapOf<String, Int>()
    private var localCount = 0
    
    fun compile(program: SomniaProgram): BytecodeFile {
        // Compile ID block (constants and variables become globals)
        compileIdBlock(program.id)
        
        // Compile EGO functions
        for (func in program.ego.functions) {
            compileFunction(func)
        }
        
        // Create main entry point that initializes globals
        val mainFunc = createMainFunction(program)
        bcFile.entryPoint = bcFile.functions.size
        bcFile.functions.add(mainFunc)
        
        return bcFile
    }
    
    private fun compileIdBlock(id: IdBlock) {
        // Constants and variables are initialized in main
    }
    
    private fun compileFunction(func: FunctionDecl): Int {
        localVars.clear()
        localCount = 0
        currentCode.reset()
        
        // Register parameters as locals
        for (param in func.params) {
            localVars[param.name] = localCount++
        }
        
        // Compile body
        for (stmt in func.body) {
            compileStatement(stmt)
        }
        
        // Ensure function returns
        emit(Opcode.RETURN_VOID)
        
        val sFunc = SFunction(
            name = func.name,
            paramCount = func.params.size,
            localCount = localCount,
            bytecode = currentCode.toByteArray()
        )
        
        bcFile.functions.add(sFunc)
        return bcFile.functions.size - 1
    }
    
    private fun createMainFunction(program: SomniaProgram): SFunction {
        localVars.clear()
        localCount = 0
        currentCode.reset()
        
        // Initialize constants
        for (const in program.id.consts) {
            compileExpr(const.value)
            val nameIdx = bcFile.addConstant(const.name)
            emit(Opcode.STORE_GLOBAL)
            emitShort(nameIdx)
        }
        
        // Initialize variables
        for (v in program.id.vars) {
            if (v.value != null) {
                compileExpr(v.value!!)
            } else {
                emit(Opcode.CONST_NULL)
            }
            val nameIdx = bcFile.addConstant(v.name)
            emit(Opcode.STORE_GLOBAL)
            emitShort(nameIdx)
        }
        
        // Print loaded message
        val msgIdx = bcFile.addConstant("[SVM] Program initialized")
        emit(Opcode.CONST_STRING)
        emitShort(msgIdx)
        emit(Opcode.PRINTLN)
        
        emit(Opcode.RETURN_VOID)
        
        return SFunction(
            name = "__main__",
            paramCount = 0,
            localCount = localCount,
            bytecode = currentCode.toByteArray()
        )
    }
    
    private fun compileStatement(stmt: Statement) {
        when (stmt) {
            is Statement.Expression -> {
                compileExpr(stmt.expr)
                emit(Opcode.POP) // Discard result
            }
            is Statement.Var -> {
                val idx = localCount
                localVars[stmt.name] = idx
                localCount++
                
                if (stmt.initializer != null) {
                    compileExpr(stmt.initializer!!)
                } else {
                    emit(Opcode.CONST_NULL)
                }
                emit(Opcode.STORE_LOCAL)
                emitByte(idx)
            }
            is Statement.Assign -> {
                compileExpr(stmt.value)
                val localIdx = localVars[stmt.name]
                if (localIdx != null) {
                    emit(Opcode.STORE_LOCAL)
                    emitByte(localIdx)
                } else {
                    val nameIdx = bcFile.addConstant(stmt.name)
                    emit(Opcode.STORE_GLOBAL)
                    emitShort(nameIdx)
                }
            }
            is Statement.If -> {
                compileExpr(stmt.condition)
                val jumpFalsePos = currentCode.size()
                emit(Opcode.JUMP_IF_FALSE)
                emitShort(0) // Placeholder
                
                for (s in stmt.thenBranch) compileStatement(s)
                
                if (stmt.elseBranch != null) {
                    val jumpEndPos = currentCode.size()
                    emit(Opcode.JUMP)
                    emitShort(0) // Placeholder
                    
                    // Patch jump-false to here
                    patchJump(jumpFalsePos + 1, currentCode.size())
                    
                    for (s in stmt.elseBranch!!) compileStatement(s)
                    
                    // Patch jump-end to here
                    patchJump(jumpEndPos + 1, currentCode.size())
                } else {
                    patchJump(jumpFalsePos + 1, currentCode.size())
                }
            }
            is Statement.Return -> {
                compileExpr(stmt.value)
                emit(Opcode.RETURN)
            }
        }
    }
    
    private fun compileExpr(expr: Expr) {
        when (expr) {
            is Expr.Literal -> {
                when (val v = expr.value) {
                    null -> emit(Opcode.CONST_NULL)
                    true -> emit(Opcode.CONST_TRUE)
                    false -> emit(Opcode.CONST_FALSE)
                    is Int -> { emit(Opcode.CONST_INT); emitInt(v) }
                    is Long -> { emit(Opcode.CONST_LONG); emitLong(v) }
                    is Double -> { emit(Opcode.CONST_DOUBLE); emitDouble(v) }
                    is String -> {
                        val idx = bcFile.addConstant(v)
                        emit(Opcode.CONST_STRING)
                        emitShort(idx)
                    }
                    else -> throw RuntimeException("Unknown literal type: ${v::class}")
                }
            }
            is Expr.Variable -> {
                val localIdx = localVars[expr.name]
                if (localIdx != null) {
                    emit(Opcode.LOAD_LOCAL)
                    emitByte(localIdx)
                } else {
                    val nameIdx = bcFile.addConstant(expr.name)
                    emit(Opcode.LOAD_GLOBAL)
                    emitShort(nameIdx)
                }
            }
            is Expr.Binary -> {
                compileExpr(expr.left)
                compileExpr(expr.right)
                when (expr.op) {
                    TokenType.PLUS -> emit(Opcode.ADD)
                    TokenType.MINUS -> emit(Opcode.SUB)
                    TokenType.STAR -> emit(Opcode.MUL)
                    TokenType.SLASH -> emit(Opcode.DIV)
                    TokenType.EQ_EQ -> emit(Opcode.EQ)
                    TokenType.BANG_EQ -> emit(Opcode.NE)
                    TokenType.LT -> emit(Opcode.LT)
                    TokenType.LT_EQ -> emit(Opcode.LE)
                    TokenType.GT -> emit(Opcode.GT)
                    TokenType.GT_EQ -> emit(Opcode.GE)
                    TokenType.KEYWORD_AND -> emit(Opcode.AND)
                    TokenType.KEYWORD_OR -> emit(Opcode.OR)
                    else -> throw RuntimeException("Unknown binary op: ${expr.op}")
                }
            }
            is Expr.Unary -> {
                compileExpr(expr.right)
                when (expr.op) {
                    TokenType.MINUS -> emit(Opcode.NEG)
                    TokenType.KEYWORD_NOT -> emit(Opcode.NOT)
                    else -> throw RuntimeException("Unknown unary op: ${expr.op}")
                }
            }
            is Expr.Call -> {
                // Compile arguments
                for (arg in expr.args) {
                    compileExpr(arg)
                }
                
                // Check if it's a native or user function
                if (expr.callee is Expr.Variable) {
                    val name = (expr.callee as Expr.Variable).name
                    
                    // Check if it's a known function in bcFile
                    val funcIdx = bcFile.functions.indexOfFirst { it.name == name }
                    if (funcIdx >= 0) {
                        emit(Opcode.CALL)
                        emitShort(funcIdx)
                        emitByte(expr.args.size)
                    } else {
                        // Assume native
                        val nameIdx = bcFile.addConstant(name)
                        emit(Opcode.CALL_NATIVE)
                        emitShort(nameIdx)
                        emitByte(expr.args.size)
                    }
                }
            }
            is Expr.Grouping -> compileExpr(expr.expression)
            else -> throw RuntimeException("Unknown expression type: ${expr::class}")
        }
    }
    
    private fun emit(op: Opcode) = currentCode.write(op.code.toInt())
    private fun emitByte(b: Int) = currentCode.write(b)
    private fun emitShort(s: Int) {
        currentCode.write((s shr 8) and 0xFF)
        currentCode.write(s and 0xFF)
    }
    private fun emitInt(i: Int) {
        currentCode.write((i shr 24) and 0xFF)
        currentCode.write((i shr 16) and 0xFF)
        currentCode.write((i shr 8) and 0xFF)
        currentCode.write(i and 0xFF)
    }
    private fun emitLong(l: Long) {
        for (shift in 56 downTo 0 step 8) {
            currentCode.write(((l shr shift) and 0xFF).toInt())
        }
    }
    private fun emitDouble(d: Double) = emitLong(java.lang.Double.doubleToLongBits(d))
    
    private fun patchJump(position: Int, target: Int) {
        val bytes = currentCode.toByteArray()
        bytes[position] = ((target shr 8) and 0xFF).toByte()
        bytes[position + 1] = (target and 0xFF).toByte()
        // Unfortunately ByteArrayOutputStream doesn't support patching,
        // so we need to track patches and apply them at the end.
        // For now, this is a simplified implementation.
    }
}
