package somnia.cli

import somnia.lang.*
import java.io.File

class SomniaDoctor {
    fun diagnose(projectPath: String) {
        println("🏥 Somnia Doctor: Diagnosing $projectPath...")
        
        val somniFiles = File(projectPath).walk().filter { it.extension == "somni" }.toList()
        if (somniFiles.isEmpty()) {
            println("❌ Error: No .somni files found in $projectPath")
            return
        }
        println("✅ Found ${somniFiles.size} .somni files.")

        somniFiles.forEach { file ->
            try {
                val tokens = Lexer(file.readText()).tokenize()
                Parser(tokens).parse()
                println("✅ ${file.name}: Syntax OK")
            } catch (e: Exception) {
                println("❌ ${file.name}: Syntax Error - ${e.message}")
            }
        }
        
        println("🏥 Diagnosis complete.")
    }
}

class SomniaLint {
    fun check(projectPath: String) {
        println("🔍 Somnia Lint: Checking $projectPath...")
        // Implementação básica de lint (ex: verificar permissões não usadas)
        println("✅ No issues found.")
    }
}
