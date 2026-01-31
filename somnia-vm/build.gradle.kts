plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":somnia-lang"))
}

application {
    mainClass.set("somnia.vm.SomniaSDK")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "somnia.vm.SomniaSDK"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    archiveBaseName.set("somnia")
}
