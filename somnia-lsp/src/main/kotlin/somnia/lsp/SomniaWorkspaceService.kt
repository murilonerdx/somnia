package somnia.lsp

import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams

class SomniaWorkspaceService : WorkspaceService {
    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}
    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {}
}
