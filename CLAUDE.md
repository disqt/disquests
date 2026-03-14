# BuildNotes — Fabric Client + Paper Plugin

Fork of [BuildNotes](https://github.com/Atif85/buildnotes-mod) (MIT) split into a Fabric client mod + PaperMC server plugin, communicating via vanilla plugin messages on `buildnotes:main`.

## Architecture

```
common/   — shared PacketCodec, data models, no platform deps (Java 17)
client/   — Fabric mod (forked from upstream), networking rewritten to RawPayload
paper/    — Paper plugin: SQLite storage, permissions, packet handler, commands
```

Both `client` and `paper` depend on `common`. The client registers a single `RawPayload` CustomPayload type that wraps raw bytes; the Paper plugin uses `Messenger.registerIncomingPluginChannel()`. Both sides use `PacketCodec` from common for serialization.

## Current Status

- **common**: DONE — PacketCodec with all 16 packet types, round-trip tests passing
- **paper**: DONE — BuildNotesPlugin, DataManager (SQLite), PermissionManager, ServerPacketHandler, Commands
- **client networking rewrite**: DONE — RawPayload, ClientPacketHandler, server code removed
- **BLOCKED**: 45 compilation errors in GUI classes from MC API changes between upstream's yarn build and current mappings
- **HUD pinning**: NOT STARTED
- **E2E tests**: NOT STARTED — plan: HeadlessMC for full client+server testing

## Build

```bash
./gradlew build              # build all
./gradlew :common:test       # run codec tests
./gradlew :client:build      # build Fabric mod jar
./gradlew :paper:build       # build Paper plugin jar
```

## Target Versions

- Minecraft: 1.21.11
- Yarn: 1.21.11+build.1 (upstream used build.3, but both produce same errors)
- Fabric Loader: 0.18.2
- Fabric API: 0.139.5+1.21.11
- Loom: 1.14.10
- Gradle: 9.4.0
- Java: 17
- Paper API: 1.21.11-R0.1-SNAPSHOT

## The 45 Compilation Errors

All in GUI classes, all from MC API method signature changes. The upstream was built against a yarn/Fabric API version where these methods had different signatures. The errors are in:

- `ScrollableScreen.java` — mouseClicked/mouseDragged/mouseReleased/keyPressed signatures changed
- `MultiLineTextFieldWidget.java` — same + `Screen.hasShiftDown()` moved
- `AbstractListWidget.java`, `BuildListWidget.java`, `NoteListWidget.java` — render/mouse signatures
- `DarkButtonWidget.java`, `TabButtonWidget.java` — `renderWidget` became something else, new `drawIcon` abstract method
- `EditBuildScreen.java`, `ViewBuildScreen.java` — `NativeImageBackedTexture` constructor changed
- `KeyBinds.java` — `KeyBinding` category parameter type changed

Fix approach: look up the actual MC 1.21.11 API signatures via jdtls hover/goToDefinition on the parent classes and update the method signatures to match.

## Networking Protocol

Channel: `buildnotes:main`. First byte = PacketType ID.

**C2S**: REQUEST_DATA, SAVE_NOTE, DELETE_NOTE, SAVE_BUILD, DELETE_BUILD, UPLOAD_IMAGE_CHUNK, REQUEST_IMAGE
**S2C**: HANDSHAKE, INITIAL_SYNC, UPDATE_NOTE, DELETE_NOTE_S2C, UPDATE_BUILD, DELETE_BUILD_S2C, IMAGE_CHUNK, IMAGE_NOT_FOUND, UPDATE_PERMISSION

## Key Files

| File | Purpose |
|------|---------|
| `common/.../PacketCodec.java` | All 16 packet encode/decode methods |
| `common/.../model/NoteData.java` | Shared note record |
| `common/.../model/BuildData.java` | Shared build record |
| `client/.../network/RawPayload.java` | Single CustomPayload wrapper |
| `client/.../network/ClientPacketHandler.java` | Dispatches S2C packets, has Note/Build converters |
| `client/.../data/DataManager.java` | Client-side data manager (3-scope merge + send C2S) |
| `paper/.../BuildNotesPlugin.java` | Plugin entry, channel registration |
| `paper/.../ServerPacketHandler.java` | Handles C2S, broadcasts S2C |
| `paper/.../DataManager.java` | SQLite persistence |

## Upstream

Remote `upstream` points to `https://github.com/Atif85/buildnotes-mod.git`. The upstream's networking is completely rewritten — only UI, data model, and bug fix changes can be cherry-picked.

## Docs

- Spec: `docs/specs/2026-03-14-buildnotes-paper-fork-design.md`
- Plan: `docs/plans/2026-03-14-buildnotes-paper-fork.md`

## Deploy

- **Paper plugin**: `scp paper/build/libs/*.jar minecraft:~/server/plugins/BuildNotes.jar`
- **Client mod**: distribute jar to players for Fabric `mods/` directory
