plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":somnia-lang"))
}

application {
    mainClass.set("somnia.cli.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "somnia.cli.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
