plugins {
    java
    jacoco
    alias(libs.plugins.run.paper)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

val minecraft_version: String by project
val paper_api_version: String by project

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paper_api_version")
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.sqlite.jdbc)
    implementation(project(":common"))
    testImplementation("io.papermc.paper:paper-api:$paper_api_version")
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
    }
}

tasks.jar {
    from(project(":common").sourceSets.main.get().output)
    // Bundle sqlite-jdbc for runtime (Paper doesn't provide it for plugins)
    from(configurations.runtimeClasspath.get().filter { it.name.contains("sqlite-jdbc") }.map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.runServer {
    minecraftVersion(minecraft_version)
    jvmArgs("-Xmx512m")
    systemProperty("com.mojang.eula.agree", "true")

    doFirst {
        val requireFreeRam = rootProject.extra["requireFreeRam"] as (String, Long) -> Unit
        requireFreeRam("runServer", 4096L)
    }
}
