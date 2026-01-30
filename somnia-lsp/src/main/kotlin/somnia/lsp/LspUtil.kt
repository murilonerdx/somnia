package somnia.lsp

import org.eclipse.lsp4j.Position
import somnia.lang.Span
import somnia.lang.PositionedToken
import somnia.lang.TokenType
import java.util.regex.Pattern

object LspUtil {
    fun pos0(line0: Int, col0: Int) = Position(line0, col0)

    fun spanToRange(span: Span): org.eclipse.lsp4j.Range {
        return org.eclipse.lsp4j.Range(
            Position(span.startLine - 1, (span.startCol - 1).coerceAtLeast(0)),
            Position(span.endLine - 1, (span.endCol - 1).coerceAtLeast(0))
        )
    }

    fun offsetAt(text: String, pos: Position): Int {
        val lines = text.split("\n")
        val line = pos.line.coerceIn(0, lines.size.coerceAtLeast(1) - 1)
        val col = pos.character.coerceAtLeast(0)
        var offset = 0
        for (i in 0 until line) offset += lines[i].length + 1
        return offset + col.coerceAtMost(lines[line].length)
    }

    fun extractWordAt(text: String, offset: Int): String? {
        if (text.isEmpty()) return null
        val i = offset.coerceIn(0, text.length)
        val left = text.substring(0, i)
        val right = text.substring(i)

        val leftMatch = Regex("""[A-Za-z0-9_]+$""").find(left)?.value ?: return null
        val rightMatch = Regex("""^[A-Za-z0-9_]+""").find(right)?.value ?: ""
        return leftMatch + rightMatch
    }

    fun extractLineFromError(message: String?): Pair<Int, String> {
        if (message == null) return 1 to "Parse error"
        val p = Pattern.compile("line\\s+(\\d+)")
        val m = p.matcher(message)
        val line = if (m.find()) m.group(1).toInt() else 1
        return line to message
    }

    fun tokenAtOffset(tokens: List<PositionedToken>, offset: Int): PositionedToken? {
        return tokens.firstOrNull { offset >= it.span.startOffset && offset < it.span.endOffset }
    }

    fun intentStringAt(tokens: List<PositionedToken>, offset: Int): String? {
        val tok = tokenAtOffset(tokens, offset) ?: return null
        if (tok.token.type != TokenType.STRING_LITERAL) return null

        val idx = tokens.indexOf(tok)
        if (idx <= 0) return null

        // padrões aceitos:
        // 1) intent "name"      => KEYWORD_INTENT STRING_LITERAL
        // 2) intent("name")     => KEYWORD_INTENT LPAREN STRING_LITERAL
        val prev = tokens[idx - 1].token.type
        if (prev == TokenType.KEYWORD_INTENT) return tok.token.literal

        if (idx >= 2) {
            val prev1 = tokens[idx - 1].token.type
            val prev2 = tokens[idx - 2].token.type
            if (prev1 == TokenType.LPAREN && prev2 == TokenType.KEYWORD_INTENT) return tok.token.literal
        }
        return null
    }
}
