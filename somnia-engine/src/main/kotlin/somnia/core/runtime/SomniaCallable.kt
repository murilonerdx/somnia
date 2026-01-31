package somnia.core.runtime

/**
 * Interface for callable objects in Somnia (native functions, user-defined functions).
 */
interface SomniaCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}
