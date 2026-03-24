import java.io.DataInputStream
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    log4jConfigs.from(file("src/testmod/resources/log4j2-test.xml"))
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
            val testPackage = providers.gradleProperty("testPackage")
            if (testPackage.isPresent) vmArg("-Ddisquests.test.package=${testPackage.get()}")
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
            val testPackageB = providers.gradleProperty("testPackage")
            if (testPackageB.isPresent) vmArg("-Ddisquests.test.package=${testPackageB.get()}")
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

tasks.register<JacocoReport>("jacocoIntegrationTestReport") {
    group = "verification"
    description = "Generate code coverage report from integration E2E tests"
    dependsOn("compileJava")

    executionData(layout.buildDirectory.file("jacoco/gameTest.exec"))
    sourceDirectories.from(files("src/main/java"))
    classDirectories.from(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            include("com/disqt/disquests/**")
        }
    )

    reports {
        html.required.set(true)
        xml.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/integrationTest/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/integrationTest/report.xml"))
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

// --- Shared helpers for solo/duo test orchestrators ---

fun bootstrapServerDir(serverDir: File, mcVersion: String, logger: org.gradle.api.logging.Logger) {
    serverDir.mkdirs()
    File(serverDir, "plugins").mkdirs()
    File(serverDir, "logs").mkdirs()

    // Download paper.jar from Paper API
    logger.lifecycle("Downloading Paper $mcVersion...")
    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL).build()
    val versionResp = client.send(
        HttpRequest.newBuilder()
            .uri(URI("https://api.papermc.io/v2/projects/paper/versions/$mcVersion"))
            .GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )
    val buildsMatch = Regex("""\"builds"\s*:\s*\[([^\]]+)\]""").find(versionResp.body())
        ?: throw RuntimeException("Could not parse Paper API response for MC $mcVersion")
    val latestBuild = buildsMatch.groupValues[1].split(",").last().trim()
    val downloadName = "paper-$mcVersion-$latestBuild.jar"
    val downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/$mcVersion/builds/$latestBuild/downloads/$downloadName"
    logger.lifecycle("Downloading $downloadUrl")
    client.send(
        HttpRequest.newBuilder().uri(URI(downloadUrl)).GET().build(),
        HttpResponse.BodyHandlers.ofFile(File(serverDir, "paper.jar").toPath())
    )
    logger.lifecycle("Downloaded paper.jar (${File(serverDir, "paper.jar").length() / 1024 / 1024}MB)")

    // Create eula.txt
    File(serverDir, "eula.txt").writeText("eula=true\n")

    // Create server.properties with test settings
    File(serverDir, "server.properties").writeText(
        """
        |online-mode=false
        |max-players=4
        |enable-rcon=true
        |rcon.password=testpassword
        |rcon.port=25575
        |server-port=25565
        |level-type=flat
        |spawn-protection=0
        |""".trimMargin()
    )
    logger.lifecycle("Server directory bootstrapped at ${serverDir.absolutePath}")
}

fun ensureServer(serverDir: File, logger: org.gradle.api.logging.Logger, pluginJar: File, mcVersion: String): Process? {
    val serverRunning = try {
        Socket("localhost", 25565).use { true }
    } catch (_: Exception) { false }

    if (!serverRunning) {
        // Clean DB for fresh start
        val db = File(serverDir, "plugins/Disquests/disquests.db")
        val walFile = File(serverDir, "plugins/Disquests/disquests.db-wal")
        val shmFile = File(serverDir, "plugins/Disquests/disquests.db-shm")
        listOf(db, walFile, shmFile).forEach { if (it.exists()) it.delete() }

        // Deploy plugin jar
        val pluginDest = File(serverDir, "plugins/Disquests.jar")
        pluginJar.copyTo(pluginDest, overwrite = true)
        logger.lifecycle("Deployed plugin jar")

        val paperJar = File(serverDir, "paper.jar")
        if (!paperJar.exists()) {
            bootstrapServerDir(serverDir, mcVersion, logger)
        }
        val serverProcess = ProcessBuilder(
            "java", "-Xmx1G", "-Ddisquests.debug=true",
            "-jar", paperJar.absolutePath, "--nogui"
        ).directory(serverDir).redirectErrorStream(true).start()

        // Capture server stdout for debugging
        val serverLog = File(serverDir, "logs/server-stdout.log")
        Thread {
            serverLog.outputStream().use { out ->
                serverProcess.inputStream.copyTo(out)
            }
        }.apply { isDaemon = true; start() }

        // Wait for server to accept connections
        logger.lifecycle("Waiting for Paper server...")
        val serverPort = 25565
        val startupDeadline = System.currentTimeMillis() + 120_000L
        while (System.currentTimeMillis() < startupDeadline) {
            try {
                Socket("localhost", serverPort).close()
                break
            } catch (e: Exception) {
                Thread.sleep(1000)
            }
        }
        try {
            Socket("localhost", serverPort).close()
        } catch (e: Exception) {
            throw RuntimeException("Paper server not accepting connections on port $serverPort after 120s")
        }
        logger.lifecycle("Paper server ready")
        return serverProcess
    } else {
        logger.lifecycle("Server already running on port 25565")
        return null
    }
}

fun rconReset(logger: org.gradle.api.logging.Logger) {
    try {
        sendRconCommand("localhost", 25575, "testpassword", "disquests reset")
        logger.lifecycle("Sent RCON reset")
        Thread.sleep(1000)
    } catch (e: Exception) {
        logger.warn("RCON reset failed: ${e.message}")
    }
}

fun launchClient(
    gradlew: String, isWin: Boolean, rootDir: File,
    journey: String, username: String, taskName: String,
    harness: Boolean, coverage: Boolean, testPackage: String?,
    logger: org.gradle.api.logging.Logger
): Process {
    val cmd = mutableListOf<String>()
    if (isWin) cmd.addAll(listOf("cmd", "/c"))
    cmd.add(gradlew)
    cmd.addAll(listOf(taskName, "--no-daemon",
        "-PtestJourney=$journey", "-PtestUsername=$username"))
    if (harness) cmd.add("-Pharness")
    if (coverage) cmd.add("-Pcoverage")
    if (testPackage != null) cmd.add("-PtestPackage=$testPackage")
    logger.lifecycle("  Starting $journey as $username")
    return ProcessBuilder(cmd)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
}

fun stopServer(serverProcess: Process?) {
    if (serverProcess != null) {
        try {
            serverProcess.outputStream.write("stop\n".toByteArray())
            serverProcess.outputStream.flush()
            Thread.sleep(5000)
        } catch (_: Exception) {}
        if (serverProcess.isAlive) serverProcess.destroyForcibly()
    }
}

// --- runSoloTests: single-player journeys with one client ---

tasks.register("runSoloTests") {
    group = "verification"
    description = "Run single-player E2E journey tests (one client only)"
    dependsOn(":server:jar", ":client:build")

    doLast {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val gradlew = File(rootProject.projectDir, if (isWin) "gradlew.bat" else "gradlew").absolutePath
        val noStart = project.hasProperty("noStart")
        val testFilter = project.findProperty("testFilter")?.toString()
        val coverage = project.hasProperty("coverage")
        val soloPackage = "com.disqt.disquests.test.integration.journeys.solo"

        val syncDir = File(rootProject.projectDir, "integration-sync")
        val serverDir = file("../server/run")
        val processes = mutableListOf<Process>()
        var serverProcess: Process? = null
        var startedClients = false

        // --- Step 1: Clean sync directory ---
        if (syncDir.exists()) syncDir.listFiles()?.forEach { it.delete() }
        syncDir.mkdirs()
        logger.lifecycle("Cleaned sync directory")

        try {
            // --- Step 2: Server ---
            if (!noStart) {
                val pluginJar = file("../server/build/libs/server.jar")
                serverProcess = ensureServer(serverDir, logger, pluginJar, minecraft_version)
                rconReset(logger)
            } else {
                // -PnoStart: verify client A is ready
                val readyDeadline = System.currentTimeMillis() + 10000
                while (System.currentTimeMillis() < readyDeadline) {
                    if (File(syncDir, "client-a-ready.done").exists()) break
                    Thread.sleep(500)
                }
                if (!File(syncDir, "client-a-ready.done").exists()) {
                    throw RuntimeException("Client A not running. Start it first or remove -PnoStart.")
                }
                syncDir.listFiles()?.filter { !it.name.startsWith("client-") }?.forEach { it.delete() }
                rconReset(logger)
            }

            // --- Step 3: Launch PlayerA only ---
            if (!noStart) {
                val clientAReady = File(syncDir, "client-a-ready.done").exists()

                if (!clientAReady) {
                    val procA = launchClient(
                        gradlew, isWin, rootProject.projectDir,
                        "HarnessPlayerA", "IntTestPlayerA", ":client:runClientGameTest",
                        false, coverage, soloPackage, logger
                    )
                    processes.add(procA)
                    startedClients = true

                    val logA = File(syncDir, "client-a-output.log")
                    Thread {
                        logA.outputStream().use { out ->
                            procA.inputStream.copyTo(out)
                        }
                    }.apply { isDaemon = true; start() }
                } else {
                    logger.lifecycle("Client A already running (ready marker found)")
                }
            }

            // --- Step 4: Write run signal ---
            val signalContent = testFilter ?: "*"
            File(syncDir, "run.signal").writeText(signalContent)
            logger.lifecycle("Triggered solo test run: $signalContent")

            // --- Step 5: Wait for results (Player A only) ---
            val resultA = File(syncDir, "results-a.txt")
            val resultsDeadline = System.currentTimeMillis() + 180000
            while (System.currentTimeMillis() < resultsDeadline) {
                if (resultA.exists()) break
                if (startedClients && processes.all { !it.isAlive }) break
                Thread.sleep(500)
            }

            // --- Step 6: Report results ---
            logger.lifecycle("\n=== Solo Test Results ===")
            val aResult = if (resultA.exists()) resultA.readText().trim() else "NO RESULT (timeout or crash)"
            logger.lifecycle("  Player A: $aResult")

            if (!aResult.startsWith("PASS")) {
                val logA = File(syncDir, "client-a-output.log")
                if (logA.exists()) logger.lifecycle("  Client A log: ${logA.absolutePath}")
                throw RuntimeException("Solo tests failed")
            }
            logger.lifecycle("  Solo tests PASSED")

        } finally {
            if (!noStart) {
                processes.forEach { it.destroyForcibly() }
                stopServer(serverProcess)
                logger.lifecycle("Solo teardown complete")
            }
        }
    }
}

// --- runDuoTests: two-player journeys with both clients ---

tasks.register("runDuoTests") {
    group = "verification"
    description = "Run two-player E2E journey tests (both clients)"
    dependsOn(":server:jar", ":client:build")

    doLast {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val gradlew = File(rootProject.projectDir, if (isWin) "gradlew.bat" else "gradlew").absolutePath
        val noStart = project.hasProperty("noStart")
        val harness = project.hasProperty("harness")
        val testFilter = project.findProperty("testFilter")?.toString()
        val coverage = project.hasProperty("coverage")
        val duoPackage = "com.disqt.disquests.test.integration.journeys.duo"

        val syncDir = File(rootProject.projectDir, "integration-sync")
        val serverDir = file("../server/run")
        val processes = mutableListOf<Process>()
        var serverProcess: Process? = null
        var startedClients = false

        // --- Step 1: Clean sync directory ---
        if (syncDir.exists()) syncDir.listFiles()?.forEach { it.delete() }
        syncDir.mkdirs()
        logger.lifecycle("Cleaned sync directory")

        try {
            // --- Step 2: Server ---
            if (!noStart) {
                val pluginJar = file("../server/build/libs/server.jar")
                serverProcess = ensureServer(serverDir, logger, pluginJar, minecraft_version)
                rconReset(logger)
            } else {
                // -PnoStart: verify both clients are ready
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
                syncDir.listFiles()?.filter { !it.name.startsWith("client-") }?.forEach { it.delete() }
                rconReset(logger)
            }

            // --- Step 3: Launch both clients ---
            if (!noStart) {
                val clientAReady = File(syncDir, "client-a-ready.done").exists()
                val clientBReady = File(syncDir, "client-b-ready.done").exists()

                if (!clientAReady || !clientBReady) {
                    file("run-b").mkdirs()

                    val procA = launchClient(
                        gradlew, isWin, rootProject.projectDir,
                        "HarnessPlayerA", "IntTestPlayerA", ":client:runClientGameTest",
                        harness, coverage, duoPackage, logger
                    )
                    val procB = launchClient(
                        gradlew, isWin, rootProject.projectDir,
                        "HarnessPlayerB", "IntTestPlayerB", ":client:runClientGameTestB",
                        harness, coverage, duoPackage, logger
                    )
                    processes.addAll(listOf(procA, procB))
                    startedClients = true

                    val logA = File(syncDir, "client-a-output.log")
                    val logB = File(syncDir, "client-b-output.log")
                    Thread {
                        logA.outputStream().use { out ->
                            procA.inputStream.copyTo(out)
                        }
                    }.apply { isDaemon = true; start() }
                    Thread {
                        logB.outputStream().use { out ->
                            procB.inputStream.copyTo(out)
                        }
                    }.apply { isDaemon = true; start() }

                    if (harness) {
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
                        rconReset(logger)
                    }
                } else {
                    logger.lifecycle("Clients already running (ready markers found)")
                }
            }

            // --- Step 4: Write run signal ---
            val signalContent = testFilter ?: "*"
            File(syncDir, "run.signal").writeText(signalContent)
            logger.lifecycle("Triggered duo test run: $signalContent")

            // --- Step 5: Wait for results (both players) ---
            val resultA = File(syncDir, "results-a.txt")
            val resultB = File(syncDir, "results-b.txt")
            val resultsDeadline = System.currentTimeMillis() + 180000
            while (System.currentTimeMillis() < resultsDeadline) {
                if (resultA.exists() && resultB.exists()) break
                if (!harness && startedClients && processes.all { !it.isAlive }) break
                Thread.sleep(500)
            }

            // --- Step 6: Report results ---
            logger.lifecycle("\n=== Duo Test Results ===")
            val aResult = if (resultA.exists()) resultA.readText().trim() else "NO RESULT (timeout or crash)"
            val bResult = if (resultB.exists()) resultB.readText().trim() else "NO RESULT (timeout or crash)"
            logger.lifecycle("  Player A: $aResult")
            logger.lifecycle("  Player B: $bResult")

            val passed = aResult.startsWith("PASS") && bResult.startsWith("PASS")
            if (!passed) {
                val logA = File(syncDir, "client-a-output.log")
                val logB = File(syncDir, "client-b-output.log")
                if (logA.exists()) logger.lifecycle("  Client A log: ${logA.absolutePath}")
                if (logB.exists()) logger.lifecycle("  Client B log: ${logB.absolutePath}")
                throw RuntimeException("Duo tests failed")
            }
            logger.lifecycle("  Duo tests PASSED")

        } finally {
            if (!harness && !noStart) {
                processes.forEach { it.destroyForcibly() }
                stopServer(serverProcess)
                logger.lifecycle("Duo teardown complete")
            } else {
                logger.lifecycle("Harness mode: server and clients left running")
            }
        }
    }
}

// --- runIntegrationTest: runs solo then duo sequentially ---

tasks.register("runIntegrationTest") {
    group = "verification"
    description = "Run all integration E2E tests: solo first, then duo"
    dependsOn(":server:jar", ":client:build")

    doLast {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val gradlew = File(rootProject.projectDir, if (isWin) "gradlew.bat" else "gradlew").absolutePath

        fun runSubTask(taskName: String) {
            val cmd = mutableListOf<String>()
            if (isWin) cmd.addAll(listOf("cmd", "/c"))
            cmd.add(gradlew)
            cmd.addAll(listOf(taskName, "--no-daemon"))
            if (project.hasProperty("coverage")) cmd.add("-Pcoverage")
            if (project.hasProperty("noStart")) cmd.add("-PnoStart")
            if (project.hasProperty("harness")) cmd.add("-Pharness")
            if (project.hasProperty("testFilter")) cmd.add("-PtestFilter=${project.findProperty("testFilter")}")

            logger.lifecycle("=== Running $taskName ===")
            val process = ProcessBuilder(cmd)
                .directory(rootProject.projectDir)
                .redirectErrorStream(true)
                .start()
            // Pipe subprocess output through Gradle's logger so it appears in CI
            Thread {
                process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
            }.apply { isDaemon = true; start() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("$taskName failed with exit code $exitCode")
            }
        }

        runSubTask(":client:runSoloTests")
        runSubTask(":client:runDuoTests")
        logger.lifecycle("\n=== All integration tests PASSED ===")
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
