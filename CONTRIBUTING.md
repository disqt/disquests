# Contributing to Disquests

Thanks for your interest in contributing to Disquests.

## Getting started

1. Fork the repository
2. Clone your fork and create a branch for your work
3. Make sure you can build and run tests:

```bash
./gradlew build
./gradlew :common:test
```

## Project structure

```
common/   — shared packet codec and data models (no platform deps)
client/   — Fabric client mod
server/   — PaperMC server plugin
```

Both `client` and `server` depend on `common`. Changes to the networking protocol or data models usually touch all three modules.

## Development setup

- **Java 21+** required
- **Gradle** — the wrapper is included, use `./gradlew`
- **Paper dev server** — `./gradlew :server:runServer` starts a local Paper server with the plugin installed (requires 4GB+ free RAM)
- **Versions** — all Minecraft, Fabric, and Paper versions are in `gradle.properties`

## Code style

- Follow existing patterns in the module you're working in
- No IDE-specific formatting enforced — just be consistent with surrounding code
- Keep commits focused — one logical change per commit

## Pull requests

- Open PRs against `main`
- Include a clear description of what changed and why
- Make sure `./gradlew build` and `./gradlew :common:test` pass
- If you're adding a new packet type, update `PacketCodec`, `PacketType`, and add codec tests in `common/`

## Reporting issues

Open an issue on GitHub with:
- What you expected to happen
- What actually happened
- Minecraft version, Fabric Loader version, and any other relevant mods/plugins
