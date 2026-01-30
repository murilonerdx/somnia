package somnia.core

/**
 * Legacy Runtime class. 
 * v0.2 modular uses SomniaEngine and ActionInterpreter instead.
 */
class Runtime(val program: somnia.lang.SomniaProgram) {
    fun init() {}
    companion object {
        fun evaluateExpr(expr: somnia.lang.Expr, context: Any?): Any? = null
    }
}
