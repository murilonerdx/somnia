package somnia.lang

data class Span(
    val startLine: Int,
    val startCol: Int,
    val endLine: Int,
    val endCol: Int,
    val startOffset: Int,
    val endOffset: Int
)

data class PositionedToken(val token: Token, val span: Span)
