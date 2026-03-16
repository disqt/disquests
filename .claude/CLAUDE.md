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

The old testmod (`client/src/testmod/`) was deleted as part of the Disquests rewrite. E2E tests need to be rewritten against the new protocol and quest model. The `runClientGameTest` Loom run config has been removed from `client/build.gradle.kts`.

## Networking Protocol

Channel: `disquests:main`. First byte = PacketType ID.

**C2S**: REQUEST_SYNC, SAVE_QUEST, DELETE_QUEST, JOIN_QUEST, REQUEST_COLLABORATION, RESPOND_COLLABORATION, UPDATE_CONTRIBUTORS, UPDATE_VISIBILITY, PIN_QUEST

**S2C**: HANDSHAKE, SYNC_MY_QUESTS, SYNC_SERVER_QUESTS, UPDATE_QUEST, DELETE_QUEST_S2C, COLLABORATION_REQUEST, COLLABORATION_RESPONSE

## Key Files

| File | Purpose |
|------|---------|
| `common/src/main/java/com/disqt/disquests/common/PacketCodec.java` | All packet encode/decode methods |
| `common/src/main/java/com/disqt/disquests/common/PacketType.java` | Packet type enum with byte IDs |
| `common/src/main/java/com/disqt/disquests/common/model/` | QuestData, Visibility, ContributorData, ContributorOp, CoordinatesData, CollaborationRequestData |
| `client/src/main/java/com/disqt/disquests/client/DisquestsClient.java` | Fabric mod entrypoint, keybinds, channel registration |
| `client/src/main/java/com/disqt/disquests/client/ClientSession.java` | Tracks connection state, dispatches S2C packets |
| `client/src/main/java/com/disqt/disquests/client/ClientCache.java` | Client-side quest cache |
| `client/src/main/java/com/disqt/disquests/client/gui/screen/` | MainScreen, EditQuestScreen, ViewQuestScreen, ContributorScreen, ConfirmScreen |
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

- **Paper plugin**: `scp paper/build/libs/disquests-paper-*.jar minecraft:~/server/plugins/Disquests.jar`
- **Client mod**: distribute `client/build/libs/disquests-*.jar` to players for Fabric `mods/` directory

## Release

Tag-triggered via GitHub Actions (`.github/workflows/release.yml`). Pushes a `v*` tag, runs E2E tests, generates changelog from conventional commits, creates GitHub release with `disquests-client-{version}.jar` and `disquests-paper-{version}.jar`.

```bash
git tag v0.1.0
git push origin v0.1.0
```
