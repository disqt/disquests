plugins {
    java
}

subprojects {
    // Client uses Fabric Loom which manages its own Java config
    if (name != "client") {
        apply(plugin = "java")

        java {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    repositories {
        mavenCentral()
    }
}
