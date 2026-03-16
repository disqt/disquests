# Disquests

A Minecraft mod for creating, sharing, and collaborating on in-game quests. Fabric client mod + PaperMC server plugin.

<!-- TODO: Add screenshots/GIFs
![Main screen](docs/images/main-screen.png)
![Quest editing](docs/images/edit-quest.png)
![HUD pin](docs/images/hud-pin.png)
-->

## Features

- **Quests** — create titled notes with markdown content, coordinates, and map references
- **Collaboration** — share quests with other players as contributors (view or edit access)
- **Visibility** — private (only you), closed (visible to all, editable by contributors), or open (editable by all)
- **HUD pin** — pin a quest to your screen for quick reference while playing
- **Coordinates** — attach a location (point or region) to any quest, with BlueMap link support
- **Server-side storage** — all quests stored in SQLite on the server, synced to clients automatically
- **Markdown** — quest content supports bold, italic, strikethrough, lists, task lists, and more

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.18.2+ |
| Fabric API | 0.139.5+ |
| PaperMC | 1.21.11 |
| Java | 21+ |

## Installation

### Players

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest `disquests-*.jar` from [Releases](../../releases)
4. Place it in your `.minecraft/mods/` directory

### Server admins

1. Download the latest `disquests-paper-*.jar` from [Releases](../../releases)
2. Place it in your server's `plugins/` directory
3. Restart the server

The plugin creates a `plugins/Disquests/` directory with a `config.yml` and SQLite database on first run.

## Usage

| Action | Default keybind |
|--------|-----------------|
| Open quest list | **N** |
| Toggle HUD pin | Unbound (set in Controls) |

From the quest list you can create new quests, browse server quests, and manage collaboration requests. Each quest has a title, markdown content body, optional coordinates, and visibility settings.

### Visibility levels

- **Private** — only you and your contributors can see it
- **Closed** — everyone can see it, only you and contributors can edit
- **Open** — everyone can see and edit

### Collaboration

Quest owners can add contributors directly or other players can request to join. Contributors can be granted view-only or edit access. Pending requests show as a badge on your inventory screen.

## Building from source

```bash
git clone https://github.com/disqt/disquests.git
cd disquests

./gradlew build                # build all modules
./gradlew :client:build        # client mod jar only
./gradlew :paper:build         # server plugin jar only
./gradlew :common:test         # run unit tests
./gradlew :paper:runServer     # start a dev Paper server
```

Build outputs:
- Client mod: `client/build/libs/disquests-*.jar`
- Paper plugin: `paper/build/libs/disquests-paper-*.jar`

### Project structure

```
common/   — shared packet codec, data models (no platform dependencies)
client/   — Fabric mod: UI, networking, HUD rendering, markdown
paper/    — PaperMC plugin: SQLite storage, packet handler, commands
```

### Running E2E tests

The E2E tests run a real Minecraft client against a Paper server using Fabric's game test framework.

```bash
# 1. Start a Paper server with the plugin + RCON enabled
./gradlew :paper:runServer

# 2. In another terminal, run the game tests
./gradlew runClientGameTest
```

See `.github/workflows/e2e-test.yml` for the full CI setup.

## Attribution

Disquests is a fork of [BuildNotes](https://github.com/Atif85/buildnotes-mod) by [Atif85](https://github.com/Atif85), originally licensed under MIT. The project has been substantially rewritten — approximately 80% of the codebase is new, with the remaining 20% being UI widget code carried forward from upstream.

Key differences from BuildNotes:
- Single quest model (replaces separate Notes and Builds)
- Server-only storage (replaces local JSON files)
- Per-quest ownership and collaboration (replaces global permissions)
- Markdown content support
- New networking protocol
- Full Paper plugin rewrite

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
