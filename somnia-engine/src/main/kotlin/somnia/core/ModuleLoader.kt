package somnia.core

import somnia.lang.SomniaProgram
import somnia.lang.Token
import somnia.lang.Lexer
import somnia.lang.Parser
import java.io.File
import java.nio.file.Files

class ModuleLoader {
    private val loadedModules = mutableMapOf<String, SomniaProgram>()
    
    fun loadImports(program: SomniaProgram, baseDir: File) {
        val queue = ArrayDeque<String>()
        program.imports.forEach { queue.add(it.path) }
        
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (loadedModules.containsKey(path)) continue
            
            val module = loadModule(path, baseDir)
            if (module != null) {
                loadedModules[path] = module
                merge(program, module)
                module.imports.forEach { 
                    if (!loadedModules.containsKey(it.path)) queue.add(it.path) 
                }
            } else {
                // Mark as loaded to avoid infinite retry loops on failure
                loadedModules[path] = SomniaProgram() 
            }
        }
    }
    
    private fun loadModule(path: String, baseDir: File): SomniaProgram? {
        var file = File(baseDir, path)
        var content: String? = null
        
        if (file.exists()) {
            println("[SOMNIA] Loading module from file: ${file.absolutePath}")
            content = Files.readString(file.toPath())
        } else {
            // Check classpath with and without specific prefixes
            val attempts = listOf(path, "somnia/$path", "somnia/std/$path")
            for (p in attempts) {
                val resource = this::class.java.classLoader.getResource(p)
                if (resource != null) {
                    println("[SOMNIA] Loading module from classpath: $p")
                    content = resource.readText()
                    break
                }
            }
        }
        
        if (content == null) {
            println("[SOMNIA] Warning: Could not find module '$path' in ${baseDir.absolutePath} or classpath")
            return null
        }
        
        return try {
            val tokens = Lexer(content).tokenize()
            Parser(tokens).parse()
        } catch (e: Exception) {
            println("[SOMNIA] Error parsing module '$path': ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun merge(acc: SomniaProgram, mod: SomniaProgram) {
        acc.act.entities.addAll(mod.act.entities)
        acc.act.dtos.addAll(mod.act.dtos)
        acc.act.repositories.addAll(mod.act.repositories)
        acc.act.actions.addAll(mod.act.actions)
        acc.act.renders.addAll(mod.act.renders)
        acc.act.patternDefs.addAll(mod.act.patternDefs)
        
        if (mod.act.http != null) {
            if (acc.act.http == null) {
                acc.act.http = mod.act.http
            } else {
                val currentRoutes = acc.act.http!!.routes.toMutableList()
                currentRoutes.addAll(mod.act.http!!.routes)
                acc.act.http = acc.act.http!!.copy(routes = currentRoutes)
            }
        }
    }
}
