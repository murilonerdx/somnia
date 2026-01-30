package somnia.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

class SomniaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("somniaGenerate", SomniaGenerateTask::class.java)
        project.tasks.register("somniaDoctor", SomniaDoctorTask::class.java)
    }
}

open class SomniaGenerateTask : DefaultTask() {
    @TaskAction
    fun generate() {
        println("🚀 Somnia Generate: Processing .somni files...")
        // Simulação de geração de código ou IR
        println("✅ Generation complete.")
    }
}

open class SomniaDoctorTask : DefaultTask() {
    @TaskAction
    fun diagnose() {
        println("🏥 Somnia Doctor: Running from Gradle...")
        // Chamar lógica do somnia-cli
    }
}
