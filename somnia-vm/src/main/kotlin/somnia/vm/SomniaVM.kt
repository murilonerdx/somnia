package somnia.vm

import java.io.BufferedReader
import java.io.InputStreamReader
import somnia.vm.stdlib.Stdlib

/**
 * Somnia Virtual Machine - The execution engine.
 * Stack-based VM inspired by JVM and Python VM.
 */
class SomniaVM {
    // Execution state
    private val stack = ArrayDeque<Any?>(256)
    private var globals = mutableMapOf<String, Any?>()
    
    // Current frame
    private var bytecode: ByteArray = ByteArray(0)
    private var ip: Int = 0 // Instruction pointer
    private var locals: Array<Any?> = arrayOf()
    
    // Call stack
    private val callStack = ArrayDeque<CallFrame>()
    
    // Loaded bytecode file
    private lateinit var bcFile: BytecodeFile
    
    // Native functions (stdlib)
    private val natives = mutableMapOf<String, SomniaCallable>()
    
    // I/O
    private val reader = BufferedReader(InputStreamReader(System.`in`))
    
    init {
        registerNatives()
    }
    
    private fun registerNatives() {
        // Load all stdlib functions
        Stdlib.all().forEach { (name, callable) ->
            natives[name] = callable
        }
    }
    
    fun registerNative(name: String, callable: SomniaCallable) {
        natives[name] = callable
    }
    
    fun load(file: BytecodeFile) {
        this.bcFile = file
    }
    
    fun execute(): Any? {
        if (!::bcFile.isInitialized) {
            throw IllegalStateException("No bytecode file loaded")
        }
        
        // Start at entry point function
        val entryFunc = bcFile.functions[bcFile.entryPoint]
        return executeFunction(entryFunc, emptyList())
    }
    
    private fun executeFunction(func: SFunction, args: List<Any?>): Any? {
        // Save current frame
        if (bytecode.isNotEmpty()) {
            callStack.addLast(CallFrame(bytecode, ip, locals))
        }
        
        // Setup new frame
        bytecode = func.bytecode
        ip = 0
        locals = Array(func.localCount) { null }
        
        // Copy arguments to locals
        for (i in args.indices) {
            if (i < locals.size) locals[i] = args[i]
        }
        
        // Execute
        var result: Any? = null
        try {
            result = runLoop()
        } finally {
            // Restore previous frame
            if (callStack.isNotEmpty()) {
                val frame = callStack.removeLast()
                bytecode = frame.bytecode
                ip = frame.ip
                locals = frame.locals
            }
        }
        
        return result
    }
    
    private fun runLoop(): Any? {
        while (ip < bytecode.size) {
            val opcode = Opcode.fromCode(bytecode[ip])
                ?: throw IllegalArgumentException("Unknown opcode: ${bytecode[ip]}")
            ip++
            
            when (opcode) {
                Opcode.NOP -> { }
                
                // Constants
                Opcode.CONST_NULL -> push(null)
                Opcode.CONST_TRUE -> push(true)
                Opcode.CONST_FALSE -> push(false)
                Opcode.CONST_INT -> push(readInt())
                Opcode.CONST_LONG -> push(readLong())
                Opcode.CONST_DOUBLE -> push(readDouble())
                Opcode.CONST_STRING -> {
                    val idx = readShort()
                    val const = bcFile.constantPool[idx]
                    push((const as Constant.StringVal).value)
                }
                
                // Stack
                Opcode.POP -> pop()
                Opcode.DUP -> push(peek())
                Opcode.SWAP -> {
                    val a = pop()
                    val b = pop()
                    push(a)
                    push(b)
                }
                
                // Locals
                Opcode.LOAD_LOCAL -> {
                    val idx = readByte()
                    push(locals[idx])
                }
                Opcode.STORE_LOCAL -> {
                    val idx = readByte()
                    locals[idx] = pop()
                }
                Opcode.LOAD_GLOBAL -> {
                    val idx = readShort()
                    val name = (bcFile.constantPool[idx] as Constant.StringVal).value
                    push(globals[name])
                }
                Opcode.STORE_GLOBAL -> {
                    val idx = readShort()
                    val name = (bcFile.constantPool[idx] as Constant.StringVal).value
                    globals[name] = pop()
                }
                
                // Arithmetic
                Opcode.ADD -> binaryOp { a, b -> 
                    if (a is String || b is String) a.toString() + b.toString()
                    else (a as Number).toDouble() + (b as Number).toDouble()
                }
                Opcode.SUB -> binaryOp { a, b -> (a as Number).toDouble() - (b as Number).toDouble() }
                Opcode.MUL -> binaryOp { a, b -> (a as Number).toDouble() * (b as Number).toDouble() }
                Opcode.DIV -> binaryOp { a, b -> (a as Number).toDouble() / (b as Number).toDouble() }
                Opcode.MOD -> binaryOp { a, b -> (a as Number).toDouble() % (b as Number).toDouble() }
                Opcode.NEG -> push(-(pop() as Number).toDouble())
                
                // Comparison
                Opcode.EQ -> binaryOp { a, b -> a == b }
                Opcode.NE -> binaryOp { a, b -> a != b }
                Opcode.LT -> binaryOp { a, b -> (a as Comparable<Any?>).compareTo(b) < 0 }
                Opcode.LE -> binaryOp { a, b -> (a as Comparable<Any?>).compareTo(b) <= 0 }
                Opcode.GT -> binaryOp { a, b -> (a as Comparable<Any?>).compareTo(b) > 0 }
                Opcode.GE -> binaryOp { a, b -> (a as Comparable<Any?>).compareTo(b) >= 0 }
                
                // Logic
                Opcode.AND -> binaryOp { a, b -> (a as Boolean) && (b as Boolean) }
                Opcode.OR -> binaryOp { a, b -> (a as Boolean) || (b as Boolean) }
                Opcode.NOT -> push(!(pop() as Boolean))
                
                // Control Flow
                Opcode.JUMP -> {
                    val offset = readShort()
                    ip = offset
                }
                Opcode.JUMP_IF_TRUE -> {
                    val offset = readShort()
                    if (pop() as Boolean) ip = offset
                }
                Opcode.JUMP_IF_FALSE -> {
                    val offset = readShort()
                    if (!(pop() as Boolean)) ip = offset
                }
                
                // Functions
                Opcode.CALL -> {
                    val funcIdx = readShort()
                    val argCount = readByte()
                    val args = (0 until argCount).map { pop() }.reversed()
                    val func = bcFile.functions[funcIdx]
                    val result = executeFunction(func, args)
                    if (result != null) push(result)
                }
                Opcode.CALL_NATIVE -> {
                    val nameIdx = readShort()
                    val argCount = readByte()
                    val name = (bcFile.constantPool[nameIdx] as Constant.StringVal).value
                    val args = (0 until argCount).map { pop() }.reversed()
                    val native = natives[name] ?: throw RuntimeException("Unknown native: $name")
                    val result = native.call(this, args)
                    if (result != null) push(result)
                }
                Opcode.RETURN -> {
                    return pop()
                }
                Opcode.RETURN_VOID -> {
                    return null
                }
                
                // I/O
                Opcode.PRINT -> {
                    print(pop())
                }
                Opcode.PRINTLN -> {
                    println(pop())
                }
                Opcode.READ_LINE -> {
                    push(reader.readLine() ?: "")
                }
                
                Opcode.HALT -> return null
                
                else -> throw RuntimeException("Unimplemented opcode: $opcode")
            }
        }
        return null
    }
    
    // Stack operations
    private fun push(value: Any?) = stack.addLast(value)
    private fun pop(): Any? = stack.removeLast()
    private fun peek(): Any? = stack.last()
    
    private inline fun binaryOp(op: (Any?, Any?) -> Any?) {
        val b = pop()
        val a = pop()
        push(op(a, b))
    }
    
    // Bytecode reading
    private fun readByte(): Int = bytecode[ip++].toInt() and 0xFF
    private fun readShort(): Int {
        val high = readByte()
        val low = readByte()
        return (high shl 8) or low
    }
    private fun readInt(): Int {
        var result = 0
        for (i in 0..3) {
            result = (result shl 8) or readByte()
        }
        return result
    }
    private fun readLong(): Long {
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or readByte().toLong()
        }
        return result
    }
    private fun readDouble(): Double = java.lang.Double.longBitsToDouble(readLong())
    
    data class CallFrame(
        val bytecode: ByteArray,
        val ip: Int,
        val locals: Array<Any?>
    )
}
