package somnia.cli

import picocli.CommandLine.Command
import java.io.File
import java.util.concurrent.Callable

@Command(name = "doctor", description = ["Check environment for Somnia compatibility"])
class DoctorCommand : Callable<Int> {
    override fun call(): Int {
        println("\u001B[1m[SOMNIA DOCTOR] Checking environment...\u001B[0m")
        
        var issues = 0

        // 1. Check for Somnia files
        val files = File(".").walk().filter { it.extension == "somnia" }.toList()
        if (files.isEmpty()) {
            println("\u001B[31m[!] No .somnia files found in current directory.\u001B[0m")
            issues++
        } else {
            println("\u001B[32m[OK] Found ${files.size} .somnia files.\u001B[0m")
        }

        // 2. Check for Spring Boot project
        val hasGradle = File("build.gradle.kts").exists() || File("build.gradle").exists()
        if (!hasGradle) {
            println("\u001B[33m[?] No Gradle build file found. Are you in a Spring project root?\u001B[0m")
        } else {
            println("\u001B[32m[OK] Gradle project detected.\u001B[0m")
        }

        // 3. Check application.yaml for Redis/Kafka
        val appYaml = File("src/main/resources/application.yaml")
        if (appYaml.exists()) {
            val content = appYaml.readText()
            if (content.contains("redis")) println("\u001B[32m[OK] Redis configuration detected in application.yaml.\u001B[0m")
            if (content.contains("kafka")) println("\u001B[32m[OK] Kafka configuration detected in application.yaml.\u001B[0m")
        }

        if (issues == 0) {
            println("\n\u001B[32m\u001B[1mEnvironment looks good!\u001B[0m")
            return 0
        } else {
            println("\n\u001B[31mFound $issues critical issues.\u001B[0m")
            return 1
        }
    }
}
