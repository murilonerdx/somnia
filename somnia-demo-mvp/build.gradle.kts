plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "1.9.22"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":somnia-engine"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    // Somnia Lang for parser
    implementation(project(":somnia-lang"))
}

application {
    mainClass.set("somnia.demo.TodoApplicationKt")
}

tasks.bootRun {
    // Ensure we can read the .somnia file
    workingDir = file(".")
}
