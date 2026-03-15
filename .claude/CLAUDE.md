# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of [BuildNotes](https://github.com/Atif85/buildnotes-mod) (MIT) split into a Fabric client mod + PaperMC server plugin, communicating via vanilla plugin messages on `buildnotes:main`.

## Architecture

```
common/   — shared PacketCodec, data models, no platform deps
client/   — Fabric mod (forked from upstream), networking rewritten to RawPayload
paper/    — Paper plugin: SQLite storage, permissions, packet handler, commands
```

Both `client` and `paper` depend on `common`. The client registers a single `RawPayload` CustomPayload type that wraps raw bytes; the Paper plugin uses `Messenger.registerIncomingPluginChannel()`. Both sides use `PacketCodec` from common for serialization.

**Package namespaces differ between modules** — client keeps the upstream namespace `net.atif.buildnotes.*`, while paper and common use `com.disqt.buildnotes.*`. Watch imports when working across modules.

## Build

All versions (MC, Fabric, Paper, Java) are in `gradle.properties` — that is the source of truth.

```bash
./gradlew build              # build all
./gradlew :common:test       # run codec unit tests (JUnit 5)
./gradlew :client:build      # build Fabric mod jar
./gradlew :paper:build       # build Paper plugin jar
```

## E2E Tests

Uses Fabric's `FabricClientGameTest` API — the test runs inside a real Minecraft client that connects to a Paper server. Not headless; requires a display (Xvfb in CI).

```bash
# 1. Start a Paper server with BuildNotes plugin + RCON enabled (see .github/workflows/e2e-test.yml)
# 2. Run the game test:
./gradlew runClientGameTest
```

Test source is in `client/src/testmod/` — this is a separate Loom source set with its own `fabric.mod.json` that registers the test entrypoint via `fabric-client-gametest`. Tests use RCON (`TestHelper.rconCommand()`) to control server state (e.g. toggling permissions). CI workflow: `.github/workflows/e2e-test.yml`.

## Networking Protocol

Channel: `buildnotes:main`. First byte = PacketType ID.

**C2S**: REQUEST_DATA, SAVE_NOTE, DELETE_NOTE, SAVE_BUILD, DELETE_BUILD, UPLOAD_IMAGE_CHUNK, REQUEST_IMAGE
**S2C**: HANDSHAKE, INITIAL_SYNC, UPDATE_NOTE, DELETE_NOTE_S2C, UPDATE_BUILD, DELETE_BUILD_S2C, IMAGE_CHUNK, IMAGE_NOT_FOUND, UPDATE_PERMISSION

## Key Files

| File | Purpose |
|------|---------|
| `common/src/main/java/com/disqt/buildnotes/common/PacketCodec.java` | All 16 packet encode/decode methods |
| `common/src/main/java/com/disqt/buildnotes/common/model/` | NoteData, BuildData, PermissionLevel, CustomFieldData |
| `client/src/main/java/net/atif/buildnotes/network/RawPayload.java` | Single CustomPayload wrapper |
| `client/src/main/java/net/atif/buildnotes/network/ClientPacketHandler.java` | Dispatches S2C packets, Note/Build converters |
| `client/src/main/java/net/atif/buildnotes/data/DataManager.java` | Client-side data manager (3-scope merge + send C2S) |
| `client/src/main/java/net/atif/buildnotes/hud/HudPinManager.java` | Server-scoped HUD pin state |
| `paper/src/main/java/com/disqt/buildnotes/paper/BuildNotesPlugin.java` | Plugin entry, channel registration |
| `paper/src/main/java/com/disqt/buildnotes/paper/ServerPacketHandler.java` | Handles C2S, broadcasts S2C |
| `paper/src/main/java/com/disqt/buildnotes/paper/DataManager.java` | SQLite persistence |

## Upstream

Remote `upstream` points to `https://github.com/Atif85/buildnotes-mod.git`. The upstream's networking is completely rewritten — only UI, data model, and bug fix changes can be cherry-picked.

## Deploy

- **Paper plugin**: `scp paper/build/libs/*.jar minecraft:~/server/plugins/BuildNotes.jar`
- **Client mod**: distribute jar to players for Fabric `mods/` directory
