package somnia.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(name = "fmt", description = ["Format Somnia source files"])
class FmtCommand : Callable<Int> {
    
    @Parameters(index = "0", description = ["File to format"])
    lateinit var file: File

    override fun call(): Int {
        if (!file.exists()) {
            println("Error: File ${file.path} not found.")
            return 1
        }

        println("Formatting ${file.name}...")
        val lines = file.readLines()
        val formatted = formatLines(lines)
        file.writeText(formatted.joinToString("\n"))
        println("Done.")
        return 0
    }

    private fun formatLines(lines: List<String>): List<String> {
        var indent = 0
        return lines.map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("}") || trimmed.startsWith("]")) indent--
            val res = "  ".repeat(indent.coerceAtLeast(0)) + trimmed
            if (trimmed.endsWith("{") || trimmed.endsWith("[")) indent++
            res
        }
    }
}
