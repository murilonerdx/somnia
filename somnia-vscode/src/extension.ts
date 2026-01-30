import * as vscode from "vscode";
import * as path from "path";
import { LanguageClient, LanguageClientOptions, ServerOptions } from "vscode-languageclient/node";

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext) {
    const jarPath = context.asAbsolutePath(path.join("server", "somnia-lsp-all.jar"));

    const serverOptions: ServerOptions = {
        command: "java",
        args: ["-jar", jarPath],
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: "file", language: "somni" }],
    };

    client = new LanguageClient("somniaLsp", "Somnia LSP", serverOptions, clientOptions);
    context.subscriptions.push(client.start());
}

export async function deactivate() {
    if (client) await client.stop();
}
