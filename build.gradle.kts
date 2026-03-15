plugins {
    java
}

subprojects {
    // Client uses Fabric Loom which manages its own Java config
    if (name != "client") {
        apply(plugin = "java")

        java {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    repositories {
        mavenCentral()
    }
}
