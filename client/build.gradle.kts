import java.io.DataInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

plugins {
    id("fabric-loom") version "1.15.5"
    jacoco
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

dependencies {
    // JUnit 5 for integration test harness (programmatic launcher inside MC client)
    "testmodImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
    "testmodImplementation"("org.junit.platform:junit-platform-launcher:1.11.4")
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
            val harness = providers.gradleProperty("harness")
            if (harness.isPresent) vmArg("-Ddisquests.test.harness=true")
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
            val harnessB = providers.gradleProperty("harness")
            if (harnessB.isPresent) vmArg("-Ddisquests.test.harness=true")
        }
    }
}

// --- JaCoCo coverage for game tests ---
val jacocoExecFile = layout.buildDirectory.file("jacoco/gameTest.exec")

// JaCoCo runtime agent (with Premain-Class manifest)
val jacocoRuntime by configurations.creating

dependencies {
    jacocoRuntime("org.jacoco:org.jacoco.agent:0.8.14:runtime")
}

tasks.named<JavaExec>("runClientGameTest") {
    doFirst {
        val requireFreeRam = rootProject.extra["requireFreeRam"] as (String, Long) -> Unit
        requireFreeRam("runClientGameTest", 4096L)
    }
    // Attach JaCoCo agent if -Pcoverage is passed
    if (project.hasProperty("coverage")) {
        doFirst {
            val agentJar = jacocoRuntime.singleFile
            val destFile = jacocoExecFile.get().asFile
            destFile.parentFile.mkdirs()
            val agentArg = "-javaagent:$agentJar=destfile=$destFile,includes=com.disqt.disquests.*"
            jvmArgs(agentArg)
            logger.lifecycle("JaCoCo agent attached, writing to: $destFile")
        }
    }
}

tasks.register<JacocoReport>("jacocoGameTestReport") {
    group = "verification"
    description = "Generate code coverage report from E2E game tests"
    dependsOn("compileJava")

    executionData(jacocoExecFile)
    sourceDirectories.from(files("src/main/java"))
    classDirectories.from(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            include("com/disqt/disquests/**")
        }
    )

    reports {
        html.required.set(true)
        xml.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/gameTest/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/gameTest/report.xml"))
    }
}

fun sendRconCommand(host: String, port: Int, password: String, command: String): String {
    val socket = Socket(host, port)
    socket.soTimeout = 5000
    val out = socket.getOutputStream()
    val inp = socket.getInputStream()
    val dins = DataInputStream(inp)

    fun writePacket(id: Int, type: Int, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val length = 4 + 4 + bodyBytes.size + 1 + 1
        val buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(length)
        buf.putInt(id)
        buf.putInt(type)
        buf.put(bodyBytes)
        buf.put(0.toByte())
        buf.put(0.toByte())
        out.write(buf.array())
        out.flush()
    }

    fun readPacket(): Triple<Int, Int, String> {
        val lenBuf = ByteArray(4)
        dins.readFully(lenBuf)
        val length = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
        val payload = ByteArray(length)
        dins.readFully(payload)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val reqId = buf.int
        val type = buf.int
        val body = String(payload, 8, length - 10, Charsets.UTF_8)
        return Triple(reqId, type, body)
    }

    // Login
    writePacket(1, 3, password)
    val loginResp = readPacket()
    if (loginResp.first == -1) {
        socket.close()
        throw RuntimeException("RCON authentication failed")
    }

    // Command
    writePacket(2, 2, command)
    val cmdResp = readPacket()
    socket.close()
    return cmdResp.third
}

tasks.register("runIntegrationTest") {
    group = "verification"
    description = "Run integration E2E tests with optional harness mode for persistent clients"
    dependsOn(":paper:jar", ":client:build")

    doLast {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val gradlew = File(rootProject.projectDir, if (isWin) "gradlew.bat" else "gradlew").absolutePath
        val noStart = project.hasProperty("noStart")
        val harness = project.hasProperty("harness")
        val testFilter = project.findProperty("testFilter")?.toString()

        val syncDir = File(rootProject.projectDir, "integration-sync")
        val serverDir = file("../paper/run")
        val processes = mutableListOf<Process>()
        var serverProcess: Process? = null
        var startedServer = false
        var startedClients = false

        // --- Step 1: Clean sync directory ---
        if (syncDir.exists()) syncDir.listFiles()?.forEach { it.delete() }
        syncDir.mkdirs()
        logger.lifecycle("Cleaned sync directory")

        try {
            // --- Step 2: Server ---
            if (!noStart) {
                val serverRunning = try {
                    Socket("localhost", 25565).use { true }
                } catch (_: Exception) { false }

                if (!serverRunning) {
                    // Clean DB for fresh start
                    val db = file("../paper/run/plugins/Disquests/disquests.db")
                    val walFile = file("../paper/run/plugins/Disquests/disquests.db-wal")
                    val shmFile = file("../paper/run/plugins/Disquests/disquests.db-shm")
                    listOf(db, walFile, shmFile).forEach { if (it.exists()) it.delete() }

                    // Deploy plugin jar
                    val pluginJar = file("../paper/build/libs/paper.jar")
                    val pluginDest = File(serverDir, "plugins/Disquests.jar")
                    pluginJar.copyTo(pluginDest, overwrite = true)
                    logger.lifecycle("Deployed plugin jar")

                    val paperJar = File(serverDir, "paper.jar")
                    serverProcess = ProcessBuilder(
                        "java", "-Xmx1G", "-Ddisquests.debug=true",
                        "-jar", paperJar.absolutePath, "--nogui"
                    ).directory(serverDir).redirectErrorStream(true).start()
                    startedServer = true

                    // Drain server stdout
                    val sp = serverProcess!!
                    Thread { sp.inputStream.bufferedReader().lines().forEach { } }.apply { isDaemon = true; start() }

                    // Wait for server startup
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
                } else {
                    logger.lifecycle("Server already running on port 25565")
                    // RCON reset for re-runs against existing server
                    try {
                        sendRconCommand("localhost", 25575, "testpassword", "disquests reset")
                        logger.lifecycle("Sent RCON reset")
                    } catch (e: Exception) {
                        logger.warn("RCON reset failed: ${e.message}")
                    }
                }
            } else {
                // -PnoStart: verify clients are ready
                val readyDeadline = System.currentTimeMillis() + 10000
                while (System.currentTimeMillis() < readyDeadline) {
                    if (File(syncDir, "client-a-ready.done").exists() &&
                        File(syncDir, "client-b-ready.done").exists()) break
                    Thread.sleep(500)
                }
                if (!File(syncDir, "client-a-ready.done").exists() ||
                    !File(syncDir, "client-b-ready.done").exists()) {
                    throw RuntimeException("Clients not running. Start them first or remove -PnoStart.")
                }
                // RCON reset
                try {
                    // Clean sync dir except ready markers
                    syncDir.listFiles()?.filter { !it.name.startsWith("client-") }?.forEach { it.delete() }
                    sendRconCommand("localhost", 25575, "testpassword", "disquests reset")
                    logger.lifecycle("Sent RCON reset")
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    logger.warn("RCON reset failed: ${e.message}")
                }
            }

            // --- Step 3: Clients ---
            if (!noStart) {
                val clientAReady = File(syncDir, "client-a-ready.done").exists()
                val clientBReady = File(syncDir, "client-b-ready.done").exists()

                if (!clientAReady || !clientBReady) {
                    file("run-b").mkdirs()

                    fun launchClient(journey: String, username: String, taskName: String): Process {
                        val cmd = mutableListOf<String>()
                        if (isWin) cmd.addAll(listOf("cmd", "/c"))
                        cmd.add(gradlew)
                        cmd.addAll(listOf(taskName, "--no-daemon",
                            "-PtestJourney=$journey", "-PtestUsername=$username"))
                        if (harness) cmd.add("-Pharness")
                        logger.lifecycle("  Starting $journey as $username")
                        return ProcessBuilder(cmd)
                            .directory(rootProject.projectDir)
                            .redirectErrorStream(true)
                            .start()
                    }

                    val procA = launchClient("HarnessPlayerA", "IntTestPlayerA", ":client:runClientGameTest")
                    val procB = launchClient("HarnessPlayerB", "IntTestPlayerB", ":client:runClientGameTestB")
                    processes.addAll(listOf(procA, procB))
                    startedClients = true

                    // Drain output
                    Thread { procA.inputStream.bufferedReader().lines().forEach { } }.apply { isDaemon = true; start() }
                    Thread { procB.inputStream.bufferedReader().lines().forEach { } }.apply { isDaemon = true; start() }

                    if (harness) {
                        // Wait for clients to signal ready
                        logger.lifecycle("Waiting for clients to be ready...")
                        val readyDeadline = System.currentTimeMillis() + 120000
                        while (System.currentTimeMillis() < readyDeadline) {
                            if (File(syncDir, "client-a-ready.done").exists() &&
                                File(syncDir, "client-b-ready.done").exists()) break
                            Thread.sleep(1000)
                        }
                        if (!File(syncDir, "client-a-ready.done").exists() ||
                            !File(syncDir, "client-b-ready.done").exists()) {
                            throw RuntimeException("Clients failed to signal ready within 120s")
                        }
                        logger.lifecycle("Clients ready")

                        // RCON reset (fresh DB for first run)
                        try {
                            sendRconCommand("localhost", 25575, "testpassword", "disquests reset")
                            logger.lifecycle("Sent RCON reset")
                            Thread.sleep(1000)
                        } catch (e: Exception) {
                            logger.warn("RCON reset failed: ${e.message}")
                        }
                    }
                } else {
                    logger.lifecycle("Clients already running (ready markers found)")
                }
            }

            // --- Step 4: Write run signal ---
            val signalContent = testFilter ?: "*"
            File(syncDir, "run.signal").writeText(signalContent)
            logger.lifecycle("Triggered test run: $signalContent")

            // --- Step 5: Wait for results ---
            val resultA = File(syncDir, "results-a.txt")
            val resultB = File(syncDir, "results-b.txt")
            val resultsDeadline = System.currentTimeMillis() + 180000
            while (System.currentTimeMillis() < resultsDeadline) {
                if (resultA.exists() && resultB.exists()) break
                // Check for process crashes in one-shot mode
                if (!harness && startedClients && processes.all { !it.isAlive }) break
                Thread.sleep(500)
            }

            // --- Step 6: Report results ---
            logger.lifecycle("\n=== Integration Test Results ===")
            val aResult = if (resultA.exists()) resultA.readText().trim() else "NO RESULT (timeout or crash)"
            val bResult = if (resultB.exists()) resultB.readText().trim() else "NO RESULT (timeout or crash)"
            logger.lifecycle("  Player A: $aResult")
            logger.lifecycle("  Player B: $bResult")

            val passed = aResult.startsWith("PASS") && bResult.startsWith("PASS")
            if (!passed) {
                throw RuntimeException("Integration tests failed")
            }
            logger.lifecycle("  All tests PASSED")

        } finally {
            // --- Step 7: Teardown ---
            if (!harness && !noStart) {
                processes.forEach { it.destroyForcibly() }
                if (serverProcess != null) {
                    try {
                        serverProcess.outputStream.write("stop\n".toByteArray())
                        serverProcess.outputStream.flush()
                        Thread.sleep(5000)
                    } catch (_: Exception) {}
                    if (serverProcess.isAlive) serverProcess.destroyForcibly()
                }
                logger.lifecycle("Teardown complete")
            } else {
                logger.lifecycle("Harness mode: server and clients left running")
            }
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
