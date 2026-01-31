package somnia.vm

/**
 * Interface for callable objects in Somnia.
 * Both native functions and user-defined functions implement this.
 */
interface SomniaCallable {
    val name: String
    val arity: Int  // -1 for variadic
    fun call(vm: SomniaVM, args: List<Any?>): Any?
}
