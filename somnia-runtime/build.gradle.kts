plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":somnia-lang"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
}
