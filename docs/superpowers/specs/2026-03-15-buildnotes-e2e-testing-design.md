# BuildNotes E2E Testing — Design Spec

## Overview

Automated end-to-end testing for the BuildNotes Fabric client mod + PaperMC server plugin. Tests run in GitHub Actions CI using the Fabric Client GameTest Framework, a real Paper server, and Xvfb for headless rendering. Screenshots are captured for visual regression testing.

## Goals

1. **Protocol correctness** — verify the client and server correctly exchange packets (handshake, sync, CRUD, permissions, image chunks) over the `buildnotes:main` plugin channel
2. **Full stack integration** — a real Fabric client connects to a real Paper server, packets flow end-to-end, GUI shows correct data
3. **UX validation** — screens open, buttons work, text fields accept input, HUD overlay renders
4. **Visual regression** — golden-image screenshots catch unintended UI changes

## Architecture

### Components

```
GitHub Actions Runner (ubuntu-latest, Java 21)
├── Xvfb :99 (virtual framebuffer for MC rendering)
├── Paper 1.21.11 Server (localhost:25565)
│   ├── BuildNotes plugin JAR (from build)
│   ├── RCON enabled (port 25575, for sending commands from tests)
│   └── online-mode=false (no Mojang auth in CI)
└── Minecraft Client (via ./gradlew runClientGameTest)
    ├── BuildNotes client mod (from build)
    └── BuildNotes test mod (client/src/testmod/)
        └── Implements FabricClientGameTest
```

### Technology choices

| Component | Technology | Why |
|-----------|-----------|-----|
| Client test framework | Fabric Client GameTest API (`net.fabricmc.fabric.api.client.gametest.v1`) | Official Fabric testing API. Provides `TestInput` for simulating mouse/keyboard, `takeScreenshot()` for golden images, runs a real MC client with mods loaded. |
| Server | Real Paper 1.21.11 | Tests the actual plugin code, not mocks. Validates the full packet protocol end-to-end. |
| Display | Xvfb | Pre-installed on GitHub Actions runners. Lightweight virtual framebuffer, no GPU needed. |
| CI | GitHub Actions | Repository already on GitHub. `actions/cache` for aggressive caching. |
| Server commands | RCON | Paper has built-in RCON. Needed for permission tests (`/buildnotes allow_all`). |

### Test mod structure

The test mod lives alongside the client mod in the same Gradle subproject, using a `testmod` source set (standard Fabric convention):

```
client/
├── src/main/          # BuildNotes client mod (production code)
├── src/testmod/
│   ├── java/net/atif/buildnotes/test/
│   │   ├── BuildNotesE2ETest.java       # FabricClientGameTest implementation
│   │   ├── TestHelper.java              # Shared utilities (wait for sync, RCON client, etc.)
│   │   └── RconClient.java              # Minimal RCON client for sending server commands
│   └── resources/
│       └── fabric.mod.json              # Test mod metadata (depends on buildnotes)
```

## Test Scenarios

### 1. Smoke test — mod loads

- Client starts with BuildNotes mod loaded
- No crash on startup
- Press N keybind
- Main screen opens with Notes and Builds tabs visible
- Screenshot: `01_main_screen_empty.png`

### 2. Server handshake

- Client connects to `localhost:25565`
- Wait for handshake packet (S2C `HANDSHAKE` with permission level)
- Wait for initial sync packet (S2C `INITIAL_SYNC` with empty note/build lists)
- Verify `ClientSession.isOnServer()` returns true
- Verify `ClientSession.hasEditPermission()` returns true (default: allow_all or op)

### 3. Note CRUD

- Open main screen (press N), select Notes tab
- Click "Add" button
- Type title "Test Note" in title field
- Type content "Hello world\nLine 2" in content field
- Click "Save", verify return to main screen
- Verify "Test Note" appears in note list
- Screenshot: `03_note_in_list.png`
- Open the note, verify title and content match
- Screenshot: `03_note_view.png`
- Click "Edit", change title to "Updated Note", save
- Verify updated title in list
- Open note, click "Delete", confirm deletion
- Verify note removed from list

### 4. Build CRUD

- Switch to Builds tab
- Click "Add" button
- Set name "Test Build", coordinates "100 64 -200", dimension "minecraft:overworld"
- Set description "A test build"
- Click "Save"
- Verify "Test Build" appears in build list
- Screenshot: `04_build_in_list.png`
- Open the build, verify fields match
- Screenshot: `04_build_view.png`
- Edit description to "Updated description", save
- Delete the build, verify removal

### 5. Server sync (persistence)

- Create a note "Persistent Note" with content "Should survive reconnect"
- Disconnect from server
- Reconnect to `localhost:25565`
- Wait for initial sync
- Open main screen, verify "Persistent Note" exists in list with correct content

### 6. HUD pinning

- Create a note "Pin Test" with content "This should appear on screen"
- Open the note in view mode
- Click "Pin to HUD" button
- Close the UI (press Escape)
- Screenshot: `06_hud_pin_visible.png` — verify overlay renders in top-left
- Reopen the note
- Click "Unpin" button
- Close the UI
- Screenshot: `06_hud_pin_removed.png` — verify overlay is gone
- Verify pin persists: disconnect, reconnect, verify overlay returns

### 7. Permissions

- Via RCON: `/buildnotes allow_all false`
- Disconnect and reconnect (to get new permission level)
- Open main screen
- Verify "Add" button is present but creating a note shows restricted behavior
- Open an existing note, verify "Edit" and "Delete" buttons are disabled
- Screenshot: `07_view_only.png`
- Via RCON: `/buildnotes allow_all true`
- Disconnect and reconnect
- Verify "Edit" and "Delete" buttons are now enabled
- Screenshot: `07_can_edit.png`

### 8. Screenshot regression

Golden images are stored in `client/src/testmod/resources/golden/` and compared against captured screenshots. Key screenshots:

| Screenshot | What it validates |
|------------|------------------|
| `01_main_screen_empty.png` | Main screen layout, tab styling, empty state |
| `03_note_in_list.png` | Note list entry rendering, scope indicator |
| `03_note_view.png` | Note view screen layout, title/content panels |
| `04_build_in_list.png` | Build list entry rendering |
| `04_build_view.png` | Build edit screen layout |
| `06_hud_pin_visible.png` | HUD overlay position, background, text wrapping |
| `06_hud_pin_removed.png` | Clean HUD after unpin |
| `07_view_only.png` | Disabled buttons in VIEW_ONLY mode |
| `07_can_edit.png` | Enabled buttons with edit permission |

Comparison uses pixel-diff with a tolerance threshold (e.g., 1% pixel difference allowed) to account for minor rendering variations across runs.

## CI Pipeline

### GitHub Actions workflow: `.github/workflows/e2e-test.yml`

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
    steps:
      # --- Setup ---
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      # --- Caching ---
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
          path: |
            ~/.gradle/caches/fabric-loom
          key: loom-${{ hashFiles('gradle.properties') }}
          restore-keys: loom-

      - name: Cache Paper server
        uses: actions/cache@v4
        with:
          path: .ci/paper
          key: paper-1.21.11

      # --- Build ---
      - name: Build all JARs
        run: ./gradlew build

      # --- Paper server setup ---
      - name: Download Paper (if not cached)
        run: |
          mkdir -p .ci/paper/plugins
          if [ ! -f .ci/paper/paper.jar ]; then
            # Download Paper 1.21.11 via Paper API
            curl -sL "https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/$(curl -s https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds | jq '.builds[-1].build')/downloads/paper-1.21.11-$(curl -s https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds | jq '.builds[-1].build').jar" -o .ci/paper/paper.jar
          fi

      - name: Configure Paper server
        run: |
          cp paper/build/libs/*.jar .ci/paper/plugins/BuildNotes.jar
          echo "eula=true" > .ci/paper/eula.txt
          cat > .ci/paper/server.properties << 'EOF'
          online-mode=false
          enable-rcon=true
          rcon.password=testpassword
          rcon.port=25575
          level-type=flat
          spawn-protection=0
          max-players=2
          EOF

      - name: Start Paper server
        run: |
          cd .ci/paper
          java -Xmx512m -jar paper.jar --nogui &
          echo $! > server.pid
          # Wait for server to be ready
          timeout 120 bash -c 'until grep -q "Done" logs/latest.log 2>/dev/null; do sleep 2; done'

      # --- Client tests ---
      - name: Install Xvfb
        run: sudo apt-get install -y xvfb

      - name: Run E2E tests
        run: |
          Xvfb :99 -ac -screen 0 854x480x24 &
          DISPLAY=:99 ./gradlew runClientGameTest

      # --- Cleanup & artifacts ---
      - name: Stop Paper server
        if: always()
        run: |
          if [ -f .ci/paper/server.pid ]; then
            kill $(cat .ci/paper/server.pid) || true
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
```

### Caching strategy

| What | Cache key | Size | Changes when |
|------|-----------|------|-------------|
| Gradle wrapper + deps | Hash of `*.gradle.kts` + `gradle.properties` + wrapper props | ~200MB | Dependency versions change |
| Loom assets (MC JARs, yarn mappings, deobf) | Hash of `gradle.properties` (contains MC version + yarn version) | ~500MB | MC or yarn version bumps |
| Paper server JAR | `paper-1.21.11` (static) | ~50MB | MC version bump |

First CI run: ~5 min (downloads everything). Subsequent runs: ~2-3 min (only builds code + runs tests).

## Gradle Configuration

### Loom testmod setup in `client/build.gradle.kts`

Add to existing config:

```kotlin
loom {
    runs {
        named("clientGameTest") {
            client()
            configName = "Client Game Test"
            source(sourceSets.getByName("testmod"))
            // Connect to local Paper server
            vmArg("-Dbuildnotes.test.server.host=localhost")
            vmArg("-Dbuildnotes.test.server.port=25565")
            vmArg("-Dbuildnotes.test.rcon.port=25575")
            vmArg("-Dbuildnotes.test.rcon.password=testpassword")
        }
    }
}

sourceSets {
    create("testmod") {
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
}
```

### Test mod `fabric.mod.json`

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

## RCON Client

A minimal RCON client (~50 lines) for sending commands to the Paper server from within the test mod. Used for permission tests. Protocol is simple: TCP connection, login packet with password, then command packets.

The test reads connection details from system properties (`-Dbuildnotes.test.rcon.*`) set in the Gradle run config.

## Constraints

- **GitHub Actions runner has 7GB RAM** — Paper server (512MB) + MC client (~2GB) + Xvfb fits comfortably
- **No Mojang auth in CI** — `online-mode=false` on the Paper server, client uses offline mode
- **Test execution is sequential** — Fabric Client GameTest runs tests one at a time on a dedicated thread. This is fine for 8 tests.
- **Golden image updates** — when UI intentionally changes, developer updates golden images by running tests locally and copying new screenshots to `client/src/testmod/resources/golden/`
- **Pi considerations** — developers can run tests locally with Xvfb on the Pi, but the 8GB RAM is tight. CI is the primary execution environment.

## Out of Scope

- Image upload/download testing (requires file system setup, chunked transfer timing — defer to v2)
- Multi-client tests (two clients connected simultaneously)
- Performance/load testing
- Mobile/Bedrock testing
