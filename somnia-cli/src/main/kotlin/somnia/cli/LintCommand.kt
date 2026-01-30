package somnia.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import somnia.lang.Lexer
import somnia.lang.Parser
import somnia.lang.Token
import java.io.File
import java.util.concurrent.Callable

@Command(name = "lint", description = ["Check Somnia source files for errors"])
class LintCommand : Callable<Int> {

    @Parameters(index = "0", description = ["File to lint"])
    lateinit var file: File

    override fun call(): Int {
        if (!file.exists()) {
            println("Error: File ${file.path} not found.")
            return 1
        }

        println("Linting ${file.name}...")
        
        try {
            val source = file.readText()
            val lexer = Lexer(source)
            val tokens = lexer.tokenize()
            val parser = Parser(tokens)
            parser.parse()
            
            println("\u001B[32m[OK] No syntax errors found.\u001B[0m")
            return 0
        } catch (e: Exception) {
            println("\u001B[31m[FAIL] Error in ${file.name}: ${e.message}\u001B[0m")
            return 1
        }
    }
}
