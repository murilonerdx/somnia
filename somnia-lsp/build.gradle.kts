plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":somnia-lang"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.23.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.23.1")
}

application {
    mainClass.set("somnia.lsp.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("somnia-lsp")
    archiveClassifier.set("all")
}
