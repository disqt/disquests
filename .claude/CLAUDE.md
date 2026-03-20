# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Disquests** — a Fabric client mod + PaperMC server plugin for tracking in-game quests. Communicates via vanilla plugin messages on `disquests:main`.

Originally forked from [BuildNotes](https://github.com/Atif85/buildnotes-mod) (MIT), but fully rewritten as its own project with a new data model, networking protocol, and feature set.

## Architecture

```
common/   — shared PacketCodec, data models, no platform deps
client/   — Fabric mod, networking via RawPayload, markdown rendering via commonmark-java
paper/    — Paper plugin: SQLite storage, permissions, packet handler, commands
```

Both `client` and `paper` depend on `common`. The client registers a single `RawPayload` CustomPayload type that wraps raw bytes; the Paper plugin uses `Messenger.registerIncomingPluginChannel()`. Both sides use `PacketCodec` from common for serialization.

**All modules share the same package namespace**: `com.disqt.disquests.*`
- Common: `com.disqt.disquests.common.*`
- Paper: `com.disqt.disquests.paper.*`
- Client: `com.disqt.disquests.client.*`

## Build

All versions (MC, Fabric, Paper, Java) are in `gradle.properties` — that is the source of truth.

```bash
./gradlew build              # build all
./gradlew :common:test       # run codec unit tests (JUnit 5)
./gradlew :client:build      # build Fabric mod jar
./gradlew :paper:build       # build Paper plugin jar
./gradlew :paper:runServer   # start Paper dev server (auto-downloads Paper, places plugin jar)
```

`runServer` has a 4GB free RAM gate — it will refuse to start on low-memory machines (Pi, VPS). The check and threshold are in `build.gradle.kts` via `requireFreeRam`.

## E2E Tests

Client game tests live in `client/src/testmod/java/com/disqt/disquests/test/QuestScreenTest.java`. Run via:

```bash
./gradlew :client:runClientGameTest   # requires Paper dev server running
```

Tests use `FabricClientGameTest` API: `setScreen`, `waitForScreen`, `computeOnClient`, `runOnClient`. Quests must be added to `ClientCache` before opening screens to prevent auto-close (`tick()` closes screens when quest is not in cache).

- **Use `TestInput` for click simulation** -- `context.getInput().setCursorPos(x * scale, y * scale)` + `context.getInput().pressMouse(GLFW_MOUSE_BUTTON_LEFT)`. Never call `screen.mouseClicked()` directly -- it bypasses the real input pipeline and gives false test results.
- **GLFW uses physical pixels** -- multiply logical coordinates by `client.getWindow().getScaleFactor()` before passing to `setCursorPos`.
- **`TestLogCapture`** -- attach to any logger name, captures Log4j2 events at DEBUG level for assertions: `TestLogCapture.attach("Disquests/QuestEntry")` then `capture.hasMessageContaining("...")`.
- **`DebugScreenEvents`** -- registered at mod init, logs all mouse events on Disquests screens at DEBUG level via Fabric `ScreenMouseEvents` hooks. Enable by setting `Disquests` logger to DEBUG in log4j2 config.

## Networking Protocol

Channel: `disquests:main`. First byte = PacketType ID.

**C2S**: REQUEST_SYNC, SAVE_QUEST, DELETE_QUEST, JOIN_QUEST, REQUEST_COLLABORATION, RESPOND_COLLABORATION, UPDATE_CONTRIBUTORS, UPDATE_VISIBILITY, PIN_QUEST, LEAVE_QUEST

**S2C**: HANDSHAKE, SYNC_MY_QUESTS, SYNC_SERVER_QUESTS, UPDATE_QUEST, DELETE_QUEST_S2C, COLLABORATION_REQUEST, COLLABORATION_RESPONSE, SYNC_PENDING_REQUESTS

## Key Files

| File | Purpose |
|------|---------|
| `common/src/main/java/com/disqt/disquests/common/PacketCodec.java` | All packet encode/decode methods |
| `common/src/main/java/com/disqt/disquests/common/PacketType.java` | Packet type enum with byte IDs |
| `common/src/main/java/com/disqt/disquests/common/model/` | QuestData, Visibility, ContributorData, ContributorOp, CoordinatesData, CollaborationRequestData |
| `client/src/main/java/com/disqt/disquests/client/DisquestsClient.java` | Fabric mod entrypoint, keybinds, channel registration |
| `client/src/main/java/com/disqt/disquests/client/ClientSession.java` | Tracks connection state, dispatches S2C packets |
| `client/src/main/java/com/disqt/disquests/client/ClientCache.java` | Client-side quest cache |
| `client/src/main/java/com/disqt/disquests/client/gui/screen/` | MainScreen, QuestScreen (unified view/edit), ContributorScreen, ConfirmScreen |
| `client/src/main/java/com/disqt/disquests/client/gui/helper/ColorConfig.java` | Color config loader (parses hex/rgb/rgba strings) |
| `paper/src/main/java/com/disqt/disquests/paper/DisquestsPlugin.java` | Plugin entry, channel registration |
| `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java` | Handles C2S, broadcasts S2C |
| `paper/src/main/java/com/disqt/disquests/paper/DataManager.java` | SQLite persistence |
| `paper/src/main/java/com/disqt/disquests/paper/PlayerNameTracker.java` | Tracks online player display names |

## Dependencies

- **commonmark-java** (`org.commonmark:commonmark:0.27.1` + ext-gfm-strikethrough + ext-task-list-items): Markdown rendering in the client. Bundled via Loom `include`.
- **sqlite-jdbc** (`org.xerial:sqlite-jdbc:3.51.2.0`): Paper-side SQLite. `compileOnly` (bundled in Paper env).

## Gotchas

- **Gradle Kotlin DSL shadows `java` package** — `java.lang.management.*` won't resolve in `.gradle.kts` because `java` is a Gradle DSL accessor. Use `Class.forName("java.lang.management.ManagementFactory")` instead.
- **PR target** — origin is `disqt/disquests`. No upstream remote.

## Deploy

- **Paper plugin**: `scp paper/build/libs/paper.jar minecraft:~/serverfiles/plugins/Disquests.jar` then `ssh minecraft "tmux -S /tmp/tmux-1000/pmcserver-bb664df1 send-keys -t pmcserver 'plugman reload Disquests' Enter"`
- **Client mod**: `cp client/build/libs/client.jar "C:/Users/leole/AppData/Roaming/PrismLauncher/instances/1.21.11 v2.7/.minecraft/mods/disquests-client-0.2.4.jar"`

## Release

Tag-triggered via GitHub Actions (`.github/workflows/release.yml`). Pushes a `v*` tag, runs E2E tests, generates changelog from conventional commits, creates GitHub release with `disquests-client-{version}.jar` and `disquests-paper-{version}.jar`.

```bash
git tag v0.2.4
git push origin v0.2.4
```

## References

Library docs and design specs live in the repo, not in memory or CLAUDE.md:

| Path | Contents |
|------|----------|
| `docs/references/owo-ui.md` | owo-ui API reference (layout, components, surfaces, animations, XML hot-reload) |
| `docs/superpowers/specs/` | Design specs for features |
| `docs/superpowers/plans/` | Implementation plans |

Read these on-demand when working on the relevant area. For owo-ui, Context7 MCP can also fetch live docs (`resolve-library-id` for "owo-lib").

## Gotchas (additional)

- **MC 1.21.11 ClickEvent API** — sealed classes, not enum. Use `new ClickEvent.OpenUrl(URI)` and `instanceof ClickEvent.OpenUrl openUrl` for pattern matching.
- **Contributor is immutable** — `canEdit` is final. To update, replace with `new Contributor(new ContributorData(...))`.
- **QuestScreen auto-close** — `tick()` closes the screen if the quest is not in `ClientCache`. E2E tests must add quests to cache before opening screens.
- **owo-ui v0.13.0 renames** — `BaseComponent` -> `BaseUIComponent`, `Components` -> `UIComponents`, `Containers` -> `UIContainers`, `OwoUIDrawContext` -> `OwoUIGraphics`. XML tags unchanged.
- **owo-ui `onMouseDown` coordinates are relative** — `Click.x()`/`Click.y()` are already relative to the component. Do NOT subtract `this.x()`/`this.y()`.
- **owo-ui XML scroll container** — child element must be FIRST (before `<sizing>`, `<surface>`, `<padding>`). `WrappingParentUIComponent.parseProperties` takes first element child.
- **owo-ui XML requires `<components>` wrapper** — root component must be inside `<components>` tag, not directly under `<owo-ui>`.
- **MC 1.21.11 `Click` record** — `Click(double x, double y, MouseInput buttonInfo)` where `MouseInput(int button, int modifiers)`. Not `(double, double, int)`.
- **No owo-ui visibility API** — use `component.sizing(Sizing.fixed(0), Sizing.fixed(0))` to hide, `Sizing.content()` to show.
