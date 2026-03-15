plugins {
    id("fabric-loom") version "1.14.10"
}

val minecraft_version: String by project
val yarn_mappings: String by project
val fabric_loader_version: String by project
val fabric_api_version: String by project

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings("net.fabricmc:yarn:$yarn_mappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    implementation(project(":common"))
    include(project(":common"))

    implementation("org.lwjgl:lwjgl-tinyfd:3.3.1")
    include("org.lwjgl:lwjgl-tinyfd:3.3.1")

    // Markdown rendering
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.24.0")
    include("org.commonmark:commonmark:0.24.0")
    include("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    include("org.commonmark:commonmark-ext-task-list-items:0.24.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

sourceSets {
    create("testmod") {
        compileClasspath += sourceSets.main.get().output
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
}

loom {
    runs {
        create("clientGameTest") {
            client()
            configName = "Client Game Test"
            source(sourceSets.getByName("testmod"))
            vmArg("-Dfabric.client.gametest")
            vmArg("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
            vmArg("-Dbuildnotes.test.server.host=localhost")
            vmArg("-Dbuildnotes.test.server.port=25565")
            vmArg("-Dbuildnotes.test.rcon.port=25575")
            vmArg("-Dbuildnotes.test.rcon.password=testpassword")
        }
    }
}

tasks.named("runClientGameTest") {
    doFirst {
        val requireFreeRam = rootProject.extra["requireFreeRam"] as (String, Long) -> Unit
        requireFreeRam("runClientGameTest", 4096L)
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
