package somnia.spring

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import somnia.lang.*
import somnia.core.*
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.ComponentScan

@AutoConfiguration
class SomniaBootAutoConfiguration(
    private val appCtx: ApplicationContext
) {

    @Bean
    fun somniaProgram(): SomniaProgram {
        val resolver = PathMatchingResourcePatternResolver()
        val resources = try {
            resolver.getResources("classpath*:/somnia/**/*.somni")
        } catch (e: Exception) {
            emptyArray()
        }
        
        var entryResource: org.springframework.core.io.Resource? = null
        var entryContent = ""
        
        // 1. Find explicit entry point with 'app "..."'
        for (res in resources) {
            val content = res.inputStream.bufferedReader().readText()
            // Simple check for 'app "Name"'
            if (content.contains("app \"")) {
                entryResource = res
                entryContent = content
                break
            }
        }
        
        // 2. Fallback to specific 'crud.somni' or first available
        if (entryResource == null && resources.isNotEmpty()) {
             entryResource = resources.find { it.filename == "crud.somni" } ?: resources.first()
             entryContent = entryResource!!.inputStream.bufferedReader().readText()
        }
        
        // 3. Dev Fallback
        if (entryContent.isEmpty()) {
            val devFile = java.io.File("todo.somnia")
            if (devFile.exists()) {
                entryContent = devFile.readText()
                // Fake URI for dev file
                // program.sourcePath will be set below
            } else {
                 throw RuntimeException("No Somnia scripts found!")
            }
        }

        val tokens = Lexer(entryContent).tokenize()
        val program = Parser(tokens).parse()
        
        try {
            if (entryResource != null) {
                // Determine 'baseDir' equivalent from URI
                // If URI is jar:file:/...!/somnia/crud.somni -> resolved by ModuleLoader using classpath fallback
                // We just set the path relative to classpath root 'somnia/'?
                // Or just set identifier. ModuleLoader handles URI parsing?
                // Let's just set the filename or URI.
                // ModuleLoader logic expects 'baseDir' file or classpath relative.
                // If we give it "crud.somni", and baseDir is "somnia/", it works by classpath.
                // If we give it full URI, ModuleLoader might choke if it expects File.
                // We will handle this in Compiler/ModuleLoader integration.
                program.sourcePath = entryResource.uri.toString()
            } else {
                program.sourcePath = "file://dev-mode"
            }
        } catch (e: Exception) {
            program.sourcePath = "unknown"
        }
        
        println("[SOMNIA] Parsed Entry Program: ${program.sourcePath}")
        return program
    }

}
