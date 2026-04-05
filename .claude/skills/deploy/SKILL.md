---
name: deploy
description: Use when deploying Disquests changes to the Minecraft server and Prism Launcher instance, or when the user says "deploy", "push to server", "update the mod".
disable-model-invocation: true
---

# Deploy Disquests

Build all modules, deploy the Paper plugin to the Minecraft server, and update the client mod in the Prism Launcher instance.

## Steps

1. **Build everything**
   ```bash
   ./gradlew build
   ```
   Stop if build fails.

2. **Deploy Paper plugin to server**
   ```bash
   scp paper/build/libs/paper.jar minecraft:~/serverfiles/plugins/Disquests.jar
   ssh minecraft "tmux -S /tmp/tmux-1000/pmcserver-bb664df1 send-keys -t pmcserver 'plugman reload Disquests' Enter"
   ```

3. **Deploy client mod to Prism instance**
   Detect the Prism Launcher instances directory at runtime:
   - **Windows**: `$APPDATA/PrismLauncher/instances/`
   - **Linux**: `~/.local/share/PrismLauncher/instances/`
   - **macOS**: `~/Library/Application Support/PrismLauncher/instances/`

   Find the latest `1.21.* v*` instance, then copy:
   ```bash
   cp client/build/libs/client.jar "<instances>/<latest>/.minecraft/mods/disquests-client.jar"
   ```

4. **Verify owo-lib is present in Prism mods**
   Check that `owo-lib-*.jar` exists in the Prism mods folder. If missing, copy from Gradle cache:
   ```bash
   ls "C:/Users/leole/AppData/Roaming/PrismLauncher/instances/1.21.11 v2.7/.minecraft/mods/owo-lib-"* 2>/dev/null
   ```
   If not found:
   ```bash
   find ~/.gradle/caches/modules-2/files-2.1/io.wispforest/owo-lib/ -name "*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1
   ```
   Copy the result to the Prism mods folder.

5. **Report** what was deployed (server plugin version, client mod, owo-lib status).

## Notes

- The user must restart their Minecraft client for client changes to take effect.
- The server plugin reloads via PlugManX without a restart.
- If only server OR client changes were made, it's OK to skip the unchanged side -- but default to deploying both.
