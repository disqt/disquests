# BuildNotes E2E Testing — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automated E2E testing of the BuildNotes Fabric client mod + PaperMC server plugin, running in GitHub Actions CI with screenshots.

**Architecture:** A `testmod` source set in the client module implements `FabricClientGameTest`. Tests connect to an external Paper server (started by CI), exercise the full UI flow (CRUD, sync, HUD pinning, permissions), and capture screenshots. RCON sends server commands for permission tests.

**Tech Stack:** Fabric Client GameTest API, Paper 1.21.11, Xvfb, GitHub Actions, RCON protocol.

**Spec:** `docs/specs/2026-03-15-buildnotes-e2e-testing-design.md`

**Important notes:**
- The Fabric Client GameTest API runs on a dedicated gametest thread. Use `runOnClient` for client-thread operations and `waitFor` for async waits.
- `TestDedicatedServerContext` starts an in-process vanilla server — we cannot use it for Paper. Instead, we connect to the external Paper server via `ConnectScreen` programmatically.
- `clickScreenButton(label)` matches by button text. Our buttons use translatable keys, so we must pass the resolved English text (e.g., `"Add"` not `"gui.buildnotes.add_button"`).
- The intermediary-mapped jar uses obfuscated names in javap output (e.g., `class_310` = `MinecraftClient`, `class_437` = `Screen`). The yarn-mapped source code uses the readable names.

---

## Chunk 1: Gradle & Test Mod Skeleton

### Task 1: Configure testmod source set and Loom run config

**Files:**
- Modify: `client/build.gradle.kts`

- [ ] **Step 1: Add testmod source set and Loom run config**

Add to `client/build.gradle.kts`:

```kotlin
sourceSets {
    create("testmod") {
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
}

loom {
    runs {
        create("clientGameTest") {
            client()
            configName = "Client Game Test"
            source(sourceSets.getByName("testmod"))
            vmArg("-Dbuildnotes.test.server.host=localhost")
            vmArg("-Dbuildnotes.test.server.port=25565")
            vmArg("-Dbuildnotes.test.rcon.port=25575")
            vmArg("-Dbuildnotes.test.rcon.password=testpassword")
        }
    }
}
```

- [ ] **Step 2: Verify Gradle sync**

```bash
./gradlew :client:classes
```

Expected: BUILD SUCCESSFUL (no testmod sources yet, just confirms config is valid).

- [ ] **Step 3: Commit**

```bash
git add client/build.gradle.kts
git commit -m "build(client): add testmod source set and clientGameTest run config"
```

### Task 2: Create test mod fabric.mod.json

**Files:**
- Create: `client/src/testmod/resources/fabric.mod.json`

- [ ] **Step 1: Create test mod metadata**

```json
{
  "schemaVersion": 1,
  "id": "buildnotes-test",
  "version": "1.0.0",
  "name": "BuildNotes E2E Tests",
  "environment": "client",
  "entrypoints": {
    "fabric-client-gametest": [
      "net.atif.buildnotes.test.BuildNotesE2ETest"
    ]
  },
  "depends": {
    "buildnotes": "*",
    "fabric-client-gametest-api-v1": "*"
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/testmod/
git commit -m "test: add test mod fabric.mod.json"
```

### Task 3: Create RCON client

**Files:**
- Create: `client/src/testmod/java/net/atif/buildnotes/test/RconClient.java`

- [ ] **Step 1: Implement minimal RCON client**

The RCON protocol (https://wiki.vg/RCON): TCP, packets are `length(4) + requestId(4) + type(4) + payload + \0\0`. Type 3 = login, type 2 = command. Response type 0 = command response, type 2 = login response.

```java
package net.atif.buildnotes.test;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RconClient implements AutoCloseable {
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;
    private int requestId = 1;

    public RconClient(String host, int port, String password) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new DataOutputStream(socket.getOutputStream());
        this.in = new DataInputStream(socket.getInputStream());
        sendPacket(3, password); // login
        RconResponse resp = readPacket();
        if (resp.requestId == -1) {
            throw new IOException("RCON authentication failed");
        }
    }

    public String command(String cmd) throws IOException {
        sendPacket(2, cmd);
        RconResponse resp = readPacket();
        return resp.payload;
    }

    private void sendPacket(int type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes("ASCII");
        int length = 4 + 4 + payloadBytes.length + 2; // requestId + type + payload + 2 null bytes
        ByteBuffer buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.putInt(requestId++);
        buf.putInt(type);
        buf.put(payloadBytes);
        buf.put((byte) 0);
        buf.put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private RconResponse readPacket() throws IOException {
        byte[] lenBytes = new byte[4];
        in.readFully(lenBytes);
        int length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] data = new byte[length];
        in.readFully(data);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int respId = buf.getInt();
        int type = buf.getInt();
        byte[] payload = new byte[length - 10]; // minus requestId(4) + type(4) + 2 nulls
        buf.get(payload);
        return new RconResponse(respId, type, new String(payload, "ASCII"));
    }

    private record RconResponse(int requestId, int type, String payload) {}

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/testmod/
git commit -m "test: add minimal RCON client for server commands"
```

### Task 4: Create TestHelper utilities

**Files:**
- Create: `client/src/testmod/java/net/atif/buildnotes/test/TestHelper.java`

- [ ] **Step 1: Implement shared test utilities**

```java
package net.atif.buildnotes.test;

import net.atif.buildnotes.client.ClientSession;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;

public class TestHelper {
    private static final String SERVER_HOST = System.getProperty("buildnotes.test.server.host", "localhost");
    private static final int SERVER_PORT = Integer.getInteger("buildnotes.test.server.port", 25565);
    private static final int RCON_PORT = Integer.getInteger("buildnotes.test.rcon.port", 25575);
    private static final String RCON_PASSWORD = System.getProperty("buildnotes.test.rcon.password", "testpassword");

    /**
     * Connect the client to the external Paper server.
     */
    public static void connectToServer(ClientGameTestContext context) {
        String address = SERVER_HOST + ":" + SERVER_PORT;
        context.runOnClient(client -> {
            ServerInfo serverInfo = new ServerInfo("Test Server", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address), serverInfo, false, null);
        });

        // Wait for the client to be in-game (no screen open = in world)
        context.waitFor(client -> client.currentScreen == null && client.world != null, 600); // 30 second timeout (600 ticks)
    }

    /**
     * Wait for the BuildNotes handshake to complete.
     */
    public static void waitForHandshake(ClientGameTestContext context) {
        context.waitFor(client -> ClientSession.isOnServer(), 200); // 10 second timeout
    }

    /**
     * Disconnect from the server.
     */
    public static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.world != null) {
                client.world.disconnect();
            }
        });
        // Wait for disconnect to complete (back to title/multiplayer screen)
        context.waitFor(client -> client.world == null, 200);
        context.waitTicks(20); // Let things settle
    }

    /**
     * Send an RCON command to the Paper server.
     */
    public static String rconCommand(String command) {
        try (RconClient rcon = new RconClient(SERVER_HOST, RCON_PORT, RCON_PASSWORD)) {
            return rcon.command(command);
        } catch (IOException e) {
            throw new RuntimeException("RCON command failed: " + command, e);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/testmod/
git commit -m "test: add TestHelper with server connection and RCON utilities"
```

---

## Chunk 2: Test Implementation

### Task 5: Create the E2E test class with smoke + handshake tests

**Files:**
- Create: `client/src/testmod/java/net/atif/buildnotes/test/BuildNotesE2ETest.java`

- [ ] **Step 1: Implement the test class with first two tests**

```java
package net.atif.buildnotes.test;

import net.atif.buildnotes.client.ClientCache;
import net.atif.buildnotes.client.ClientSession;
import net.atif.buildnotes.client.KeyBinds;
import net.atif.buildnotes.data.Note;
import net.atif.buildnotes.data.Scope;
import net.atif.buildnotes.gui.screen.MainScreen;
import net.atif.buildnotes.gui.screen.ViewNoteScreen;
import net.atif.buildnotes.hud.HudPinManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class BuildNotesE2ETest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        test1_smokeTest(context);
        test2_serverHandshake(context);
        test3_noteCrud(context);
        test4_buildCrud(context);
        test5_serverSync(context);
        test6_hudPinning(context);
        test7_permissions(context);
    }

    /**
     * Test 1: Mod loads, main screen opens.
     */
    private void test1_smokeTest(ClientGameTestContext context) {
        // Press the BuildNotes keybind (N)
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitTicks(5);

        // Verify main screen opened
        context.waitForScreen(MainScreen.class);

        // Take screenshot of empty main screen
        context.takeScreenshot("01_main_screen_empty");

        // Close the screen
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }

    /**
     * Test 2: Connect to Paper server, verify handshake.
     */
    private void test2_serverHandshake(ClientGameTestContext context) {
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        // Verify session state
        boolean onServer = context.computeOnClient(client -> ClientSession.isOnServer());
        if (!onServer) throw new AssertionError("Expected to be on server after handshake");

        boolean canEdit = context.computeOnClient(client -> ClientSession.hasEditPermission());
        if (!canEdit) throw new AssertionError("Expected CAN_EDIT permission by default");
    }

    /**
     * Test 3: Note CRUD via the UI.
     */
    private void test3_noteCrud(ClientGameTestContext context) {
        // Open main screen
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);

        // Click "Add" button
        context.clickScreenButton("Add");
        context.waitTicks(10);

        // Type title
        context.getInput().typeChars("Test Note");
        context.waitTicks(5);

        // Tab to content field and type content
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
        context.waitTicks(5);
        context.getInput().typeChars("Hello world");
        context.waitTicks(5);

        // Click Save
        context.clickScreenButton("Save");
        context.waitTicks(20); // Wait for server round-trip

        // Verify we're back on main screen
        context.waitForScreen(MainScreen.class);

        // Take screenshot of note in list
        context.takeScreenshot("03_note_in_list");

        // Open the note (double-click or click Open)
        context.clickScreenButton("Open");
        context.waitTicks(10);

        // Take screenshot of note view
        context.waitForScreen(ViewNoteScreen.class);
        context.takeScreenshot("03_note_view");

        // Click Delete
        context.clickScreenButton("Delete");
        context.waitTicks(5);
        // Confirm deletion
        context.clickScreenButton("Confirm");
        context.waitTicks(20);

        // Close back to game
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }

    /**
     * Test 4: Build CRUD via the UI.
     */
    private void test4_buildCrud(ClientGameTestContext context) {
        // Open main screen, switch to Builds tab
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Builds");
        context.waitTicks(5);

        // Click Add
        context.clickScreenButton("Add");
        context.waitTicks(10);

        // Type build name
        context.getInput().typeChars("Test Build");
        context.waitTicks(5);

        // Save
        context.clickScreenButton("Save");
        context.waitTicks(20);

        // Take screenshot
        context.waitForScreen(MainScreen.class);
        context.takeScreenshot("04_build_in_list");

        // Open the build
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.takeScreenshot("04_build_view");

        // Delete
        context.clickScreenButton("Delete");
        context.waitTicks(5);
        context.clickScreenButton("Confirm");
        context.waitTicks(20);

        // Close
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }

    /**
     * Test 5: Notes persist across reconnect.
     */
    private void test5_serverSync(ClientGameTestContext context) {
        // Create a note
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Notes");
        context.waitTicks(5);
        context.clickScreenButton("Add");
        context.waitTicks(10);
        context.getInput().typeChars("Persistent Note");
        context.waitTicks(5);
        context.clickScreenButton("Save");
        context.waitTicks(20);
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);

        // Disconnect and reconnect
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        // Verify note persists
        boolean found = context.computeOnClient(client -> {
            for (Note n : ClientCache.getNotes()) {
                if ("Persistent Note".equals(n.getTitle())) return true;
            }
            return false;
        });
        if (!found) throw new AssertionError("Persistent Note not found after reconnect");

        // Clean up: delete the note via RCON or leave for next test
    }

    /**
     * Test 6: HUD pin toggle.
     */
    private void test6_hudPinning(ClientGameTestContext context) {
        // Open a note
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Notes");
        context.waitTicks(5);

        // Open the existing note
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.waitForScreen(ViewNoteScreen.class);

        // Pin it
        context.clickScreenButton("Pin to HUD");
        context.waitTicks(5);

        // Close UI
        context.clickScreenButton("Close");
        context.waitTicks(10);

        // Take screenshot — HUD should be visible
        context.waitTicks(20); // Let render settle
        context.takeScreenshot("06_hud_pin_visible");

        // Verify pin manager state
        boolean pinned = context.computeOnClient(client -> HudPinManager.getPinnedNoteId() != null);
        if (!pinned) throw new AssertionError("Expected note to be pinned");

        // Unpin
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.waitForScreen(ViewNoteScreen.class);
        context.clickScreenButton("Unpin");
        context.waitTicks(5);
        context.clickScreenButton("Close");
        context.waitTicks(10);

        // Screenshot — HUD should be gone
        context.takeScreenshot("06_hud_pin_removed");

        boolean unpinned = context.computeOnClient(client -> HudPinManager.getPinnedNoteId() == null);
        if (!unpinned) throw new AssertionError("Expected note to be unpinned");
    }

    /**
     * Test 7: Permission enforcement.
     */
    private void test7_permissions(ClientGameTestContext context) {
        // Disable editing via RCON
        TestHelper.rconCommand("buildnotes allow_all false");

        // Reconnect to get new permissions
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        // Verify VIEW_ONLY
        boolean viewOnly = context.computeOnClient(client -> !ClientSession.hasEditPermission());
        if (!viewOnly) throw new AssertionError("Expected VIEW_ONLY after allow_all false");

        // Open UI, take screenshot showing disabled buttons
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);

        // Open the existing note to check disabled edit/delete buttons
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.takeScreenshot("07_view_only");

        // Close
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);

        // Re-enable editing via RCON
        TestHelper.rconCommand("buildnotes allow_all true");

        // Reconnect
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        // Verify CAN_EDIT
        boolean canEdit = context.computeOnClient(client -> ClientSession.hasEditPermission());
        if (!canEdit) throw new AssertionError("Expected CAN_EDIT after allow_all true");

        // Take screenshot showing enabled buttons
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.takeScreenshot("07_can_edit");

        // Clean up
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }
}
```

- [ ] **Step 2: Verify testmod compiles**

```bash
./gradlew :client:compileTestmodJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add client/src/testmod/
git commit -m "test: add E2E test class with all 7 test scenarios"
```

---

## Chunk 3: CI Pipeline

### Task 6: Create GitHub Actions workflow

**Files:**
- Create: `.github/workflows/e2e-test.yml`

- [ ] **Step 1: Create the workflow file**

```yaml
name: E2E Tests

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main]

jobs:
  e2e-test:
    runs-on: ubuntu-latest
    timeout-minutes: 15

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle.kts', 'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: gradle-

      - name: Cache Loom assets
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches/fabric-loom
          key: loom-${{ hashFiles('gradle.properties') }}
          restore-keys: loom-

      - name: Cache Paper server
        id: paper-cache
        uses: actions/cache@v4
        with:
          path: .ci/paper/paper.jar
          key: paper-1.21.11

      - name: Build all JARs
        run: ./gradlew build

      - name: Download Paper server
        if: steps.paper-cache.outputs.cache-hit != 'true'
        run: |
          mkdir -p .ci/paper
          BUILDS_JSON=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds")
          BUILD=$(echo "$BUILDS_JSON" | jq '.builds[-1].build')
          curl -sL "https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/${BUILD}/downloads/paper-1.21.11-${BUILD}.jar" -o .ci/paper/paper.jar

      - name: Configure Paper server
        run: |
          mkdir -p .ci/paper/plugins
          cp paper/build/libs/*.jar .ci/paper/plugins/BuildNotes.jar
          echo "eula=true" > .ci/paper/eula.txt
          cat > .ci/paper/server.properties << 'PROPS'
          online-mode=false
          enable-rcon=true
          rcon.password=testpassword
          rcon.port=25575
          level-type=flat
          spawn-protection=0
          max-players=2
          PROPS

      - name: Start Paper server
        run: |
          cd .ci/paper
          java -Xmx512m -jar paper.jar --nogui > server-stdout.log 2>&1 &
          echo $! > server.pid
          timeout 120 bash -c 'until grep -q "Done" logs/latest.log 2>/dev/null; do sleep 2; done'
          echo "Paper server started"

      - name: Run E2E tests
        run: |
          sudo apt-get install -y xvfb
          Xvfb :99 -ac -screen 0 854x480x24 &
          sleep 2
          DISPLAY=:99 ./gradlew runClientGameTest
        timeout-minutes: 10

      - name: Stop Paper server
        if: always()
        run: |
          if [ -f .ci/paper/server.pid ]; then
            kill "$(cat .ci/paper/server.pid)" 2>/dev/null || true
          fi

      - name: Upload test artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-results
          path: |
            client/build/run/clientGameTest/screenshots/
            client/build/run/clientGameTest/logs/
            .ci/paper/logs/
            .ci/paper/server-stdout.log
          retention-days: 14
```

- [ ] **Step 2: Add `.ci/` to `.gitignore`**

Add to `.gitignore`:

```
# CI artifacts
.ci/
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/e2e-test.yml .gitignore
git commit -m "ci: add E2E test workflow with Paper server, Xvfb, and caching"
```

### Task 7: Verify local build and push

- [ ] **Step 1: Verify full build passes locally**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL (all modules including testmod compilation).

- [ ] **Step 2: Push to trigger CI**

```bash
git push origin master
```

Monitor the GitHub Actions run. First run will take ~5 min (downloading Paper, Loom assets). Check the uploaded artifacts for screenshots and logs.

- [ ] **Step 3: Debug and fix**

If tests fail:
1. Download the `e2e-results` artifact from GitHub Actions
2. Check `client/build/run/clientGameTest/logs/` for client errors
3. Check `.ci/paper/logs/latest.log` for server errors
4. Check screenshots to see what the client rendered
5. Adjust timeouts, button labels, or test flow as needed

- [ ] **Step 4: Once passing, commit any fixes**

```bash
git add -A
git commit -m "fix: E2E test adjustments from CI run"
```

---

## Chunk 4: Screenshot Regression (optional, after tests pass)

### Task 8: Set up golden image comparison

**Files:**
- Create: `client/src/testmod/resources/golden/` (directory for reference screenshots)

- [ ] **Step 1: Run tests once to generate baseline screenshots**

Download screenshots from the first passing CI run's `e2e-results` artifact.

- [ ] **Step 2: Copy baseline screenshots to golden directory**

```bash
mkdir -p client/src/testmod/resources/golden/
cp <downloaded-screenshots>/*.png client/src/testmod/resources/golden/
```

- [ ] **Step 3: Replace `takeScreenshot` calls with `assertScreenshotEquals`**

In `BuildNotesE2ETest.java`, change calls like:

```java
context.takeScreenshot("01_main_screen_empty");
```

to:

```java
context.assertScreenshotEquals("01_main_screen_empty");
```

This compares against the golden image in `client/src/testmod/resources/golden/01_main_screen_empty.png` and fails the test if they differ beyond the tolerance threshold.

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/resources/golden/
git add client/src/testmod/java/
git commit -m "test: add golden image comparison for screenshot regression"
```
