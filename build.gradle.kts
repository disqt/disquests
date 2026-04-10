import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.lefthook)
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.cyclonedx)
}

fun requireFreeRam(taskName: String, requiredMb: Long = 4096L) {
    if (System.getenv("CI") != null) return
    val factory = Class.forName("java.lang.management.ManagementFactory")
    val bean = factory.getMethod("getOperatingSystemMXBean").invoke(null)
    val clazz = Class.forName("com.sun.management.OperatingSystemMXBean")
    val freeMemMb = if (clazz.isInstance(bean)) {
        clazz.getMethod("getFreeMemorySize").invoke(bean) as Long / (1024 * 1024)
    } else { Long.MAX_VALUE }
    if (freeMemMb < requiredMb) {
        throw GradleException(
            "$taskName needs at least ${requiredMb}MB free RAM, but only ${freeMemMb}MB available. " +
            "Skip with: -x $taskName"
        )
    }
}

extra["requireFreeRam"] = ::requireFreeRam

lefthook {
    config.set(mapOf(
        "pre-commit" to mapOf(
            "commands" to mapOf(
                "java-format" to mapOf(
                    "glob" to "*.java",
                    "run" to "./gradlew spotlessApply -PspotlessIdeHook={staged_files}",
                    "stage_fixed" to true
                )
            )
        )
    ))
}

allprojects {
    version = findProperty("mod_version") ?: "unspecified"
}

subprojects {
    // Keep jar names stable (e.g. server.jar not server-0.3.1.jar)
    // so CI, deploy scripts, and build.gradle.kts references all work.
    // project.version is still set (for fabric.mod.json substitution) but
    // stripped from archive filenames.
    afterEvaluate {
        tasks.withType<AbstractArchiveTask>().configureEach {
            archiveVersion.set("")
        }
    }

    // Client uses Fabric Loom which manages its own Java config
    if (name != "client") {
        apply(plugin = "java")

        java {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat()
            targetExclude("build/**")
        }
    }

    // Error Prone static analysis (common + server; client handled by Fabric Loom separately)
    if (name != "client") {
        apply(plugin = "net.ltgt.errorprone")

        dependencies {
            "errorprone"(rootProject.libs.errorprone.core)
            "errorprone"(rootProject.libs.nullaway)
        }

        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                disableWarningsInGeneratedCode.set(true)
                option("NullAway:AnnotatedPackages", "com.disqt.disquests")
            }
        }
    }

    repositories {
        mavenCentral()
    }
}
