package com.disqt.buildnotes.paper;

import com.disqt.buildnotes.common.model.PermissionLevel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

public class PermissionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private boolean allowAll;
    private final Set<UUID> editors = ConcurrentHashMap.newKeySet();

    public PermissionManager(Path dataDir) {
        this.configFile = dataDir.resolve("permissions.json");
        load();
    }

    public PermissionLevel getPermission(Player player) {
        if (allowAll || player.isOp() || editors.contains(player.getUniqueId())) {
            return PermissionLevel.CAN_EDIT;
        }
        return PermissionLevel.VIEW_ONLY;
    }

    public void addEditor(UUID uuid) {
        editors.add(uuid);
        save();
    }

    public void removeEditor(UUID uuid) {
        editors.remove(uuid);
        save();
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    public void setAllowAll(boolean allowAll) {
        this.allowAll = allowAll;
        save();
    }

    public Set<UUID> getEditors() {
        return Set.copyOf(editors);
    }

    private void load() {
        if (!Files.exists(configFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            PermissionsData data = GSON.fromJson(reader, PermissionsData.class);
            if (data != null) {
                this.allowAll = data.allowAll;
                if (data.editors != null) {
                    for (String uuidStr : data.editors) {
                        editors.add(UUID.fromString(uuidStr));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load permissions", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(configFile.getParent());
            PermissionsData data = new PermissionsData();
            data.allowAll = this.allowAll;
            data.editors = new ArrayList<>();
            for (UUID uuid : editors) {
                data.editors.add(uuid.toString());
            }
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save permissions", e);
        }
    }

    private static class PermissionsData {
        boolean allowAll;
        List<String> editors;
    }
}
