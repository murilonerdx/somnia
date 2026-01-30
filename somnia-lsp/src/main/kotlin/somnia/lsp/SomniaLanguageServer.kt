package somnia.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.jsonrpc.messages.Either

class SomniaLanguageServer : LanguageServer, LanguageClientAware {

    private var client: LanguageClient? = null
    private val docs = DocumentStore { uri, diags -> client?.publishDiagnostics(PublishDiagnosticsParams(uri, diags)) }
    private val index = WorkspaceIndex()

    private val textDocumentService = SomniaTextDocumentService(docs, index)
    private val workspaceService = SomniaWorkspaceService()

    override fun connect(client: LanguageClient) {
        this.client = client
        docs.setClientPublisher { uri, diags ->
            client.publishDiagnostics(PublishDiagnosticsParams(uri, diags))
        }
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities()
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
        capabilities.setCompletionProvider(CompletionOptions(false, listOf(".")))
        capabilities.setDefinitionProvider(true)
        // Enable workspace folder support if needed, but for now we primarily rely on open files

        capabilities.hoverProvider = Either.forLeft(true)

        capabilities.semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
            legend = SomniaSemanticTokens.legend
            full = Either.forLeft(true)
            range = Either.forLeft(false)
        }

        val result = InitializeResult(capabilities)
        return CompletableFuture.completedFuture(result)
    }

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(Any())
    override fun exit() {}

    override fun getTextDocumentService(): TextDocumentService = textDocumentService
    override fun getWorkspaceService(): WorkspaceService = workspaceService
}
