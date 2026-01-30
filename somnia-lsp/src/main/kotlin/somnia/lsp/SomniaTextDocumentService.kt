package somnia.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

import somnia.lang.Lexer
import somnia.lang.Parser

class SomniaTextDocumentService(
    private val store: DocumentStore,
    private val index: WorkspaceIndex
) : TextDocumentService {

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val text = params.textDocument.text
        store.open(uri, text)
        updateIndex(uri, text)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        val text = params.contentChanges.firstOrNull()?.text ?: return
        store.change(uri, text)
        updateIndex(uri, text)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = params.textDocument.uri
        store.close(uri)
        index.remove(uri)
    }

    private fun updateIndex(uri: String, text: String) {
        try {
            val tokens = Lexer(text).tokenizeWithSpans()
            val program = Parser(tokens.map { it.token }).parse()
            index.update(
                uri, 
                program.act.entities, 
                program.act.repositories,
                program.act.actions,
                program.act.intents
            )
            
            // Re-validate
            val diagnostics = Validator.validate(tokens, index)
            store.publish(uri, diagnostics)
        } catch (e: Exception) {
            // If parsing fails, we could clear diagnostics or report syntax error
            // For now, simple fail-safe
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams) {}

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val uri = params.textDocument.uri
        val doc = store.get(uri) ?: return CompletableFuture.completedFuture(Either.forLeft(emptyList()))

        val offset = LspUtil.offsetAt(doc.text, params.position)
        val ctx = CompletionContextDetector.detect(doc.text, offset)

        val defaultProposals = listOf("outline", "style", "step", "action", "plan", "narrative")

        val items: List<CompletionItem> = when (ctx) {
            CompletionCtx.DRIVE_ARG -> doc.index.drives.keys.map { it.item("drive") }
            CompletionCtx.AFFECT_ARG -> doc.index.affects.keys.map { it.item("affect") }
            CompletionCtx.INTENT_STRING -> doc.index.intents.keys.map { it.item("intent") }
            CompletionCtx.RUN_TARGET -> doc.index.actions.keys.map { it.item("action") }
            CompletionCtx.PROPOSAL_NAME -> (
                (doc.index.proposals.keys + defaultProposals).distinct()
            ).map { it.item("proposal") }
            else -> emptyList()
        }

        return CompletableFuture.completedFuture(Either.forLeft(items))
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val uri = params.textDocument.uri
        val doc = store.get(uri) ?: return CompletableFuture.completedFuture(Either.forLeft(emptyList()))

        val offset = LspUtil.offsetAt(doc.text, params.position)

        // 1) se cursor estiver em string literal de intent("..."), navegar para intent "..."
        val intentStr = LspUtil.intentStringAt(doc.tokens, offset)
        if (intentStr != null) {
            val declSpan = doc.index.intents[intentStr]
            if (declSpan != null) {
                val loc = Location(uri, LspUtil.spanToRange(declSpan))
                return CompletableFuture.completedFuture(Either.forLeft(listOf(loc)))
            }
            return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        }

        val word = LspUtil.extractWordAt(doc.text, offset) ?: return CompletableFuture.completedFuture(Either.forLeft(emptyList()))

        val span = doc.index.drives[word]
            ?: doc.index.affects[word]
            ?: doc.index.actions[word]
            ?: doc.index.proposals[word]
            ?: doc.index.intents[word]

        if (span == null) return CompletableFuture.completedFuture(Either.forLeft(emptyList()))

        val loc = Location(uri, LspUtil.spanToRange(span))
        return CompletableFuture.completedFuture(Either.forLeft(listOf(loc)))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        val uri = params.textDocument.uri
        val doc = store.get(uri) ?: return CompletableFuture.completedFuture(Hover())

        val offset = LspUtil.offsetAt(doc.text, params.position)

        val intentStr = LspUtil.intentStringAt(doc.tokens, offset)
        if (intentStr != null) {
            val contents = MarkupContent("markdown", "```somni\nintent \"$intentStr\"\n```")
            return CompletableFuture.completedFuture(Hover(contents))
        }

        val word = LspUtil.extractWordAt(doc.text, offset) ?: return CompletableFuture.completedFuture(Hover())

        val kind = when {
            doc.index.drives.containsKey(word) -> "drive"
            doc.index.affects.containsKey(word) -> "affect"
            doc.index.actions.containsKey(word) -> "action"
            doc.index.intents.containsKey(word) -> "intent"
            doc.index.proposals.containsKey(word) -> "proposal"
            else -> null
        } ?: return CompletableFuture.completedFuture(Hover())

        val contents = MarkupContent("markdown", "```somni\n$kind $word\n```")
        return CompletableFuture.completedFuture(Hover(contents))
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val uri = params.textDocument.uri
        val doc = store.get(uri) ?: return CompletableFuture.completedFuture(SemanticTokens(listOf()))
        val data = SomniaSemanticTokens.build(doc.tokens)
        return CompletableFuture.completedFuture(SemanticTokens(data))
    }
}

private fun String.item(detail: String): CompletionItem =
    CompletionItem(this).apply {
        kind = CompletionItemKind.Value
        this.detail = detail
        insertText = this@item
    }

private enum class CompletionCtx { DRIVE_ARG, AFFECT_ARG, INTENT_STRING, RUN_TARGET, PROPOSAL_NAME, NONE }

private object CompletionContextDetector {
    fun detect(text: String, offset: Int): CompletionCtx {
        val lookback = text.substring(0, offset.coerceIn(0, text.length)).takeLast(80)

        if (Regex("""intent\s*\(\s*"[^"]*$""").containsMatchIn(lookback)) return CompletionCtx.INTENT_STRING
        if (Regex("""drive\s*\(\s*[A-Za-z0-9_]*$""").containsMatchIn(lookback)) return CompletionCtx.DRIVE_ARG
        if (Regex("""affect\s*\(\s*[A-Za-z0-9_]*$""").containsMatchIn(lookback)) return CompletionCtx.AFFECT_ARG
        if (Regex("""run\s+[A-Za-z0-9_]*$""").containsMatchIn(lookback)) return CompletionCtx.RUN_TARGET
        if (Regex("""propose\s+[A-Za-z0-9_]*$""").containsMatchIn(lookback)) return CompletionCtx.PROPOSAL_NAME

        return CompletionCtx.NONE
    }
}
