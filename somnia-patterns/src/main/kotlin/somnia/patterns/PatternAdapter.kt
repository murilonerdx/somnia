package somnia.patterns

import somnia.lang.*

interface PatternAdapter {
    /**
     * Expand the pattern into the Somnia Program AST.
     * This is the "T-Adapter" that transforms high-level definitions 
     * into concrete actions, routes, and repositories.
     */
    fun expand(program: SomniaProgram, args: List<Expr>)
}
