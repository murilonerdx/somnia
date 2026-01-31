plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("org.springframework.boot") version "3.2.0" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

subprojects {
    apply(plugin = "maven-publish")

    /*
    publishing {
        publications {
             create<MavenPublication>("maven") {
                 val javaComponent = components.findByName("java")
                 if (javaComponent != null) {
                     from(javaComponent)
                 }
             }
        }
    }
    */
}
