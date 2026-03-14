# BuildNotes Paper Fork — Design Spec

## Overview

Fork [BuildNotes](https://github.com/Atif85/buildnotes-mod) (MIT, Fabric mod) to work with a PaperMC server. BuildNotes provides in-game note-taking with server-scoped shared notes, a rich text editor, build tracking, image galleries, and a permission system. Currently it requires Fabric on both client and server — we're splitting it into a Fabric client mod + Paper plugin pair.

Two deliverables in a single private repo at `github.com/disqt/buildnotes`:

1. **Client mod** (`client/`) — Fabric mod, fork of BuildNotes with networking rewritten to use vanilla plugin messages instead of Fabric-specific payloads
2. **Paper plugin** (`paper/`) — reimplements BuildNotes' server-side logic (data persistence, packet handling, permissions) as a Paper plugin

Additionally, we add **HUD pinning** — the ability to pin a shared note to the screen overlay, borrowed from the [Notes mod](https://github.com/MattCzyr/Notes) pattern.

## Motivation

Our Minecraft server runs PaperMC. Players use Fabric clients. BuildNotes has exactly the shared notes UX we want (server-scoped notes visible to all players, permissions, rich editor), but its server component only works on Fabric servers. By reimplementing the server side as a Paper plugin, we get BuildNotes' full feature set on our existing infrastructure.

HUD pinning lets players keep a shared project note (e.g. "Guardian XP Farm — steps to complete") visible on screen while playing.

## Target versions

- Minecraft: 1.21.4
- Fabric Loader: 0.16.x
- Fabric API: latest for 1.21.4
- Paper API: 1.21.4-R0.1-SNAPSHOT

## Scope

### v1 (this spec)

1. **Paper compatibility** — rewrite client networking from Fabric `PayloadTypeRegistry`/`ServerPlayNetworking` to vanilla plugin message channels. Write a Paper plugin that implements the same protocol. Image support (galleries, chunk upload/download) is included for full feature parity — it's only 3 extra packet types and a blob table.
2. **HUD pinning** — pin one server-scoped note to the screen overlay. Configurable position (top-left, top-right, center-left, center-right, bottom-left, bottom-right). Renders with semi-transparent background, truncates with ellipsis if too long. Only notes can be pinned in v1; build pinning is out of scope.

### v2 (future, out of scope)

- `[ ]` / `[x]` checkbox rendering in note text
- Progress indicator ("4/7") in note list for notes containing checkboxes
- Clickable checkboxes
- Markdown rendering
- `completedBy` / `completedAt` metadata per checkbox
- Web dashboard at `disqt.com/minecraft/quests/` (v1 schema should not preclude shared DB access for a future web process)
- Build pinning on HUD

## Architecture

### Repository structure

```
buildnotes/                          # github.com/disqt/buildnotes (private)
├── common/                          # Shared code (Gradle subproject)
│   └── src/main/java/...
│       ├── PacketCodec.java         # Serialize/deserialize to byte arrays
│       ├── PacketType.java          # Packet type ID constants
│       └── packet/                  # Packet record types (used by both sides)
├── client/                          # Fabric mod (Gradle subproject, depends on common)
│   └── src/main/java/...
│       ├── client/                  # UI, keybinds, HUD rendering, client cache
│       ├── data/                    # Note, Build, Scope, etc. (unchanged from upstream)
│       ├── network/                 # Rewritten: vanilla plugin messages
│       │   └── ClientPacketHandler.java
│       └── hud/                    # NEW: HUD pin overlay (from Notes mod pattern)
├── paper/                          # Paper plugin (Gradle subproject, depends on common)
│   └── src/main/java/...
│       ├── BuildNotesPlugin.java   # Plugin entry point
│       ├── ServerPacketHandler.java # Handles C2S packets, sends S2C
│       ├── DataManager.java        # SQLite persistence
│       ├── PermissionManager.java  # Per-note permissions
│       └── Commands.java           # /buildnotes commands
├── settings.gradle
└── build.gradle                    # Root build with common + client + paper subprojects
```

### Networking protocol

**Channel**: `buildnotes:main`

We define our own wire format rather than matching BuildNotes' upstream exactly. Upstream uses Fabric-specific `StreamCodec` registration which is tightly coupled to the Fabric networking stack. Our format uses vanilla-compatible byte arrays: packets are prefixed with a single byte packet type ID, followed by fields serialized as Minecraft-style varints and length-prefixed UTF-8 strings (matching vanilla `FriendlyByteBuf` encoding). Both `PacketByteBuf` (Fabric) and `ByteArrayDataInput`/`ByteArrayDataOutput` (Paper) can read/write this format.

The serialization logic lives in the `common/` module (`PacketCodec.java`) so both sides share the exact same code. The `common` module has no Fabric or Paper dependencies — just plain Java.

We reimplement all ~16 upstream packet types:

**C2S (client to server):**

| Packet | Purpose |
|--------|---------|
| `REQUEST_DATA` | Player joins / opens UI, requests full sync |
| `SAVE_NOTE` | Create or update a note |
| `DELETE_NOTE` | Delete a note |
| `SAVE_BUILD` | Create or update a build |
| `DELETE_BUILD` | Delete a build |
| `REQUEST_IMAGE` | Request an image by ID |
| `UPLOAD_IMAGE_CHUNK` | Upload image data in chunks |

**S2C (server to client):**

| Packet | Purpose |
|--------|---------|
| `HANDSHAKE` | Server confirms BuildNotes support, sends config |
| `INITIAL_SYNC` | Full data dump (all notes + builds for this scope) |
| `UPDATE_NOTE` | Broadcast note create/update to all players |
| `DELETE_NOTE` | Broadcast note deletion |
| `UPDATE_BUILD` | Broadcast build create/update |
| `DELETE_BUILD` | Broadcast build deletion |
| `IMAGE_CHUNK` | Send image data chunk to client |
| `IMAGE_NOT_FOUND` | Image request failed |
| `UPDATE_PERMISSION` | Permission change notification |

**Handshake flow**: When a player joins, the Paper plugin sends `HANDSHAKE` on the `buildnotes:main` channel. The client recognizes it and sends `REQUEST_DATA`. The server replies with `INITIAL_SYNC` containing all server-scoped notes and builds. From then on, any mutation triggers a broadcast to all online players.

### Paper plugin — server side

**Data persistence**: SQLite database (`plugins/BuildNotes/data.db`). Tables:

- `notes` (id, title, content, owner_uuid, created_at, updated_at)
- `builds` (id, name, coordinates, dimension, description, credits, custom_fields_json, image_filenames_json, owner_uuid, created_at, updated_at)
- `images` (id, entry_id, entry_type, filename, data_blob, UNIQUE(entry_id, filename))

**Permission model (v1)**: Global permission model matching upstream BuildNotes. Two levels:
- `CAN_EDIT` — create, edit, delete any note/build (granted to ops, or via allowlist, or via `allow_all`)
- `VIEW_ONLY` — read-only (default for non-op players)

Per-note ownership (OWNER/EDITOR/VIEWER ACLs) is deferred to v2.

**Commands**: `/buildnotes allow <player>`, `/buildnotes disallow <player>`, `/buildnotes list`, `/buildnotes allow_all <true|false>` (matching upstream). `/buildnotes pin <note-id>` for HUD pinning.

### Client mod — Fabric side

**Networking rewrite**: Replace all uses of `PayloadTypeRegistry`, `ServerPlayNetworking`, `ClientPlayNetworking` with vanilla plugin message channel handling. On Fabric 1.21.4, we still need to register a `CustomPayload` type with `PayloadTypeRegistry` on the client — this is a Fabric requirement even for vanilla-compatible channels. The payload type wraps a raw `byte[]` and delegates serialization to `PacketCodec` from the `common` module. The Paper plugin registers the same channel via `Messenger.registerIncomingPluginChannel()` / `player.sendPluginMessage()` and uses `PacketCodec` directly.

The key point: the client registers one generic `CustomPayload` type that carries raw bytes. It does NOT register per-packet-type codecs. The packet type byte prefix inside the payload is parsed by `PacketCodec`, which is shared code.

**HUD pinning** (new feature):
- Player can pin one note via keybind (configurable, default: unbound) or `/buildnotes pin`
- Renders pinned note on the HUD using a mixin on `InGameHud` (same pattern as Notes mod's `HUDMixin`)
- Configurable position and scale via mod config
- Shows note title + content, truncated with ellipsis if too long
- Updates in real time when the note is modified by another player (via `UPDATE_NOTE` broadcast)
- Pin preference stored client-side (config file with note ID)

## Build & deploy

- **Client mod**: `./gradlew :client:build` produces a `.jar` for Fabric
- **Paper plugin**: `./gradlew :paper:build` produces a `.jar` for Paper's `plugins/` directory
- Client jar distributed to players (manual install or via modpack)
- Paper plugin deployed to server via `scp` to `minecraft@disqt.com:~/server/plugins/`

## Testing strategy

- **Manual testing**: Primary approach for v1. Run a local Paper server + Fabric client, verify all packet types work, test with 2+ clients for broadcast behavior.
- **Unit tests**: Packet serialization/deserialization round-trip tests (shared codec, test that Paper and Fabric sides agree on byte format)

## Upstream sync

BuildNotes is MIT licensed and actively maintained (last commit Dec 2025, v1.2.3). Our fork should:
- Track upstream releases in a separate branch (`upstream/main`)
- Cherry-pick or merge upstream updates when they contain relevant fixes (UI, data model, bug fixes)
- Upstream networking changes cannot be cherry-picked and must be manually evaluated for protocol additions, since we've completely rewritten the networking layer
- Keep our changes in clearly separated files/packages where possible (e.g. `hud/` package for pin feature, networking in `common/` + platform-specific handlers)

## Resolved decisions

1. **Packet format**: We define our own wire format using Minecraft-style varints and length-prefixed UTF-8 strings. The upstream Fabric-specific `StreamCodec` approach is too coupled to Fabric's networking stack. Our format lives in a shared `common` module.
2. **Image support**: Included in v1 for full feature parity. It's only 3 packet types and a blob table.
3. **Handshake**: Sends the player's permission level (matching upstream). No version string or additional config in v1.
4. **Server plugin path**: Deploy to `minecraft@disqt.com` via SSH alias, actual path to be confirmed during implementation.
