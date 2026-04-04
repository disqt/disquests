# Modpack Release CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A single Python script that zips a Prism instance, uploads to VPS, auto-generates a changelog, updates manifest.json, prunes old versions, and optionally pings Discord.

**Architecture:** `modpack_release.py` in `minecraft/prism-modpack-releaser/`. Config in `modpack-release.json`. Stdlib only (zipfile, json, subprocess, urllib). Claude Code skill wrapper in `disquests/.claude/skills/release-modpack/`.

**Git workflow:** Create branch `feat/prism-modpack-releaser` in the `minecraft/` repo, commit per task, raise PR.

**Tech Stack:** Python 3.10+, stdlib only, SSH/SCP via subprocess

---

### Task 1: Config file and argument parsing

**Files:**
- Create: `C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack-release.json`
- Create: `C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py`

- [ ] **Step 1: Create the config file**

```json
{
  "vps_host": "dev",
  "vps_path": "/home/dev/prism",
  "mc_version": "1.21.11",
  "modloader": "Fabric",
  "discord_webhook": "https://discord.com/api/webhooks/1490001989122129943/zcNiF89UyqJn2o1KFJt1LSuMqfGcNrHXvki1vJoLAsngFYK18LW_0gvKnUPoqG_T7CDc",
  "disquests_repo": "disqt/disquests",
  "keep": 3
}
```

- [ ] **Step 2: Create the script with arg parsing and config loading**

```python
#!/usr/bin/env python3
"""Modpack Release Tool -- zip, upload, changelog, publish."""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from datetime import date
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
DEFAULT_CONFIG = SCRIPT_DIR / "modpack-release.json"


def load_config(path: Path) -> dict:
    return json.loads(path.read_text())


def parse_args():
    parser = argparse.ArgumentParser(description="Release a Prism Launcher modpack version")
    parser.add_argument("instance_path", type=Path, help="Path to Prism instance directory")
    parser.add_argument("--version", required=True, help="Version to publish (e.g. 2.10)")
    parser.add_argument("--keep", type=int, help="Versions to retain on VPS (default: from config)")
    parser.add_argument("--no-notify", action="store_true", help="Skip Discord notification")
    parser.add_argument("--dry-run", action="store_true", help="Show plan without executing")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help="Config file path")
    return parser.parse_args()


def validate_instance(instance_path: Path):
    """Check that the path looks like a valid Prism instance."""
    if not instance_path.is_dir():
        sys.exit(f"Error: {instance_path} is not a directory")
    if not (instance_path / "mmc-pack.json").exists():
        sys.exit(f"Error: {instance_path}/mmc-pack.json not found (not a Prism instance?)")
    mods_dir = instance_path / ".minecraft" / "mods"
    if not mods_dir.is_dir():
        sys.exit(f"Error: {mods_dir} not found")


def main():
    args = parse_args()
    config = load_config(args.config)
    validate_instance(args.instance_path)

    mc_version = config["mc_version"]
    version = args.version
    filename = f"{mc_version} v{version}.zip"
    keep = args.keep or config.get("keep", 3)

    print(f"Modpack Release: {mc_version} v{version}")
    print("=" * 50)
    print(f"  Instance: {args.instance_path}")
    print(f"  Filename: {filename}")
    print(f"  VPS:      {config['vps_host']}:{config['vps_path']}")
    print()

    # Steps follow in subsequent tasks...


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Verify it runs**

Run: `python C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py "C:\Users\leole\AppData\Roaming\PrismLauncher\instances\1.21.11 v2.10" --version 2.10 --dry-run`
Expected: Prints header info and exits cleanly.

- [ ] **Step 4: Commit**

```bash
cd C:\Users\leole\Documents\code\minecraft
git checkout -b feat/prism-modpack-releaser
git add prism-modpack-releaser/
git commit -m "feat: modpack release CLI scaffold with config and arg parsing"
```

---

### Task 2: Zip the instance

**Files:**
- Modify: `C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py`

- [ ] **Step 1: Add zip_instance function**

Add after `validate_instance`:

```python
def load_packignore(instance_path: Path) -> list[str]:
    """Read .packignore patterns (one per line, relative to instance root)."""
    packignore = instance_path / ".packignore"
    if not packignore.exists():
        return []
    return [line.strip() for line in packignore.read_text().splitlines() if line.strip()]


def should_exclude(rel_path: str, ignore_patterns: list[str]) -> bool:
    """Check if a relative path matches any .packignore pattern."""
    for pattern in ignore_patterns:
        # Pattern is like ".minecraft/saves" -- match if path starts with it
        normalized = pattern.replace("\\", "/")
        if rel_path.startswith(normalized) or rel_path.startswith(normalized + "/"):
            return True
    return False


def zip_instance(instance_path: Path, output_path: Path, root_name: str) -> int:
    """Zip the instance directory, respecting .packignore.
    Returns the zip file size in bytes.
    """
    ignore_patterns = load_packignore(instance_path)
    # Always exclude these runtime dirs
    ignore_patterns.extend([
        ".minecraft/logs",
        ".minecraft/crash-reports",
    ])

    print("Zipping instance...")
    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for file_path in sorted(instance_path.rglob("*")):
            if file_path.is_dir():
                continue
            rel = file_path.relative_to(instance_path).as_posix()
            if should_exclude(rel, ignore_patterns):
                continue
            arcname = f"{root_name}/{rel}"
            zf.write(file_path, arcname)

    size = output_path.stat().st_size
    size_mb = size / (1024 * 1024)
    print(f"  Created {output_path.name} ({size_mb:.0f} MB)")
    return size
```

- [ ] **Step 2: Wire into main**

Add to `main()` after the header print:

```python
    with tempfile.TemporaryDirectory() as tmp:
        zip_path = Path(tmp) / filename
        zip_size = zip_instance(args.instance_path, zip_path, f"{mc_version} v{version}")
        size_str = f"{zip_size / (1024 * 1024):.0f} MB"
        print()

        if args.dry_run:
            print("[dry-run] Would upload, update manifest, and notify.")
            return

        # Steps continue in next tasks...
```

- [ ] **Step 3: Test the zip**

Run: `python C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py "C:\Users\leole\AppData\Roaming\PrismLauncher\instances\1.21.11 v2.10" --version 2.10 --dry-run`
Expected: Creates zip in temp dir, prints size (~1018 MB), then prints dry-run message.

- [ ] **Step 4: Commit**

```bash
cd C:\Users\leole\Documents\code\minecraft
git add prism-modpack-releaser/
git commit -m "feat: zip Prism instance respecting .packignore"
```

---

### Task 3: Changelog generation

**Files:**
- Modify: `C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py`

- [ ] **Step 1: Add SSH helper and changelog functions**

Add after `zip_instance`:

```python
def ssh_cmd(host: str, cmd: str) -> str:
    """Run a command on the VPS via SSH and return stdout."""
    result = subprocess.run(
        ["ssh", host, cmd],
        capture_output=True, text=True, timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError(f"SSH command failed: {cmd}\n{result.stderr}")
    return result.stdout


def get_previous_mod_list(config: dict) -> dict[str, str]:
    """Get mod jar filenames from the latest version on VPS.
    Returns {mod_prefix: filename} mapping.
    """
    host = config["vps_host"]
    vps_path = config["vps_path"]
    try:
        manifest_raw = ssh_cmd(host, f"cat {vps_path}/manifest.json")
        manifest = json.loads(manifest_raw)
    except Exception:
        return {}
    if not manifest.get("latest"):
        return {}
    prev_file = manifest["latest"]["file"]
    try:
        output = ssh_cmd(host, f"unzip -l '{vps_path}/{prev_file}' 2>/dev/null | grep '\\.jar'")
    except Exception:
        return {}
    jars = {}
    for line in output.splitlines():
        parts = line.strip().split()
        if not parts:
            continue
        path = parts[-1]
        if "/mods/" in path:
            name = path.split("/")[-1]
            jars[name] = name
    return jars


def extract_mod_version(jar_name: str) -> tuple[str, str]:
    """Split a jar filename into (prefix, version).
    E.g. 'ModName-1.2.3.jar' -> ('ModName', '1.2.3')
    """
    base = jar_name.removesuffix(".jar").removesuffix(".jar.disabled")
    # Try common patterns: name-version, name_version
    m = re.match(r"^(.+?)[_-](\d+\.\d+.*)$", base)
    if m:
        return m.group(1), m.group(2)
    return base, ""


def get_local_mod_list(instance_path: Path) -> dict[str, str]:
    """Get mod jar filenames from the local instance.
    Returns {filename: filename} mapping.
    """
    mods_dir = instance_path / ".minecraft" / "mods"
    return {f.name: f.name for f in mods_dir.iterdir() if f.suffix == ".jar"}


def get_config_diff(instance_path: Path, config: dict) -> list[str]:
    """Diff config directory against previous version on VPS.
    Returns list of changelog lines for config changes.
    """
    host = config["vps_host"]
    vps_path = config["vps_path"]
    try:
        manifest_raw = ssh_cmd(host, f"cat {vps_path}/manifest.json")
        manifest = json.loads(manifest_raw)
    except Exception:
        return []
    if not manifest.get("latest"):
        return []
    prev_file = manifest["latest"]["file"]
    try:
        output = ssh_cmd(host, f"unzip -l '{vps_path}/{prev_file}' 2>/dev/null | grep '/config/'")
    except Exception:
        return []
    prev_configs = set()
    for line in output.splitlines():
        parts = line.strip().split()
        if parts:
            path = parts[-1]
            if "/config/" in path:
                prev_configs.add(path.split("/config/", 1)[-1])
    local_configs = set()
    config_dir = instance_path / ".minecraft" / "config"
    if config_dir.is_dir():
        for f in config_dir.rglob("*"):
            if f.is_file():
                local_configs.add(f.relative_to(config_dir).as_posix())
    added = local_configs - prev_configs
    removed = prev_configs - local_configs
    changes = []
    if added:
        changes.append(f"Added {len(added)} config files")
    if removed:
        changes.append(f"Removed {len(removed)} config files")
    return changes


def get_disquests_changelog(old_jars: dict, new_jars: dict, repo: str) -> list[str]:
    """If disquests version changed, fetch first 3 lines from GitHub release."""
    old_ver, new_ver = None, None
    for name in old_jars:
        if "disquests-client" in name.lower():
            _, old_ver = extract_mod_version(name)
    for name in new_jars:
        if "disquests-client" in name.lower():
            _, new_ver = extract_mod_version(name)
    if not new_ver or new_ver == old_ver:
        return []
    try:
        result = subprocess.run(
            ["gh", "release", "view", f"v{new_ver}", "--repo", repo, "--json", "body", "--jq", ".body"],
            capture_output=True, text=True, timeout=15,
        )
        if result.returncode != 0:
            return [f"Updated Disquests to v{new_ver}"]
        lines = [l.strip() for l in result.stdout.splitlines() if l.strip() and not l.startswith("#")]
        return [f"Disquests v{new_ver}:"] + [f"  {l}" for l in lines[:3]]
    except Exception:
        return [f"Updated Disquests to v{new_ver}"]


def generate_changelog(instance_path: Path, config: dict) -> list[str]:
    """Generate changelog by diffing mod jars and configs against previous VPS version."""
    old_jars = get_previous_mod_list(config)
    new_jars = get_local_mod_list(instance_path)
    changelog = []

    if not old_jars:
        changelog.append("Initial release")
        return changelog

    # Build prefix->filename maps for matching
    old_by_prefix = {}
    for name in old_jars:
        prefix, ver = extract_mod_version(name)
        old_by_prefix[prefix.lower()] = (name, ver)
    new_by_prefix = {}
    for name in new_jars:
        prefix, ver = extract_mod_version(name)
        new_by_prefix[prefix.lower()] = (name, ver)

    # Detect added, removed, updated
    for prefix in sorted(set(old_by_prefix) | set(new_by_prefix)):
        old_entry = old_by_prefix.get(prefix)
        new_entry = new_by_prefix.get(prefix)
        if old_entry and not new_entry:
            changelog.append(f"Removed {old_entry[0]}")
        elif new_entry and not old_entry:
            changelog.append(f"Added {new_entry[0]}")
        elif old_entry and new_entry and old_entry[1] != new_entry[1]:
            name = new_entry[0].split("-")[0] if "-" in new_entry[0] else prefix
            changelog.append(f"Updated {name} {old_entry[1]} -> {new_entry[1]}")

    # Config changes
    config_changes = get_config_diff(instance_path, config)
    changelog.extend(config_changes)

    # Disquests subsection
    dq_lines = get_disquests_changelog(old_jars, new_jars, config.get("disquests_repo", ""))
    if dq_lines:
        changelog.extend(dq_lines)

    return changelog
```

- [ ] **Step 2: Wire changelog generation into main and add confirmation**

Add after `zip_instance` call in `main()`:

```python
        # Generate changelog
        changelog = generate_changelog(args.instance_path, config)
        print("Changelog:")
        for line in changelog:
            print(f"  {line}")
        print()

        if args.dry_run:
            print("[dry-run] Would upload, update manifest, and notify.")
            return

        confirm = input("Publish? [y/n] ").strip().lower()
        if confirm != "y":
            print("Aborted.")
            return

        # Upload and publish steps follow...
```

- [ ] **Step 3: Test changelog generation**

Run: `python C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py "C:\Users\leole\AppData\Roaming\PrismLauncher\instances\1.21.11 v2.10" --version 2.11 --dry-run`
Expected: Shows diff of mods between VPS latest (v2.10) and local instance.

- [ ] **Step 4: Commit**

```bash
cd C:\Users\leole\Documents\code\minecraft
git add prism-modpack-releaser/
git commit -m "feat: auto-generate changelog from mod/config diffs and Disquests release"
```

---

### Task 4: Upload, manifest, symlink, prune

**Files:**
- Modify: `C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py`

- [ ] **Step 1: Add upload, manifest, symlink, and prune functions**

Add after `generate_changelog`:

```python
def upload_zip(zip_path: Path, config: dict):
    """SCP the zip to VPS."""
    host = config["vps_host"]
    vps_path = config["vps_path"]
    dest = f"{host}:{vps_path}/{zip_path.name}"
    print(f"Uploading {zip_path.name}...")
    subprocess.run(["scp", str(zip_path), dest], check=True)
    print("  Upload complete.")


def update_manifest(config: dict, version: str, filename: str, size_str: str, changelog: list[str]):
    """Read manifest from VPS, prepend new version, write back."""
    host = config["vps_host"]
    vps_path = config["vps_path"]
    mc_version = config["mc_version"]
    modloader = config["modloader"]

    try:
        raw = ssh_cmd(host, f"cat {vps_path}/manifest.json")
        manifest = json.loads(raw)
    except Exception:
        manifest = {"latest": None, "versions": []}

    new_entry = {
        "version": f"{mc_version} v{version}",
        "file": filename,
        "date": date.today().isoformat(),
        "mc": mc_version,
        "modloader": modloader,
        "size": size_str,
        "changelog": changelog,
    }

    manifest["latest"] = new_entry
    manifest["versions"].insert(0, new_entry)

    manifest_json = json.dumps(manifest, indent=2, ensure_ascii=False)
    # Write via SSH (escape single quotes in JSON)
    escaped = manifest_json.replace("'", "'\\''")
    ssh_cmd(host, f"cat > {vps_path}/manifest.json <<'MANIFEST_EOF'\n{manifest_json}\nMANIFEST_EOF")
    print("  Manifest updated.")


def update_symlink(config: dict, filename: str):
    """Update the latest.zip symlink on VPS."""
    host = config["vps_host"]
    vps_path = config["vps_path"]
    ssh_cmd(host, f"cd {vps_path} && ln -sf '{filename}' latest.zip")
    print("  Symlink updated.")


def prune_old_versions(config: dict, keep: int):
    """Remove old zip files beyond the keep count, and trim manifest versions array."""
    host = config["vps_host"]
    vps_path = config["vps_path"]

    # Read manifest to get ordered version list
    raw = ssh_cmd(host, f"cat {vps_path}/manifest.json")
    manifest = json.loads(raw)
    versions = manifest.get("versions", [])

    if len(versions) <= keep:
        return

    # Trim versions array
    to_remove = versions[keep:]
    manifest["versions"] = versions[:keep]

    # Write updated manifest
    manifest_json = json.dumps(manifest, indent=2, ensure_ascii=False)
    ssh_cmd(host, f"cat > {vps_path}/manifest.json <<'MANIFEST_EOF'\n{manifest_json}\nMANIFEST_EOF")

    # Delete old zip files
    for entry in to_remove:
        old_file = entry.get("file", "")
        if old_file:
            ssh_cmd(host, f"rm -f '{vps_path}/{old_file}'")
            print(f"  Pruned {old_file}")
```

- [ ] **Step 2: Wire into main after confirmation**

Replace the `# Upload and publish steps follow...` comment with:

```python
        # Upload
        upload_zip(zip_path, config)
        print()

        # Update manifest
        print("Updating manifest...")
        update_manifest(config, version, filename, size_str, changelog)

        # Update symlink
        print("Updating symlink...")
        update_symlink(config, filename)

        # Prune old versions
        print("Pruning old versions...")
        prune_old_versions(config, keep)
        print()

        # Discord notification (next task)
        if not args.no_notify and config.get("discord_webhook"):
            notify_discord(config, version, changelog)

        print(f"Released {mc_version} v{version}")
```

- [ ] **Step 3: Commit**

```bash
cd C:\Users\leole\Documents\code\minecraft
git add prism-modpack-releaser/
git commit -m "feat: upload, manifest update, symlink, and prune old versions"
```

---

### Task 5: Discord notification

**Files:**
- Modify: `C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py`

- [ ] **Step 1: Add notify_discord function**

Add after `prune_old_versions`:

```python
def notify_discord(config: dict, version: str, changelog: list[str]):
    """POST a release notification to Discord via webhook."""
    webhook_url = config["discord_webhook"]
    mc_version = config["mc_version"]

    description = "\n".join(changelog) if changelog else "No changes listed."
    payload = {
        "embeds": [{
            "title": f"Modpack {mc_version} v{version}",
            "description": description,
            "color": 0x2dd4bf,
            "url": "https://disqt.com/minecraft/",
            "footer": {"text": "disqt.com/minecraft"},
        }]
    }

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        webhook_url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status in (200, 204):
                print("  Discord notification sent.")
            else:
                print(f"  Discord notification failed: HTTP {resp.status}")
    except Exception as e:
        print(f"  Discord notification failed: {e}")
```

Add `import urllib.request` to the imports at the top (should already be there from the pattern in `updater.py`; verify and add if missing).

- [ ] **Step 2: Test with dry-run**

Run: `python C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py "C:\Users\leole\AppData\Roaming\PrismLauncher\instances\1.21.11 v2.10" --version 2.10 --dry-run`
Expected: Full dry-run output with changelog. No upload, no Discord.

- [ ] **Step 3: Commit**

```bash
cd C:\Users\leole\Documents\code\minecraft
git add prism-modpack-releaser/
git commit -m "feat: Discord webhook notification on modpack release"
```

---

### Task 6: Claude Code skill wrapper

**Files:**
- Create: `C:\Users\leole\Documents\code\disquests\.claude\skills\release-modpack\SKILL.md`

- [ ] **Step 1: Create the skill file**

```markdown
---
name: release-modpack
description: Use when deploying a new Prism Launcher modpack version to disqt.com/minecraft. Triggers on "release modpack", "update modpack", "new modpack version", "publish modpack".
disable-model-invocation: true
---

# Release Modpack

Release a new version of the Prism Launcher modpack to disqt.com/minecraft.

## Steps

1. **Ask the user for the version number** (e.g. "2.11")

2. **Ask for the Prism instance path** or default to the latest instance in `C:\Users\leole\AppData\Roaming\PrismLauncher\instances\`

3. **Run the release script**
   ```bash
   python C:\Users\leole\Documents\code\minecraft\prism-modpack-releaser\modpack_release.py "<instance-path>" --version "<version>"
   ```
   The script handles: zip, changelog, upload, manifest, symlink, prune, Discord notification.
   It will prompt for confirmation before publishing.

4. **Report** the result (version published, changelog, Discord notification status).

## Notes

- The script auto-generates a changelog by diffing mod jars against the previous VPS version.
- Discord notification goes to #minecraft via webhook.
- Old versions beyond the last 3 are automatically pruned.
- Use `--dry-run` to preview without publishing.
- Use `--no-notify` to skip Discord notification.
```

- [ ] **Step 2: Commit the skill in the disquests repo**

```bash
cd C:\Users\leole\Documents\code\disquests
git checkout -b feat/release-modpack-skill
git add .claude/skills/release-modpack/SKILL.md
git commit -m "feat: add release-modpack Claude Code skill"
```

- [ ] **Step 3: Raise PR in the minecraft repo**

```bash
cd C:\Users\leole\Documents\code\minecraft
git push -u origin feat/prism-modpack-releaser
gh pr create --title "feat: add prism-modpack-releaser CLI" --body "..."
```
