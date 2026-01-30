package somnia.lsp

import somnia.lang.PositionedToken
import somnia.lang.TokenType
import somnia.lang.SomniaProgram
import somnia.lang.Span

data class Index(
    val drives: Map<String, Span>,
    val affects: Map<String, Span>,
    val actions: Map<String, Span>,
    val intents: Map<String, Span>,
    val proposals: Map<String, Span>
) {
    companion object {
        fun empty() = Index(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

        fun fromTokens(tokens: List<PositionedToken>): Index = fromRawTokens(tokens)

        fun fromProgramAndTokens(program: SomniaProgram, tokens: List<PositionedToken>): Index =
            fromRawTokens(tokens)

        private fun fromRawTokens(tokens: List<PositionedToken>): Index {
            val drives = mutableMapOf<String, Span>()
            val affects = mutableMapOf<String, Span>()
            val actions = mutableMapOf<String, Span>()
            val intents = mutableMapOf<String, Span>()
            val proposals = mutableMapOf<String, Span>()

            var i = 0
            while (i < tokens.size - 1) {
                val t = tokens[i]
                val n = tokens[i + 1]

                when (t.token.type) {
                    TokenType.KEYWORD_DRIVE ->
                        if (n.token.type == TokenType.IDENTIFIER) drives[n.token.literal] = n.span

                    TokenType.KEYWORD_AFFECT ->
                        if (n.token.type == TokenType.IDENTIFIER) affects[n.token.literal] = n.span

                    TokenType.KEYWORD_ACTION ->
                        if (n.token.type == TokenType.IDENTIFIER) actions[n.token.literal] = n.span

                    // declaração: intent "name"
                    TokenType.KEYWORD_INTENT ->
                        if (n.token.type == TokenType.STRING_LITERAL) intents[n.token.literal] = n.span

                    // propose <IDENT>   (ou propose "x" se você quiser permitir)
                    TokenType.KEYWORD_PROPOSE ->
                        if (n.token.type == TokenType.IDENTIFIER || n.token.type == TokenType.STRING_LITERAL) {
                            proposals[n.token.literal] = n.span
                        }

                    else -> {}
                }
                i++
            }

            return Index(drives, affects, actions, intents, proposals)
        }
    }
}
