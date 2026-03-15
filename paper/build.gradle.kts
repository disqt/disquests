plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

val minecraft_version: String by project
val paper_api_version: String by project

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paper_api_version")
    implementation(project(":common"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from(project(":common").sourceSets.main.get().output)
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
