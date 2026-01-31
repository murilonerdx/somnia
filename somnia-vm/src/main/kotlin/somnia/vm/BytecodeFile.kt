package somnia.vm

import java.io.*

/**
 * Somnia Bytecode File Format (.sbc)
 * 
 * Structure:
 * - Magic: 4 bytes "SOMN"
 * - Version: 2 bytes (major.minor)
 * - Constant Pool Size: 2 bytes
 * - Constant Pool: variable
 * - Function Count: 2 bytes
 * - Functions: variable
 * - Entry Point: 2 bytes (function index)
 */
class BytecodeFile {
    companion object {
        val MAGIC = byteArrayOf('S'.code.toByte(), 'O'.code.toByte(), 'M'.code.toByte(), 'N'.code.toByte())
        const val VERSION_MAJOR: Byte = 0
        const val VERSION_MINOR: Byte = 1
    }
    
    val constantPool = mutableListOf<Constant>()
    val functions = mutableListOf<SFunction>()
    var entryPoint: Int = 0
    
    fun addConstant(value: Any?): Int {
        val constant = when (value) {
            null -> Constant.Null
            is Boolean -> if (value) Constant.True else Constant.False
            is Int -> Constant.IntVal(value)
            is Long -> Constant.LongVal(value)
            is Double -> Constant.DoubleVal(value)
            is String -> Constant.StringVal(value)
            else -> throw IllegalArgumentException("Unsupported constant type: ${value::class}")
        }
        val existing = constantPool.indexOf(constant)
        if (existing >= 0) return existing
        constantPool.add(constant)
        return constantPool.size - 1
    }
    
    fun write(output: OutputStream) {
        val dos = DataOutputStream(output)
        
        // Magic
        dos.write(MAGIC)
        
        // Version
        dos.writeByte(VERSION_MAJOR.toInt())
        dos.writeByte(VERSION_MINOR.toInt())
        
        // Constant Pool
        dos.writeShort(constantPool.size)
        for (c in constantPool) {
            c.writeTo(dos)
        }
        
        // Functions
        dos.writeShort(functions.size)
        for (f in functions) {
            f.writeTo(dos)
        }
        
        // Entry Point
        dos.writeShort(entryPoint)
        
        dos.flush()
    }
    
    fun read(input: InputStream): BytecodeFile {
        val dis = DataInputStream(input)
        
        // Magic
        val magic = ByteArray(4)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw IllegalArgumentException("Invalid magic number")
        }
        
        // Version
        val major = dis.readByte()
        val minor = dis.readByte()
        
        // Constant Pool
        val poolSize = dis.readUnsignedShort()
        repeat(poolSize) {
            constantPool.add(Constant.readFrom(dis))
        }
        
        // Functions
        val funcCount = dis.readUnsignedShort()
        repeat(funcCount) {
            functions.add(SFunction.readFrom(dis))
        }
        
        // Entry Point
        entryPoint = dis.readUnsignedShort()
        
        return this
    }
}

sealed class Constant {
    object Null : Constant()
    object True : Constant()
    object False : Constant()
    data class IntVal(val value: Int) : Constant()
    data class LongVal(val value: Long) : Constant()
    data class DoubleVal(val value: Double) : Constant()
    data class StringVal(val value: String) : Constant()
    
    fun writeTo(dos: DataOutputStream) {
        when (this) {
            Null -> dos.writeByte(0)
            True -> dos.writeByte(1)
            False -> dos.writeByte(2)
            is IntVal -> { dos.writeByte(3); dos.writeInt(value) }
            is LongVal -> { dos.writeByte(4); dos.writeLong(value) }
            is DoubleVal -> { dos.writeByte(5); dos.writeDouble(value) }
            is StringVal -> { dos.writeByte(6); dos.writeUTF(value) }
        }
    }
    
    companion object {
        fun readFrom(dis: DataInputStream): Constant {
            return when (dis.readByte().toInt()) {
                0 -> Null
                1 -> True
                2 -> False
                3 -> IntVal(dis.readInt())
                4 -> LongVal(dis.readLong())
                5 -> DoubleVal(dis.readDouble())
                6 -> StringVal(dis.readUTF())
                else -> throw IllegalArgumentException("Unknown constant type")
            }
        }
    }
}

data class SFunction(
    val name: String,
    val paramCount: Int,
    val localCount: Int,
    val bytecode: ByteArray
) {
    fun writeTo(dos: DataOutputStream) {
        dos.writeUTF(name)
        dos.writeByte(paramCount)
        dos.writeByte(localCount)
        dos.writeInt(bytecode.size)
        dos.write(bytecode)
    }
    
    companion object {
        fun readFrom(dis: DataInputStream): SFunction {
            val name = dis.readUTF()
            val paramCount = dis.readUnsignedByte()
            val localCount = dis.readUnsignedByte()
            val codeSize = dis.readInt()
            val bytecode = ByteArray(codeSize)
            dis.readFully(bytecode)
            return SFunction(name, paramCount, localCount, bytecode)
        }
    }
}
