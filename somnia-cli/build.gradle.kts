plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "1.9.22"
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":somnia-lang"))
    implementation(project(":somnia-engine"))
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

application {
    mainClass.set("somnia.cli.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("somnia")
    archiveClassifier.set("")
}
