plugins {
    id("fabric-loom") version "1.15.5"
}

repositories {
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.wispforest.io/releases/")
    maven("https://jitpack.io")
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

    implementation("org.lwjgl:lwjgl-tinyfd:3.3.3")
    include("org.lwjgl:lwjgl-tinyfd:3.3.3")

    // Native libraries for file dialogs
    listOf("natives-linux", "natives-windows", "natives-macos", "natives-macos-arm64").forEach { classifier ->
        runtimeOnly("org.lwjgl:lwjgl-tinyfd:3.3.3:$classifier")
        include("org.lwjgl:lwjgl-tinyfd:3.3.3:$classifier")
    }

    // Mod Menu (optional at runtime)
    val modmenu_version: String by project
    modCompileOnly("com.terraformersmc:modmenu:$modmenu_version")

    // owo-lib UI framework
    val owo_version: String by project
    modImplementation("io.wispforest:owo-lib:$owo_version")
    include("io.wispforest:owo-sentinel:$owo_version")

    // Markdown rendering
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.27.1")
    include("org.commonmark:commonmark:0.27.1")
    include("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
    include("org.commonmark:commonmark-ext-task-list-items:0.27.1")
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
            vmArg("-Ddisquests.test.server.host=localhost")
            vmArg("-Ddisquests.test.server.port=25565")
            vmArg("-Ddisquests.test.rcon.port=25575")
            vmArg("-Ddisquests.test.rcon.password=testpassword")
            // Integration test properties (passed via -P or -D from orchestrator)
            val journey = System.getProperty("disquests.test.journey")
            val phase = System.getProperty("disquests.test.phase")
            if (journey != null) vmArg("-Ddisquests.test.journey=$journey")
            if (phase != null) vmArg("-Ddisquests.test.phase=$phase")
            val username = providers.gradleProperty("testUsername")
            if (username.isPresent) programArgs("--username", username.get())
        }
    }
}

tasks.named("runClientGameTest") {
    doFirst {
        val requireFreeRam = rootProject.extra["requireFreeRam"] as (String, Long) -> Unit
        requireFreeRam("runClientGameTest", 4096L)
    }
}

data class JourneyPhase(val journey: String, val phase: Int, val player: String)

tasks.register("runIntegrationTest") {
    group = "verification"
    description = "Run integration E2E tests against a Paper dev server"
    dependsOn(":paper:build", ":client:build")

    doLast {
        // Clean DB
        val db = file("../paper/run/plugins/Disquests/disquests.db")
        val walFile = file("../paper/run/plugins/Disquests/disquests.db-wal")
        val shmFile = file("../paper/run/plugins/Disquests/disquests.db-shm")
        listOf(db, walFile, shmFile).forEach { if (it.exists()) it.delete() }
        logger.lifecycle("Cleaned test database")

        // Start Paper server
        val paperJar = file("../paper/run/paper.jar")
        val serverDir = file("../paper/run")
        val serverProcess = ProcessBuilder(
            "java", "-Xmx1G", "-jar", paperJar.absolutePath, "--nogui"
        ).directory(serverDir).redirectErrorStream(true).start()

        try {
            // Wait for server ready
            logger.lifecycle("Waiting for Paper server...")
            val logFile = File(serverDir, "logs/latest.log")
            val deadline = System.currentTimeMillis() + 60000
            while (System.currentTimeMillis() < deadline) {
                if (logFile.exists() && logFile.readText().contains("Done (")) break
                Thread.sleep(1000)
            }
            if (!logFile.exists() || !logFile.readText().contains("Done (")) {
                throw RuntimeException("Paper server failed to start within 60s")
            }
            logger.lifecycle("Paper server ready")

            // Define journeys
            val journeys = listOf(
                listOf(JourneyPhase("QuestLifecycleTest", 1, "IntTestPlayerA")),
                listOf(
                    JourneyPhase("QuestDiscoveryTest", 1, "IntTestPlayerA"),
                    JourneyPhase("QuestDiscoveryTest", 2, "IntTestPlayerB"),
                ),
                listOf(
                    JourneyPhase("CollaborationTest", 1, "IntTestPlayerA"),
                    JourneyPhase("CollaborationTest", 2, "IntTestPlayerB"),
                    JourneyPhase("CollaborationTest", 3, "IntTestPlayerA"),
                ),
                listOf(
                    JourneyPhase("LeaveQuestTest", 1, "IntTestPlayerA"),
                    JourneyPhase("LeaveQuestTest", 2, "IntTestPlayerB"),
                    JourneyPhase("LeaveQuestTest", 3, "IntTestPlayerB"),
                ),
                listOf(
                    JourneyPhase("PinPersistenceTest", 1, "IntTestPlayerA"),
                    JourneyPhase("PinPersistenceTest", 2, "IntTestPlayerA"),
                ),
            )

            var totalPassed = 0
            var totalFailed = 0

            for (journey in journeys) {
                val journeyName = journey.first().journey
                logger.lifecycle("=== Journey: $journeyName ===")
                var failed = false

                for (jp in journey) {
                    if (failed) {
                        logger.lifecycle("  Skipping phase ${jp.phase} (previous failed)")
                        continue
                    }
                    logger.lifecycle("  Phase ${jp.phase} as ${jp.player}...")

                    val isWin = System.getProperty("os.name").lowercase().contains("win")
                    val cmd = if (isWin) {
                        listOf("cmd", "/c", "gradlew.bat", ":client:runClientGameTest", "--no-daemon",
                            "-Ddisquests.test.journey=${jp.journey}",
                            "-Ddisquests.test.phase=${jp.phase}",
                            "-PtestUsername=${jp.player}")
                    } else {
                        listOf("./gradlew", ":client:runClientGameTest", "--no-daemon",
                            "-Ddisquests.test.journey=${jp.journey}",
                            "-Ddisquests.test.phase=${jp.phase}",
                            "-PtestUsername=${jp.player}")
                    }
                    val proc = ProcessBuilder(cmd)
                        .directory(rootProject.projectDir)
                        .inheritIO()
                        .start()
                    val exitCode = proc.waitFor()

                    if (exitCode != 0) {
                        logger.error("  FAILED: $journeyName phase ${jp.phase} as ${jp.player}")
                        failed = true
                        totalFailed++
                    } else {
                        logger.lifecycle("  PASSED")
                        totalPassed++
                    }
                }
                if (!failed) logger.lifecycle("=== $journeyName: ALL PHASES PASSED ===")
            }

            logger.lifecycle("\n=== Integration Test Results: $totalPassed passed, $totalFailed failed ===")
            if (totalFailed > 0) throw RuntimeException("$totalFailed integration test phase(s) failed")

        } finally {
            serverProcess.destroyForcibly()
            logger.lifecycle("Paper server stopped")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
