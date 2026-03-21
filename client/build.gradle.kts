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
            // Integration test properties (passed via -P from orchestrator)
            val journey = providers.gradleProperty("testJourney")
            val phase = providers.gradleProperty("testPhase")
            val username = providers.gradleProperty("testUsername")
            if (journey.isPresent) vmArg("-Ddisquests.test.journey=${journey.get()}")
            if (phase.isPresent) vmArg("-Ddisquests.test.phase=${phase.get()}")
            if (username.isPresent) programArgs("--username", username.get())
        }
        // Second client run config for Player B (separate run directory)
        create("clientGameTestB") {
            client()
            configName = "Client Game Test B"
            runDir = "run-b"
            source(sourceSets.getByName("testmod"))
            vmArg("-Dfabric.client.gametest")
            vmArg("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
            vmArg("-Ddisquests.test.server.host=localhost")
            vmArg("-Ddisquests.test.server.port=25565")
            vmArg("-Ddisquests.test.rcon.port=25575")
            vmArg("-Ddisquests.test.rcon.password=testpassword")
            val journeyB = providers.gradleProperty("testJourney")
            val phaseB = providers.gradleProperty("testPhase")
            val usernameB = providers.gradleProperty("testUsername")
            if (journeyB.isPresent) vmArg("-Ddisquests.test.journey=${journeyB.get()}")
            if (phaseB.isPresent) vmArg("-Ddisquests.test.phase=${phaseB.get()}")
            if (usernameB.isPresent) programArgs("--username", usernameB.get())
        }
    }
}

tasks.named("runClientGameTest") {
    doFirst {
        val requireFreeRam = rootProject.extra["requireFreeRam"] as (String, Long) -> Unit
        requireFreeRam("runClientGameTest", 4096L)
    }
}

tasks.register("runIntegrationTest") {
    group = "verification"
    description = "Run integration E2E tests: starts Paper server, runs two clients in parallel"
    dependsOn(":paper:build", ":client:build")

    doLast {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val gradlew = File(rootProject.projectDir, if (isWin) "gradlew.bat" else "gradlew").absolutePath

        // Clean DB + sync dir
        val db = file("../paper/run/plugins/Disquests/disquests.db")
        val walFile = file("../paper/run/plugins/Disquests/disquests.db-wal")
        val shmFile = file("../paper/run/plugins/Disquests/disquests.db-shm")
        listOf(db, walFile, shmFile).forEach { if (it.exists()) it.delete() }
        val syncDir = File(rootProject.projectDir, "integration-sync")
        if (syncDir.exists()) syncDir.listFiles()?.forEach { it.delete() }
        syncDir.mkdirs()
        logger.lifecycle("Cleaned test database and sync directory")

        // Start Paper server
        val serverDir = file("../paper/run")

        // Deploy plugin jar
        val pluginJar = file("../paper/build/libs/paper.jar")
        val pluginDest = File(serverDir, "plugins/Disquests.jar")
        pluginJar.copyTo(pluginDest, overwrite = true)
        logger.lifecycle("Deployed plugin jar")
        val paperJar = File(serverDir, "paper.jar")
        val serverProcess = ProcessBuilder(
            "java", "-Xmx1G", "-jar", paperJar.absolutePath, "--nogui"
        ).directory(serverDir).redirectErrorStream(true).start()

        try {
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

            // Ensure run-b directory exists
            file("run-b").mkdirs()

            // Launch both clients in parallel (separate run dirs)
            fun launchClient(journey: String, username: String, taskName: String = ":client:runClientGameTest"): Process {
                val cmd = if (isWin) {
                    listOf("cmd", "/c", gradlew, taskName, "--no-daemon",
                        "-PtestJourney=$journey", "-PtestUsername=$username")
                } else {
                    listOf(gradlew, taskName, "--no-daemon",
                        "-PtestJourney=$journey", "-PtestUsername=$username")
                }
                logger.lifecycle("  Starting $journey as $username")
                return ProcessBuilder(cmd)
                    .directory(rootProject.projectDir)
                    .redirectErrorStream(true)
                    .start()
            }

            val procA = launchClient("IntegrationPlayerA", "IntTestPlayerA")
            val procB = launchClient("IntegrationPlayerB", "IntTestPlayerB", ":client:runClientGameTestB")

            // Read output in threads to prevent blocking
            val outputA = StringBuilder()
            val outputB = StringBuilder()
            val readerA = Thread { procA.inputStream.bufferedReader().lines().forEach { outputA.appendLine(it) } }
            val readerB = Thread { procB.inputStream.bufferedReader().lines().forEach { outputB.appendLine(it) } }
            readerA.start()
            readerB.start()

            val exitA = procA.waitFor()
            val exitB = procB.waitFor()
            readerA.join(5000)
            readerB.join(5000)

            logger.lifecycle("\n=== Integration Test Results ===")
            if (exitA == 0) {
                logger.lifecycle("  Player A: PASSED")
            } else {
                logger.error("  Player A: FAILED (exit=$exitA)")
                logger.lifecycle("  Output (last 500): ${outputA.takeLast(500)}")
            }
            if (exitB == 0) {
                logger.lifecycle("  Player B: PASSED")
            } else {
                logger.error("  Player B: FAILED (exit=$exitB)")
                logger.lifecycle("  Output (last 500): ${outputB.takeLast(500)}")
            }

            if (exitA != 0 || exitB != 0) {
                throw RuntimeException("Integration tests failed: A=${if (exitA==0) "PASS" else "FAIL"}, B=${if (exitB==0) "PASS" else "FAIL"}")
            }
            logger.lifecycle("  All journeys PASSED")

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
