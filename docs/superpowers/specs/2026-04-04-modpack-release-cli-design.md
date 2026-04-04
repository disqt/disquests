# Modpack Release CLI Design

## Problem

Releasing a new Prism Launcher modpack version to disqt.com/minecraft is a manual multi-step process: zip the instance, scp to VPS, edit manifest.json, update symlink, prune old versions. Error-prone and tedious.

## Solution

A single Python script (`modpack-release.py`) in `minecraft-updater/` that automates the full release pipeline. Stdlib only, no external dependencies.

## Interface

```
python modpack-release.py <instance-path> --version <version>
```

Flags:
- `--keep N` -- versions to retain on VPS (default 3)
- `--webhook-url <url>` -- Discord webhook (overrides config)
- `--dry-run` -- show plan without executing
- `--no-notify` -- skip Discord notification

## Config: `modpack-release.json`

Lives alongside the script in `minecraft-updater/`.

```json
{
  "vps_host": "dev",
  "vps_path": "/home/dev/prism",
  "mc_version": "1.21.11",
  "modloader": "Fabric",
  "discord_webhook": "https://discord.com/api/webhooks/...",
  "disquests_repo": "disqt/disquests"
}
```

## Flow

### 1. Validate

Check instance path exists, has `mmc-pack.json` and `.minecraft/mods/`.

### 2. Zip

Zip the instance directory into a temp file named `{mc_version} v{version}.zip`. Respect `.packignore` if present (line-based glob exclusions). Include DH server data.

### 3. Generate Changelog

**Mod changes**: SSH to VPS, list jar filenames in the previous version's zip (`unzip -l`). Diff against the new instance's `mods/` directory. Produce lines:
- "Added ModName-1.0.jar"
- "Removed ModName-1.0.jar"
- "Updated ModName 1.0 -> 1.1" (when jar prefix matches but version differs)

**Config changes**: Same diff approach on `config/` directory. List added/removed/modified config files.

**Disquests subsection**: If `disquests-client-*.jar` version changed, run `gh release view v{new_version} --json body --jq .body` on the `disquests_repo`. Extract the first 3 non-empty lines from the release body. Prepend as a "Disquests v{x}:" subsection in the changelog.

**Confirmation**: Print the generated changelog and ask `Publish? [y/n]`. On `n`, abort.

### 4. Upload

`scp` the zip to `{vps_host}:{vps_path}/{filename}`.

### 5. Update Manifest

SSH to read current `manifest.json`. Prepend new version entry to `versions` array with: version, file, date (today), mc, modloader, size (from zip file size), changelog (the generated lines). Set `latest` to the new entry. Write back via SSH.

### 6. Update Symlink

`ssh {vps_host} "cd {vps_path} && ln -sf '{filename}' latest.zip"`

### 7. Prune

List zips on VPS sorted by date. Remove any beyond `--keep` count. Also trim the `versions` array in manifest to match.

### 8. Discord Notification

POST to webhook URL with an embed:
- Title: "Modpack {mc_version} v{version}"
- Description: changelog lines joined by newlines
- Color: teal (0x2dd4bf, matching the website accent)
- Timestamp: now

## Claude Code Skill

`disquests/.claude/skills/release-modpack/SKILL.md` -- a thin wrapper that:
1. Asks the user for version number
2. Resolves the Prism instance path from config
3. Invokes the script
4. Reports the result

## Out of Scope

- Prism instance management (copy/rename/create)
- Server-side plugin deployment (existing `updater.py` handles that)
- Website restart (Astro reads manifest on each SSR request)
