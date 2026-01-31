package somnia.vm

import somnia.lang.Lexer
import somnia.lang.Parser
import java.io.File
import java.io.FileOutputStream

/**
 * Somnia SDK - Command Line Tools
 * 
 * Commands:
 *   somniac compile <file.somni> [-o output.sbc]  - Compile to bytecode
 *   svm run <file.sbc>                             - Execute bytecode
 *   somnia run <file.somni>                        - Compile and run (interpreter mode)
 */
object SomniaSDK {
    
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }
        
        val command = args[0]
        when (command) {
            "compile", "c" -> handleCompile(args.drop(1))
            "run", "r" -> handleRun(args.drop(1))
            "exec", "x" -> handleExec(args.drop(1))
            "version", "v" -> printVersion()
            "help", "h" -> printUsage()
            else -> {
                println("Unknown command: $command")
                printUsage()
            }
        }
    }
    
    private fun handleCompile(args: List<String>) {
        if (args.isEmpty()) {
            println("Error: No input file specified")
            println("Usage: somniac compile <file.somni> [-o output.sbc]")
            return
        }
        
        val inputPath = args[0]
        val outputPath = if (args.size >= 3 && args[1] == "-o") {
            args[2]
        } else {
            inputPath.replace(".somni", ".sbc")
        }
        
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            println("Error: File not found: $inputPath")
            return
        }
        
        try {
            val source = inputFile.readText()
            val tokens = Lexer(source).tokenize()
            val program = Parser(tokens).parse()
            
            val compiler = Compiler()
            val bcFile = compiler.compile(program)
            
            FileOutputStream(outputPath).use { out ->
                bcFile.write(out)
            }
            
            println("[somniac] Compiled successfully: $outputPath")
            println("  Constants: ${bcFile.constantPool.size}")
            println("  Functions: ${bcFile.functions.size}")
            
        } catch (e: Exception) {
            println("[somniac] Compilation failed: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleRun(args: List<String>) {
        if (args.isEmpty()) {
            println("Error: No input file specified")
            println("Usage: svm run <file.sbc>")
            return
        }
        
        val inputPath = args[0]
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            println("Error: File not found: $inputPath")
            return
        }
        
        try {
            val bcFile = BytecodeFile()
            inputFile.inputStream().use { inp ->
                bcFile.read(inp)
            }
            
            val vm = SomniaVM()
            vm.load(bcFile)
            
            println("[SVM v0.1] Executing: $inputPath")
            val result = vm.execute()
            if (result != null) {
                println("[SVM] Result: $result")
            }
            
        } catch (e: Exception) {
            println("[SVM] Runtime error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleExec(args: List<String>) {
        // Compile and run in one step
        if (args.isEmpty()) {
            println("Error: No input file specified")
            println("Usage: somnia exec <file.somni>")
            return
        }
        
        val inputPath = args[0]
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            println("Error: File not found: $inputPath")
            return
        }
        
        try {
            val source = inputFile.readText()
            val tokens = Lexer(source).tokenize()
            val program = Parser(tokens).parse()
            
            val compiler = Compiler()
            val bcFile = compiler.compile(program)
            
            val vm = SomniaVM()
            vm.load(bcFile)
            
            println("[Somnia] Running: $inputPath")
            val result = vm.execute()
            if (result != null) {
                println("[Somnia] Result: $result")
            }
            
        } catch (e: Exception) {
            println("[Somnia] Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun printVersion() {
        println("""
            Somnia SDK v0.1.0
            ─────────────────────────────────
            somniac  - Somnia Compiler
            svm      - Somnia Virtual Machine
            
            The Psychological Architecture
        """.trimIndent())
    }
    
    private fun printUsage() {
        println("""
            Somnia SDK - The Complete Development Kit
            
            Usage:
              somnia compile <file.somni> [-o output.sbc]  Compile to bytecode
              somnia run <file.sbc>                        Execute bytecode
              somnia exec <file.somni>                     Compile and run
              somnia version                               Show version
              somnia help                                  Show this help
            
            Aliases:
              c = compile, r = run, x = exec, v = version, h = help
        """.trimIndent())
    }
}
