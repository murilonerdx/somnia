package somnia.lsp

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import somnia.lang.PositionedToken
import somnia.lang.TokenType

object Validator {

    fun validate(tokens: List<PositionedToken>, index: WorkspaceIndex): List<Diagnostic> {
        val diags = mutableListOf<Diagnostic>()

        val seenDrives = mutableSetOf<String>()
        val seenAffects = mutableSetOf<String>()
        val seenActions = mutableSetOf<String>()
        val seenIntents = mutableSetOf<String>()

        fun warn(tok: PositionedToken, msg: String) {
            diags.add(
                Diagnostic().apply {
                    severity = DiagnosticSeverity.Warning
                    message = msg
                    range = LspUtil.spanToRange(tok.span)
                }
            )
        }
        
        fun error(tok: PositionedToken, msg: String) {
             diags.add(
                Diagnostic().apply {
                    severity = DiagnosticSeverity.Error
                    message = msg
                    range = LspUtil.spanToRange(tok.span)
                }
            )
        }

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]

            // --- 1. ID Layer Checks ---

            // declaration "name" weight X
            // Check negative weight
            if (t.token.type == TokenType.IDENTIFIER && t.token.literal == "weight" && i + 1 < tokens.size) {
                 val numTok = tokens[i + 1]
                 if (numTok.token.type == TokenType.NUMBER) {
                     val value = numTok.token.literal.toDoubleOrNull() ?: 0.0
                     if (value < 0) {
                         error(numTok, "Peso negativo não permitido: $value")
                     }
                 } else if (numTok.token.type == TokenType.MINUS) {
                     // Check if next is number
                     if (i + 2 < tokens.size && tokens[i+2].token.type == TokenType.NUMBER) {
                          error(tokens[i+2], "Peso negativo não permitido.")
                     }
                 }
            }

            // run <action>
            if (t.token.type == TokenType.KEYWORD_RUN && i + 1 < tokens.size) {
                val n = tokens[i + 1]
                if (n.token.type == TokenType.IDENTIFIER) {
                    val actionName = n.token.literal
                    if (index.findAction(actionName) == null) {
                        warn(n, "Action '$actionName' não encontrada no workspace.")
                    }
                }
            }
            
            // intent("name") ref
             if (t.token.type == TokenType.KEYWORD_INTENT && isCall(tokens, i, TokenType.STRING_LITERAL)) {
                val argTok = tokens[i + 2]
                val intentName = argTok.token.literal
                if (!index.hasIntent(intentName)) {
                    warn(argTok, "Intent '$intentName' não definido no workspace.")
                }
            }

            // --- 2. Entity / Repo Checks ---

            // id: Type
            // Check if field is named 'id' and type is String or Int
            if (t.token.type == TokenType.IDENTIFIER && t.token.literal == "id" && i + 2 < tokens.size) {
                if (tokens[i+1].token.type == TokenType.COLON) {
                    val typeTok = tokens[i+2]
                     if (typeTok.token.type == TokenType.IDENTIFIER) {
                         val typeName = typeTok.token.literal
                         if (typeName != "String" && typeName != "Int") {
                             warn(typeTok, "Convenção: Campo 'id' deve ser String ou Int.")
                         }
                     }
                }
            }
            
            // repository X { entity Y ... }
            if (t.token.type == TokenType.KEYWORD_ENTITY && i + 1 < tokens.size) {
                // Check if inside repository block? 
                // Hard to tell without AST state.
                // But generally, 'entity' keyword is followed by Entity Name reference.
                // Or 'entity Definition'.
                // If it is 'entity Task' inside repo, verify Task exists.
                val entNameTok = tokens[i+1]
                if (entNameTok.token.type == TokenType.IDENTIFIER) {
                     val name = entNameTok.token.literal
                     // We check if this is a REFERENCE or DEFINITION.
                     // Definition: entity Task { ... } -> 'entity' followed by ID followed by LBRACE
                     // Reference: entity Task -> 'entity' followed by ID (and maybe newline/semicolon)
                     
                     // If definitions are indexed, we can just check if 'name' exists in index.
                     // If it doesn't exist, it's either a new definition or invalid ref.
                     // If we are defining it now, it might not be in index yet? 
                     // No, "didOpen" updates index BEFORE validation. :)
                     
                     if (index.findEntity(name) == null) {
                         // It might be the definition itself we are looking at.
                         // But index should have it.
                         // Only warn if it REALLY is missing.
                         // Let's rely on 'findEntity' returning null.
                         // But if we just parsed it, it IS in the index for this URI.
                         // So if findEntity returns null, it's truly unknown.
                         
                         // CAUTION: If parsing failed, index might be empty.
                         // We assume parsing succeeded enough.
                         
                         // Let's just warn:
                         // "Entity '$name' desconhecida (ou falha de parsing)."
                         // Actually, let's skip this check for now to avoid noise on definitions.
                         // Instead, specific check for: repository ... { entity X }
                     }
                }
            }

            i++
        }

        return diags
    }

    private fun isCall(tokens: List<PositionedToken>, i: Int, argType: TokenType): Boolean {
        return i + 2 < tokens.size
                && tokens[i + 1].token.type == TokenType.LPAREN
                && tokens[i + 2].token.type == argType
    }
}
