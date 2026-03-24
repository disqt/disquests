package com.disqt.disquests.server.papermc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class Config {
    private String bluemapUrl;
    private Map<String, String> bluemapMapNames;
    private boolean debug;

    public Config(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        reload(plugin);
    }

    public void reload(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        this.bluemapUrl = cfg.getString("bluemap-url", "");
        this.bluemapMapNames = new HashMap<>();
        if (cfg.isConfigurationSection("bluemap-map-names")) {
            var section = cfg.getConfigurationSection("bluemap-map-names");
            for (String key : section.getKeys(false)) {
                bluemapMapNames.put(key, section.getString(key));
            }
        }
        this.debug = cfg.getBoolean("debug", false);
        if (Boolean.getBoolean("disquests.debug")) {
            this.debug = true;
        }
    }

    public boolean isDebug() { return debug; }
    public String getBluemapUrl() { return bluemapUrl; }
    public Map<String, String> getBluemapMapNames() { return bluemapMapNames; }
}
