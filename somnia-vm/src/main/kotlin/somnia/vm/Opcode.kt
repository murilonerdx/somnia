package somnia.vm

/**
 * Somnia Virtual Machine Opcodes
 * Stack-based instruction set inspired by JVM and Python bytecode.
 */
enum class Opcode(val code: Int) {
    // Constants
    NOP(0x00),
    CONST_NULL(0x01),
    CONST_TRUE(0x02),
    CONST_FALSE(0x03),
    CONST_INT(0x04),      // followed by 4 bytes (int32)
    CONST_LONG(0x05),     // followed by 8 bytes (int64)
    CONST_DOUBLE(0x06),   // followed by 8 bytes (float64)
    CONST_STRING(0x07),   // followed by 2 bytes (pool index)
    
    // Stack Operations
    POP(0x10),
    DUP(0x11),
    SWAP(0x12),
    
    // Local Variables
    LOAD_LOCAL(0x20),     // followed by 1 byte (local index)
    STORE_LOCAL(0x21),    // followed by 1 byte (local index)
    LOAD_GLOBAL(0x22),    // followed by 2 bytes (name index in pool)
    STORE_GLOBAL(0x23),   // followed by 2 bytes (name index in pool)
    
    // Arithmetic (pop 2, push result)
    ADD(0x30),
    SUB(0x31),
    MUL(0x32),
    DIV(0x33),
    MOD(0x34),
    NEG(0x35),            // negate top of stack
    
    // Comparison (pop 2, push boolean)
    EQ(0x40),
    NE(0x41),
    LT(0x42),
    LE(0x43),
    GT(0x44),
    GE(0x45),
    
    // Logic
    AND(0x50),
    OR(0x51),
    NOT(0x52),
    
    // Control Flow
    JUMP(0x60),           // followed by 2 bytes (offset)
    JUMP_IF_TRUE(0x61),   // followed by 2 bytes (offset)
    JUMP_IF_FALSE(0x62),  // followed by 2 bytes (offset)
    
    // Functions
    CALL(0x70),           // followed by 2 bytes (func index), 1 byte (arg count)
    CALL_NATIVE(0x71),    // followed by 2 bytes (native func index), 1 byte (arg count)
    RETURN(0x72),
    RETURN_VOID(0x73),
    
    // Objects (future)
    NEW_OBJECT(0x80),     // followed by 2 bytes (class index)
    GET_FIELD(0x81),      // followed by 2 bytes (field name index)
    SET_FIELD(0x82),      // followed by 2 bytes (field name index)
    
    // Arrays (future)
    NEW_ARRAY(0x90),
    ARRAY_GET(0x91),
    ARRAY_SET(0x92),
    ARRAY_LEN(0x93),
    
    // I/O (native bridges)
    PRINT(0xA0),
    PRINTLN(0xA1),
    READ_LINE(0xA2),
    
    // Special
    HALT(0xFF);
    
    companion object {
        private val codeMap = values().associateBy { it.code }
        fun fromCode(code: Byte): Opcode? = codeMap[code.toInt() and 0xFF]
    }
}

