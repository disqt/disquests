package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.DimensionKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Config {
  private String bluemapUrl;
  private Map<String, String> bluemapMapNames;
  private String bluemapDefaultMap;
  private List<String> predefinedTags;
  private boolean debug;
  private boolean worldMap;
  private boolean playerMarker;
  private int maxQuestDepth;

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
    this.worldMap = cfg.getBoolean("world-map", true);
    this.playerMarker = readPlayerMarker(cfg);
    this.maxQuestDepth = readMaxQuestDepth(cfg);
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

  /** The in-game map is on when the {@code world-map} flag is set and a BlueMap URL exists. */
  static boolean worldMapEnabled(boolean worldMapFlag, String bluemapUrl) {
    return worldMapFlag && bluemapUrl != null && !bluemapUrl.isBlank();
  }

  public boolean isWorldMapEnabled() {
    return worldMapEnabled(worldMap, bluemapUrl);
  }

  /** The {@code player-marker} flag, true when absent. */
  static boolean readPlayerMarker(ConfigurationSection cfg) {
    return cfg.getBoolean("player-marker", true);
  }

  /** Whether clients show each player's own marker and "Centre on me" on the in-game map. */
  public boolean isPlayerMarkerEnabled() {
    return playerMarker;
  }

  /** The {@code max-quest-depth} setting: default 3 when absent or negative, 0 for no limit. */
  static int readMaxQuestDepth(ConfigurationSection cfg) {
    int depth = cfg.getInt("max-quest-depth", 3);
    return depth < 0 ? 3 : depth;
  }

  /** How many levels of subquests a quest tree may have; 0 means no limit. */
  public int getMaxQuestDepth() {
    return maxQuestDepth;
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
