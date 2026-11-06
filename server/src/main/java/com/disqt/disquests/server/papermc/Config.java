package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.DimensionKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Config {
  private String bluemapUrl;
  private Map<String, String> bluemapMapNames;
  private String bluemapDefaultMap;
  private List<String> predefinedTags;
  private boolean debug;

  public Config(JavaPlugin plugin) {
    plugin.saveDefaultConfig();
    reload(plugin);
  }

  public void reload(JavaPlugin plugin) {
    plugin.reloadConfig();
    FileConfiguration cfg = plugin.getConfig();
    this.bluemapUrl = cfg.getString("bluemap-url", "");
    Map<String, String> raw = new HashMap<>();
    if (cfg.isConfigurationSection("bluemap-map-names")) {
      var section = cfg.getConfigurationSection("bluemap-map-names");
      for (String key : section.getKeys(false)) {
        raw.put(key, section.getString(key));
      }
    }
    this.bluemapMapNames = normalizeMapNames(raw);
    this.bluemapDefaultMap =
        DimensionKeys.normalize(cfg.getString("bluemap-default-map", "overworld"));
    if (!cfg.contains("predefined-tags")) {
      List<String> defaults = List.of("building", "expedition");
      cfg.set("predefined-tags", defaults);
      plugin.saveConfig();
      this.predefinedTags = defaults;
    } else {
      this.predefinedTags = cfg.getStringList("predefined-tags");
      if (this.predefinedTags == null) this.predefinedTags = List.of();
    }
    this.debug = cfg.getBoolean("debug", false);
    if (Boolean.getBoolean("disquests.debug")) {
      this.debug = true;
    }
  }

  /** Rewrites legacy keys (nether, end, namespaced) to canonical dimension keys. */
  static Map<String, String> normalizeMapNames(Map<String, String> raw) {
    Map<String, String> out = new HashMap<>();
    raw.forEach((k, v) -> out.put(DimensionKeys.normalize(k), v));
    return out;
  }

  public boolean isDebug() {
    return debug;
  }

  public String getBluemapUrl() {
    return bluemapUrl;
  }

  public Map<String, String> getBluemapMapNames() {
    return bluemapMapNames;
  }

  public String getBluemapDefaultMap() {
    return bluemapDefaultMap;
  }

  public List<String> getPredefinedTags() {
    return predefinedTags;
  }
}
