package somnia.lsp

import somnia.lang.PositionedToken
import somnia.lang.TokenType

object SomniaSemanticTokens {
    // tokenTypes: 0=keyword,1=variable,2=function,3=enumMember,4=string,5=number
    val legend = org.eclipse.lsp4j.SemanticTokensLegend(
        listOf("keyword", "variable", "function", "enumMember", "string", "number"),
        listOf("declaration")
    )

    private const val MOD_DECL = 1 // bit 0

    fun build(tokens: List<PositionedToken>): List<Int> {
        val out = mutableListOf<Int>()

        var lastLine0 = 0
        var lastChar0 = 0

        fun emit(t: PositionedToken, typeIdx: Int, isDecl: Boolean = false) {
            val line0 = t.span.startLine - 1
            val char0 = (t.span.startCol - 1).coerceAtLeast(0)
            val length = (t.span.endOffset - t.span.startOffset).coerceAtLeast(1)

            val deltaLine = line0 - lastLine0
            val deltaStart = if (deltaLine == 0) char0 - lastChar0 else char0

            val mods = if (isDecl) MOD_DECL else 0

            out.add(deltaLine)
            out.add(deltaStart)
            out.add(length)
            out.add(typeIdx)
            out.add(mods)

            lastLine0 = line0
            lastChar0 = char0
        }

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]

            when (t.token.type) {
                TokenType.KEYWORD_ID, TokenType.KEYWORD_EGO, TokenType.KEYWORD_ACT,
                TokenType.KEYWORD_DRIVE, TokenType.KEYWORD_AFFECT, TokenType.KEYWORD_TRACE,
                TokenType.KEYWORD_ARCHETYPE, TokenType.KEYWORD_WHEN, TokenType.KEYWORD_PROPOSE,
                TokenType.KEYWORD_FORBID, TokenType.KEYWORD_REQUIRE, TokenType.KEYWORD_SELECT,
                TokenType.KEYWORD_BEAM, TokenType.KEYWORD_TOP, TokenType.KEYWORD_SAMPLE,
                TokenType.KEYWORD_INTENT, TokenType.KEYWORD_ACTION, TokenType.KEYWORD_RUN,
                TokenType.KEYWORD_EXPOSE, TokenType.KEYWORD_AS -> emit(t, 0)

                TokenType.STRING_LITERAL -> emit(t, 4)
                TokenType.NUMBER -> emit(t, 5)
                else -> {}
            }

            // Highlighting declarations and references based on context
            if (t.token.type == TokenType.KEYWORD_DRIVE && i + 1 < tokens.size && tokens[i + 1].token.type == TokenType.IDENTIFIER) {
                emit(tokens[i + 1], 1, isDecl = true)
            }
            if (t.token.type == TokenType.KEYWORD_AFFECT && i + 1 < tokens.size && tokens[i + 1].token.type == TokenType.IDENTIFIER) {
                emit(tokens[i + 1], 1, isDecl = true)
            }
            if (t.token.type == TokenType.KEYWORD_ACTION && i + 1 < tokens.size && tokens[i + 1].token.type == TokenType.IDENTIFIER) {
                emit(tokens[i + 1], 2, isDecl = true)
            }
            if (t.token.type == TokenType.KEYWORD_INTENT && i + 1 < tokens.size && tokens[i + 1].token.type == TokenType.STRING_LITERAL) {
                emit(tokens[i + 1], 3, isDecl = true)
            }

            // References
            if (t.token.type == TokenType.KEYWORD_DRIVE
                && i + 2 < tokens.size
                && tokens[i + 1].token.type == TokenType.LPAREN
                && tokens[i + 2].token.type == TokenType.IDENTIFIER
            ) {
                emit(tokens[i + 2], 1, isDecl = false)
            }

            if (t.token.type == TokenType.KEYWORD_AFFECT
                && i + 2 < tokens.size
                && tokens[i + 1].token.type == TokenType.LPAREN
                && tokens[i + 2].token.type == TokenType.IDENTIFIER
            ) {
                emit(tokens[i + 2], 1, isDecl = false)
            }

            if (t.token.type == TokenType.KEYWORD_INTENT
                && i + 2 < tokens.size
                && tokens[i + 1].token.type == TokenType.LPAREN
                && tokens[i + 2].token.type == TokenType.STRING_LITERAL
            ) {
                emit(tokens[i + 2], 3, isDecl = false)
            }

            if (t.token.type == TokenType.KEYWORD_RUN && i + 1 < tokens.size && tokens[i + 1].token.type == TokenType.IDENTIFIER) {
                emit(tokens[i + 1], 2, isDecl = false)
            }

            // propose <IDENT|STRING> => function
            if (t.token.type == TokenType.KEYWORD_PROPOSE && i + 1 < tokens.size) {
                val n = tokens[i + 1]
                if (n.token.type == TokenType.IDENTIFIER || n.token.type == TokenType.STRING_LITERAL) {
                    emit(n, 2, isDecl = false) // function
                }
            }

            i++
        }

        return out
    }
}
