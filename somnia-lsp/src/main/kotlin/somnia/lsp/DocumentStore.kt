package somnia.lsp

import somnia.lang.Lexer
import somnia.lang.Parser
import somnia.lang.PositionedToken
import somnia.lang.SomniaProgram
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range

class DocumentStore(
    private var publisher: (String, List<Diagnostic>) -> Unit
) {
    private val docs = mutableMapOf<String, DocState>()

    fun setClientPublisher(p: (String, List<Diagnostic>) -> Unit) { publisher = p }

    fun open(uri: String, text: String) = upsert(uri, text)
    fun change(uri: String, text: String) = upsert(uri, text)
    fun close(uri: String) { docs.remove(uri); publisher(uri, emptyList()) }
    fun publish(uri: String, diags: List<Diagnostic>) = publisher(uri, diags)

    fun get(uri: String): DocState? = docs[uri]

    private fun upsert(uri: String, text: String) {
        val parsed = parse(text)
        docs[uri] = parsed
        publisher(uri, parsed.diagnostics)
    }

    private fun parse(text: String): DocState {
        val positionedTokens: List<PositionedToken> = try {
            Lexer(text).tokenizeWithSpans()
        } catch (e: Exception) {
            val diag = Diagnostic().apply {
                severity = DiagnosticSeverity.Error
                message = e.message ?: "Lexer error"
                range = Range(LspUtil.pos0(0, 0), LspUtil.pos0(0, 1))
            }
            return DocState(text, emptyList(), null, listOf(diag), Index.empty())
        }

        val tokens = positionedTokens.map { it.token }

        val program: SomniaProgram? = try {
            Parser(tokens).parse()
        } catch (e: Exception) {
            val (line, msg) = LspUtil.extractLineFromError(e.message)
            val diag = Diagnostic().apply {
                severity = DiagnosticSeverity.Error
                message = msg
                range = Range(
                    LspUtil.pos0((line - 1).coerceAtLeast(0), 0),
                    LspUtil.pos0((line - 1).coerceAtLeast(0), 200)
                )
            }
            val idx = Index.fromTokens(positionedTokens)
            return DocState(text, positionedTokens, null, listOf(diag), idx)
        }

        val idx = Index.fromProgramAndTokens(program!!, positionedTokens)
        // Validation is now handled by SomniaTextDocumentService using WorkspaceIndex
        val warnings = emptyList<Diagnostic>()

        return DocState(text, positionedTokens, program, warnings, idx)
    }
}

data class DocState(
    val text: String,
    val tokens: List<PositionedToken>,
    val program: SomniaProgram?,
    val diagnostics: List<Diagnostic>,
    val index: Index
)
