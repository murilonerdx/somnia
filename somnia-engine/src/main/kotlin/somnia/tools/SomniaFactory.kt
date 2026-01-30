package somnia.tools

import java.io.File

/**
 * SOMNIA APPLICATION FACTORY
 * usage: SomniaFactory.create("MyNewApp")
 */
object SomniaFactory {
    
    fun create(appName: String, resourcesDir: String = "src/main/resources") {
        val root = File(resourcesDir, "somnia_app")
        if (root.exists()) root.deleteRecursively()
        
        // 1. Create Folder Structure
        val bindingsDir = File(root, "bindings")
        val implDir = File(root, "implementations")
        
        bindingsDir.mkdirs()
        implDir.mkdirs()
        
        println("=== SOMNIA FACTORY ===")
        println("Scaffolding '$appName' at $root...")

        // 2. Create 'main.somnia' (The Entry Point)
        File(root, "main.somnia").writeText("""
            // APPLICATION: $appName
            // ENTRY POINT
            
            id {
               drive user_satisfaction @0.5
            }
            
            act {
               // Default intent listener
               intent "start_app"
            }
        """.trimIndent())
        
        // 3. Create 'bindings/spring.somnia' (The Connector Layer)
        File(bindingsDir, "spring.somnia").writeText("""
            // BINDINGS: Maps Somnia concepts to Java/Spring calls
            
            id {
                // Register concepts representing external tools
                concept "database"
                concept "logger"
                
                // Association: Logging helps debugging (which implies satisfaction?)
                "logger" -> "user_satisfaction" @0.1
            }
            
            id {
                // Example Rule: Link the 'log' action to the Spring Logger
                // Note: We don't implement logic here, just definitions
            }
        """.trimIndent())
        
        // 4. Create 'implementations/auth.somnia' (Business Logic)
        File(implDir, "auth.somnia").writeText("""
            // IMPLEMENTATION: Authentication Domain
            
            id {
                drive security @0.8
                
                // If we want security, we should log things
                when drive(security) 
                   => propose "java.lang.System.out.println" "[SECURITY AUDIT] System Secure" @0.9
            }
            
            ego {
                // Always log security events
                select top 3
            }
        """.trimIndent())
        
        println("SUCCESS: Project structure created.")
        println(" - ${root.absolutePath}")
        println("   + bindings/")
        println("   + implementations/")
        println("   + main.somnia")
    }
}

fun main() {
    SomniaFactory.create("GenOne")
}
