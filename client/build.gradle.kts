import java.io.DataInputStream
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder

plugins {
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    jacoco
    eclipse
}

eclipse {
    classpath {
        file.whenMerged {
            val cp = this as org.gradle.plugins.ide.eclipse.model.Classpath
            cp.entries.add(
                org.gradle.plugins.ide.eclipse.model.SourceFolder(
                    "build/generated/sources/annotationProcessor/java/main", "bin/main"
                )
            )
        }
    }
}

repositories {
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.wispforest.io/releases/")
    maven("https://jitpack.io")
}

val minecraft_version: String by project
val fabric_loader_version: String by project
val fabric_api_version: String by project

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    implementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    implementation(project(":common"))
    include(project(":common"))

    implementation(libs.lwjgl.tinyfd)
    include(libs.lwjgl.tinyfd)

    // Native libraries for file dialogs
    val tinyfdVersion = libs.versions.lwjgl.tinyfd.get()
    listOf("natives-linux", "natives-windows", "natives-macos", "natives-macos-arm64").forEach { classifier ->
        runtimeOnly("org.lwjgl:lwjgl-tinyfd:$tinyfdVersion:$classifier")
        include("org.lwjgl:lwjgl-tinyfd:$tinyfdVersion:$classifier")
    }

    // Mod Menu (optional at runtime)
    val modmenu_version: String by project
    compileOnly("com.terraformersmc:modmenu:$modmenu_version")

    // owo-lib UI framework
    val owo_version: String by project
    implementation("io.wispforest:owo-lib:$owo_version")
    annotationProcessor("io.wispforest:owo-lib:$owo_version")
    include("io.wispforest:owo-sentinel:$owo_version")

    // Markdown rendering
    implementation(libs.commonmark)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.tasklist)
    implementation(libs.commonmark.autolink)
    include(libs.commonmark)
    include(libs.commonmark.strikethrough)
    include(libs.commonmark.tasklist)
    include(libs.commonmark.autolink)
    include(libs.autolink)

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
    // Unit tests (standard test source set, no Minecraft deps)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // JUnit 5 for integration test harness (programmatic launcher inside MC client)
    "testmodImplementation"(libs.junit.jupiter)
    "testmodImplementation"(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val testServerPort = providers.gradleProperty("testServerPort").getOrElse("25565")
val testRconPort = providers.gradleProperty("testRconPort").getOrElse("25575")

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
            vmArg("-Ddisquests.test.server.port=$testServerPort")
            vmArg("-Ddisquests.test.rcon.port=$testRconPort")
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
            vmArg("-Ddisquests.test.server.port=$testServerPort")
            vmArg("-Ddisquests.test.rcon.port=$testRconPort")
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

tasks.named<net.fabricmc.loom.task.RunGameTask>("runClientGameTest") {
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
    description = "Generate combined coverage report from client + server E2E tests"
    dependsOn("compileJava", ":server:compileJava", ":common:compileJava")

    // Merge all exec files: E2E client + E2E server + common unit tests + server unit tests
    val execFiles = files(
        layout.buildDirectory.file("jacoco/gameTest.exec").get().asFile,
        file("../server/run/jacoco-server.exec"),
        file("../common/build/jacoco/test.exec"),
        file("../server/build/jacoco/test.exec")
    ).filter { it.exists() }
    executionData(execFiles)

    // Use classdump dir for server classes (Paper transforms bytecode on load,
    // so compiled classes don't match the exec data -- classdumpdir captures the actual runtime classes)
    val serverClassDump = file("../server/run/jacoco-classdump")

    sourceDirectories.from(
        files("src/main/java"),
        files("../server/src/main/java"),
        files("../common/src/main/java")
    )
    classDirectories.from(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            include("com/disqt/disquests/**")
        },
        if (serverClassDump.exists()) fileTree(serverClassDump) {
            include("com/disqt/disquests/server/**")
        } else fileTree("../server/build/classes/java/main") {
            include("com/disqt/disquests/**")
        },
        fileTree("../common/build/classes/java/main") {
            include("com/disqt/disquests/**")
        }
    )

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/integrationTest/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/integrationTest/report.xml"))
        csv.outputLocation.set(layout.buildDirectory.file("reports/jacoco/integrationTest/report.csv"))
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

    // Download paper.jar from Paper API (v3 at fill.papermc.io)
    logger.lifecycle("Downloading Paper $mcVersion...")
    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL).build()
    val buildsResp = client.send(
        HttpRequest.newBuilder()
            .uri(URI("https://fill.papermc.io/v3/projects/paper/versions/$mcVersion/builds"))
            .header("User-Agent", "disquests/${project.version} (github.com/disqt/disquests)")
            .GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )
    val urlMatch = Regex(""""url"\s*:\s*"([^"]+)"""").find(buildsResp.body())
        ?: throw RuntimeException("Could not parse Paper API response for MC $mcVersion")
    val downloadUrl = urlMatch.groupValues[1]
    logger.lifecycle("Downloading $downloadUrl")
    client.send(
        HttpRequest.newBuilder()
            .uri(URI(downloadUrl))
            .header("User-Agent", "disquests/${project.version} (github.com/disqt/disquests)")
            .GET().build(),
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
        |rcon.port=$testRconPort
        |server-port=$testServerPort
        |level-type=flat
        |spawn-protection=0
        |difficulty=peaceful
        |""".trimMargin()
    )
    logger.lifecycle("Server directory bootstrapped at ${serverDir.absolutePath}")
}

fun ensureServer(serverDir: File, logger: org.gradle.api.logging.Logger, pluginJar: File, mcVersion: String, coverageAgentJar: File? = null): Process? {
    val serverRunning = try {
        Socket("localhost", testServerPort.toInt()).use { true }
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
        val cmd = mutableListOf("java", "-Xmx1G", "-Ddisquests.debug=true")
        if (coverageAgentJar != null) {
            val execFile = File(serverDir, "jacoco-server.exec")
            val classDumpDir = File(serverDir, "jacoco-classdump")
            if (classDumpDir.exists()) classDumpDir.deleteRecursively()
            cmd.add("-javaagent:${coverageAgentJar.absolutePath}=destfile=${execFile.absolutePath},includes=com.disqt.disquests.*,append=true,classdumpdir=${classDumpDir.absolutePath}")
            logger.lifecycle("JaCoCo agent attached to server, writing to: $execFile")
        }
        cmd.addAll(listOf("-jar", paperJar.absolutePath, "--nogui"))
        val serverProcess = ProcessBuilder(cmd).directory(serverDir).redirectErrorStream(true).start()

        // Capture server stdout for debugging
        val serverLog = File(serverDir, "logs/server-stdout.log")
        Thread {
            serverLog.outputStream().use { out ->
                serverProcess.inputStream.copyTo(out)
            }
        }.apply { isDaemon = true; start() }

        // Wait for server to accept connections
        logger.lifecycle("Waiting for Paper server...")
        val serverPort = testServerPort.toInt()
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
        logger.lifecycle("Server already running on port $testServerPort")
        return null
    }
}

fun rconReset(logger: org.gradle.api.logging.Logger) {
    try {
        sendRconCommand("localhost", testRconPort.toInt(), "testpassword", "disquests reset")
        logger.lifecycle("Sent RCON reset")
        Thread.sleep(1000)
    } catch (e: Exception) {
        logger.warn("RCON reset failed: ${e.message}")
    }
}

fun ensureClientOptions(runDir: File) {
    val optionsFile = File(runDir, "options.txt")
    if (!optionsFile.exists()) {
        runDir.mkdirs()
        optionsFile.writeText("guiScale:1\n")
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
            // Wait up to 30s for graceful shutdown (JaCoCo writes .exec via shutdown hook)
            val deadline = System.currentTimeMillis() + 30_000
            while (serverProcess.isAlive && System.currentTimeMillis() < deadline) {
                Thread.sleep(1000)
            }
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
                val agentJar = if (coverage) jacocoRuntime.singleFile else null
                serverProcess = ensureServer(serverDir, logger, pluginJar, minecraft_version, agentJar)
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
                ensureClientOptions(file("run"))
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
            val resultsDeadline = System.currentTimeMillis() + 300000 // 5 minutes
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
                val agentJar = if (coverage) jacocoRuntime.singleFile else null
                serverProcess = ensureServer(serverDir, logger, pluginJar, minecraft_version, agentJar)
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
                    ensureClientOptions(file("run"))
                    ensureClientOptions(file("run-b"))

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
            val resultsDeadline = System.currentTimeMillis() + 300000 // 5 minutes
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
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
