package somnia.lsp

import org.eclipse.lsp4j.launch.LSPLauncher

fun main() {
    val server = SomniaLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
    val client = launcher.remoteProxy
    server.connect(client)
    launcher.startListening().get()
}
