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

**GUI migration state:** All screens migrated to owo-ui (XML layouts + `DisquestsBaseScreen`). MarkdownWidget ported to `BaseUIComponent`. MultiLineTextFieldWidget wrapped via `TextFieldComponent`.

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

UX-driven journey tests in `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/`. Run via:

```bash
./gradlew :client:runIntegrationTest                                          # full suite (auto-starts Paper)
./gradlew :client:runIntegrationTest -Pcoverage                               # with JaCoCo coverage
./gradlew :client:runIntegrationTest -PtestFilter=QuestLifecycleJourney       # single journey
./gradlew :client:runIntegrationTest -Pharness                                # keep clients alive for re-runs
./gradlew :client:runIntegrationTest -PnoStart -PtestFilter=ConfigJourney     # re-run on existing clients
./gradlew :client:jacocoIntegrationTestReport                                 # generate HTML report
```

Tests use a custom BDD DSL (`given`/`when`/`then`/`and`) with GLFW physical input via `TestInput`. All tests connect to a live Paper server -- no mocking.

- **`UIActions`** -- click, type, undo/redo, waitForScreen, openMainScreen, etc.
- **`UIAssertions`** -- assertLabelText, assertButtonText, assertEntryCount, assertScreenIs, etc.
- **Trust hierarchy:** UI state > component state > debug logs > cache state
- **Two-player journeys** use `PhaseSync.signal()`/`waitFor()` for coordination
- **`AbortOnFailureExtension`** skips remaining steps if a prior step fails

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
| `client/src/main/java/com/disqt/disquests/client/gui/screen/` | All screens use owo-ui: MainScreen, QuestScreen (view/edit), ContributorScreen, ConfirmScreen, ConfigScreen |
| `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java` | Shared owo-ui base screen with parent navigation |
| `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java` | Custom owo-ui component for quest list entries |
| `client/src/main/java/com/disqt/disquests/client/gui/component/TextFieldComponent.java` | BaseUIComponent wrapper for MultiLineTextFieldWidget |
| `client/src/main/java/com/disqt/disquests/client/gui/helper/Colors.java` | Color constants (AMBER, TEXT_PRIMARY, etc.) |
| `client/src/main/java/com/disqt/disquests/client/gui/helper/Theme.java` | Theme enum (VANILLA, FLAT, INSET, FROSTED, ACCENT_LINE) with color palettes and surfaces |
| `client/src/main/java/com/disqt/disquests/client/debug/DebugScreenEvents.java` | Fabric screen event hooks for debug logging |
| `client/src/main/resources/assets/disquests/owo_ui/*.xml` | XML UI models for all screens (hot-reloadable) |
| `paper/src/main/java/com/disqt/disquests/paper/DisquestsPlugin.java` | Plugin entry, channel registration |
| `paper/src/main/java/com/disqt/disquests/paper/ServerPacketHandler.java` | Handles C2S, broadcasts S2C |
| `paper/src/main/java/com/disqt/disquests/paper/DataManager.java` | SQLite persistence |
| `paper/src/main/java/com/disqt/disquests/paper/PlayerNameTracker.java` | Tracks online player display names |
| `paper/src/main/java/com/disqt/disquests/paper/Commands.java` | `/disquests reload` and `/disquests reset` (debug mode) |
| `client/src/testmod/.../integration/harness/` | JUnit 5 harness: TestContext, IntegrationTestExtension, annotations, RconClient |
| `client/src/testmod/.../integration/tests/` | JUnit test classes (Lifecycle, Discovery, Collaboration, Leave, Pin) |

## Dependencies

- **commonmark-java** (`org.commonmark:commonmark:0.27.1` + ext-gfm-strikethrough + ext-task-list-items): Markdown rendering in the client. Bundled via Loom `include`.
- **sqlite-jdbc** (`org.xerial:sqlite-jdbc:3.51.2.0`): Paper-side SQLite. `compileOnly` (bundled in Paper env).
- **owo-lib** (`io.wispforest:owo-lib:0.13.0+1.21.11`): UI framework for client screens. Runtime dependency (users install separately). owo-sentinel bundled as jar-in-jar for graceful fallback.

## Gotchas

- **Gradle Kotlin DSL shadows `java` package** — `java.lang.management.*` won't resolve in `.gradle.kts` because `java` is a Gradle DSL accessor. Use `Class.forName("java.lang.management.ManagementFactory")` instead.
- **PR target** — origin is `disqt/disquests`. No upstream remote.
- **MC 1.21.11 ClickEvent API** — sealed classes, not enum. Use `new ClickEvent.OpenUrl(URI)` and `instanceof ClickEvent.OpenUrl openUrl` for pattern matching.
- **MC 1.21.11 `Click` record** — `Click(double x, double y, MouseInput buttonInfo)` where `MouseInput(int button, int modifiers)`. Not `(double, double, int)`.
- **Contributor is immutable** — `canEdit` is final. To update, replace with `new Contributor(new ContributorData(...))`.
- **QuestScreen auto-close** — `tick()` closes the screen if the quest is not in `ClientCache`. E2E tests must add quests to cache before opening screens.
- **owo-ui v0.13.0 renames** — `BaseComponent` -> `BaseUIComponent`, `Components` -> `UIComponents`, `Containers` -> `UIContainers`, `OwoUIDrawContext` -> `OwoUIGraphics`. XML tags unchanged.
- **owo-ui `onMouseDown` coordinates are relative** — `Click.x()`/`Click.y()` are already relative to the component. Do NOT subtract `this.x()`/`this.y()`.
- **owo-ui XML scroll container** — child element must be FIRST (before `<sizing>`, `<surface>`, `<padding>`). `WrappingParentUIComponent.parseProperties` takes first element child.
- **owo-ui XML requires `<components>` wrapper** — root component must be inside `<components>` tag, not directly under `<owo-ui>`.
- **owo-ui zero-sizing doesn't hide buttons** — text still renders. Use `parent.removeChild(component)` instead of zero-sizing.
- **owo-ui keyboard input requires `GreedyInputUIComponent`** — custom `BaseUIComponent` subclasses that need key/char events must implement this marker interface, and the screen must override `charTyped()` to route to the focused greedy component (see `DisquestsBaseScreen.charTyped()`).
- **owo-ui delegate focus desync** — `MultiLineTextFieldWidget.focused` can be reset by `mouseClicked()` after `onFocusGained` sets it. Force `delegate.setFocused(true)` in `onKeyPress`/`onCharTyped` before forwarding.
- **owo-ui `ParentUIComponent` not `ParentComponent`** — v0.13.0 renamed to `ParentUIComponent`. Use this for `childById` when targeting `<scroll>` containers.
- **owo-ui Surface composing** — `Surface.flat(color).and(Surface.outline(color))` chains surfaces. Available: `flat`, `outline`, `blur`, `DARK_PANEL`, `PANEL_INSET`, `panelWithInset`, `VANILLA_TRANSLUCENT`, `BLANK`.
- **owo-ui no `clearAndInit()`** — to rebuild a screen after state change, reopen it: `client.setScreen(new MyScreen(parent))`. There is no method to re-trigger `build()` on an existing screen.
- **XML comments cannot contain `--`** — causes `SAXParseException`. Use commas instead.
- **Fire-and-forget C2S race** — `PacketSender.pinQuest()`, `respondCollaboration()` etc. have no server acknowledgment. Add `waitTicks(40)` before disconnect/re-sync if the server must process the packet first. UI code using optimistic cache updates (e.g. `ContributorScreen.respondToRequest` calls `removePendingRequest` locally) must be replicated in tests.
- **`ClientCache.getQuestById` searches both lists** — returns quests from myQuests AND serverQuests. After leaving an OPEN quest, it's removed from myQuests but still in serverQuests. Use `getMyQuests().stream()` to check membership specifically.

## Deploy

- **Paper plugin**: `scp paper/build/libs/paper.jar minecraft:~/serverfiles/plugins/Disquests.jar` then `ssh minecraft "tmux -S /tmp/tmux-1000/pmcserver-bb664df1 send-keys -t pmcserver 'plugman reload Disquests' Enter"`
- **Client mod**: `cp client/build/libs/client.jar "C:/Users/leole/AppData/Roaming/PrismLauncher/instances/1.21.11 v2.7/.minecraft/mods/disquests-client-0.2.4.jar"`
- **owo-lib (Prism)**: Must be in Prism mods folder alongside client mod. Find in `~/.gradle/caches/modules-2/files-2.1/io.wispforest/owo-lib/`.

## Integration Tests

Integration tests are now part of the E2E test suite described above. All tests run via `runIntegrationTest`. See **E2E Tests** for the full command reference and gotchas.

Key implementation notes:
- **JUnit 5 inside MC client** — `HarnessPlayerA`/`HarnessPlayerB` launch JUnit programmatically via `Launcher` API. Test classes in `journeys/` package use `@IntegrationTest` annotation.
- **`@PlayerA`/`@PlayerB` filtering** — `IntegrationTestExtension` (ExecutionCondition) skips methods tagged for the other player. Untagged methods run on both.
- **PhaseSync coordination** — file-based signals, MUST use `context.waitFor()` not `Thread.sleep()` (sleeping blocks packet processing). Has cross-client error propagation for fast-fail.
- **RCON reset** — `/disquests reset` (debug mode only) clears DB + resends handshakes. Used between test runs.
- **`-PtestFilter` not `-Ptest`** — Gradle reserves `-Ptest` for its built-in test task.
- **Loom property passing** — use `-P` (Gradle property) not `-D` (JVM system property) for subprocess invocation.
- **Prism mods incompatible with `runClient`** — production jars use intermediary mappings, dev environment uses named mappings. Mixin crashes guaranteed.
- **ModMenu incompatible with `runClient`** — ModMenu's TitleScreen mixin crashes in dev (named vs intermediary mappings). Use F6 keybind to open ConfigScreen instead.
- **`paper/run/server.properties` `max-players`** — must be >= 4 (two test clients + reconnect headroom)
- **testmod source set** — IDE (VSCode/Java LS) can't resolve JUnit imports in testmod files. This is cosmetic; Gradle compilation works fine.

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

