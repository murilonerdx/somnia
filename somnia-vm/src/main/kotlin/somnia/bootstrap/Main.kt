package somnia.bootstrap

import java.io.File

/**
 * Main entry point for the Somnia Bootstrap VM
 * 
 * Usage:
 *   ./somnia run <file.somnia>     - Run a .somnia file
 *   ./somnia test <file.somnia>    - Run tests in a .somnia file
 *   ./somnia repl                  - Start interactive REPL
 */
fun main(args: Array<String>) {
    println()
    println("╔═══════════════════════════════════════════════════════════════╗")
    println("║     SOMNIA BOOTSTRAP VM v1.0.0                                ║")
    println("║     Self-Hosted Core Runtime                                   ║")
    println("╚═══════════════════════════════════════════════════════════════╝")
    println()
    
    when {
        args.isEmpty() -> {
            printUsage()
        }
        args[0] == "run" && args.size >= 2 -> {
            runFile(args[1])
        }
        args[0] == "test" && args.size >= 2 -> {
            testFile(args[1])
        }
        args[0] == "repl" -> {
            repl()
        }
        args[0] == "test-core" -> {
            testCore()
        }
        else -> {
            // Assume it's a file path
            if (args[0].endsWith(".somnia")) {
                runFile(args[0])
            } else {
                printUsage()
            }
        }
    }
}

fun printUsage() {
    println("Usage:")
    println("  somnia run <file.somnia>     - Run a .somnia file")
    println("  somnia test <file.somnia>    - Run tests in a .somnia file")
    println("  somnia test-core             - Run core library tests")
    println("  somnia repl                  - Start interactive REPL")
    println()
}

fun runFile(path: String) {
    val file = File(path)
    if (!file.exists()) {
        println("Error: File not found: $path")
        return
    }
    
    println("[RUN] $path")
    println()
    
    try {
        val source = file.readText()
        val lexer = SomniaLexer(source)
        val tokens = lexer.scanTokens()
        val parser = SomniaParser(tokens)
        val statements = parser.parse()
        val interpreter = SomniaInterpreter()
        
        interpreter.interpret(statements, file.absolutePath)
        
        println()
        println("[DONE] Execution complete")
    } catch (e: Exception) {
        println("[ERROR] ${e.message}")
        e.printStackTrace()
    }
}

fun testFile(path: String) {
    val file = File(path)
    if (!file.exists()) {
        println("Error: File not found: $path")
        return
    }
    
    println("[TEST] $path")
    println()
    
    try {
        val source = file.readText()
        val lexer = SomniaLexer(source)
        val tokens = lexer.scanTokens()
        val parser = SomniaParser(tokens)
        val statements = parser.parse()
        val interpreter = SomniaInterpreter()
        
        // Parse and register tests
        interpreter.interpret(statements, file.absolutePath)
        
        // Run tests
        val (passed, failed) = interpreter.runTests()
        
        println()
        println("═══════════════════════════════════════════════════════════════")
        if (failed == 0) {
            println("ALL TESTS PASSED: $passed/$passed")
        } else {
            println("TESTS FAILED: $passed/${passed + failed} passed, $failed failed")
        }
        
    } catch (e: Exception) {
        println("[ERROR] ${e.message}")
        e.printStackTrace()
    }
}

fun testCore() {
    println("[TEST-CORE] Running core library tests")
    println()
    
    val coreDir = File("../somnia-core/lib")
    val testRunner = File("../somnia-core/test/test_runner.somnia")
    
    if (!coreDir.exists()) {
        println("Error: Core library not found at ${coreDir.absolutePath}")
        println("Make sure you're running from the somnia-vm directory")
        return
    }
    
    if (!testRunner.exists()) {
        println("Warning: Test runner not found, running individual module tests")
        
        // Run tests from each module
        val modules = listOf(
            "types.somnia",
            "proposal.somnia", 
            "condition.somnia",
            "context.somnia",
            "rule.somnia",
            "id_engine.somnia",
            "ego.somnia",
            "act.somnia",
            "runtime.somnia",
            "ffi.somnia"
        )
        
        var totalPassed = 0
        var totalFailed = 0
        
        // With recursive imports, we only need to load the index or the modules
        // that contain the tests.
        val file = File(coreDir, "index.somnia")
        if (file.exists()) {
            try {
                val source = file.readText()
                val lexer = SomniaLexer(source)
                val tokens = lexer.scanTokens()
                val parser = SomniaParser(tokens)
                val statements = parser.parse()
                val interpreter = SomniaInterpreter()
                
                interpreter.interpret(statements, file.absolutePath)
                val (passed, failed) = interpreter.runTests()
                
                totalPassed += passed
                totalFailed += failed
            } catch (e: Exception) {
                println("✗ index.somnia: Error - ${e.message}")
                e.printStackTrace()
                totalFailed++
            }
        }
        
        println()
        println("═══════════════════════════════════════════════════════════════")
        if (totalFailed == 0) {
            println("ALL CORE TESTS PASSED: $totalPassed tests")
        } else {
            println("CORE TESTS FAILED: $totalPassed passed, $totalFailed failed")
        }
        
    } else {
        testFile(testRunner.absolutePath)
    }
}

fun repl() {
    println("REPL Mode - Type 'exit' to quit")
    println()
    
    val interpreter = SomniaInterpreter()
    
    while (true) {
        print("somnia> ")
        val line = readLine() ?: break
        
        if (line.trim() == "exit" || line.trim() == "quit") {
            println("Goodbye!")
            break
        }
        
        if (line.isBlank()) continue
        
        try {
            val lexer = SomniaLexer(line)
            val tokens = lexer.scanTokens()
            val parser = SomniaParser(tokens)
            val statements = parser.parse()
            
            for (stmt in statements) {
                interpreter.interpret(listOf(stmt))
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
