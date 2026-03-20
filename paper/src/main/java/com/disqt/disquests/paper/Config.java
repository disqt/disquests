package com.disqt.disquests.paper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class Config {
    private String bluemapUrl;
    private Map<String, String> bluemapMapNames;

    public Config(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        reload(plugin);
    }

    public void reload(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        this.bluemapUrl = cfg.getString("bluemap-url", "");
        this.bluemapMapNames = new java.util.HashMap<>();
        if (cfg.isConfigurationSection("bluemap-map-names")) {
            var section = cfg.getConfigurationSection("bluemap-map-names");
            for (String key : section.getKeys(false)) {
                bluemapMapNames.put(key, section.getString(key));
            }
        }
    }

    public String getBluemapUrl() { return bluemapUrl; }
    public Map<String, String> getBluemapMapNames() { return bluemapMapNames; }
}
