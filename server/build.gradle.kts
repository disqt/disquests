plugins {
    java
    jacoco
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

val minecraft_version: String by project
val paper_api_version: String by project

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paper_api_version")
    implementation("org.xerial:sqlite-jdbc:3.51.2.0")
    testImplementation("org.xerial:sqlite-jdbc:3.51.2.0")
    implementation(project(":common"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.2")
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
