package com.disqt.disquests.paper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Config {
    private String bluemapUrl;

    public Config(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        reload(plugin);
    }

    public void reload(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        this.bluemapUrl = cfg.getString("bluemap-url", "");
    }

    public String getBluemapUrl() { return bluemapUrl; }
}
