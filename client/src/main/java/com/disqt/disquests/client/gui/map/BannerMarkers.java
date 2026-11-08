package com.disqt.disquests.client.gui.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses BlueMap's {@code live/markers.json} into banner markers. No Minecraft imports. */
public final class BannerMarkers {
  private static final String SET_PREFIX = "BANNER_MARKER";
  private static final Pattern ICON_COLOR = Pattern.compile("marker_([A-Za-z_]+)[.]png");
  public static final int WHITE = 0xFFF9FFFE;

  /** Minecraft dye colours, as used by the BannerMarker plugin's icon names. */
  private static final Map<String, Integer> DYES =
      Map.ofEntries(
          Map.entry("WHITE", WHITE),
          Map.entry("ORANGE", 0xFFF9801D),
          Map.entry("MAGENTA", 0xFFC74EBD),
          Map.entry("LIGHT_BLUE", 0xFF3AB3DA),
          Map.entry("YELLOW", 0xFFFED83D),
          Map.entry("LIME", 0xFF80C71F),
          Map.entry("PINK", 0xFFF38BAA),
          Map.entry("GRAY", 0xFF474F52),
          Map.entry("LIGHT_GRAY", 0xFF9D9D97),
          Map.entry("CYAN", 0xFF169C9C),
          Map.entry("PURPLE", 0xFF8932B8),
          Map.entry("BLUE", 0xFF3C44AA),
          Map.entry("BROWN", 0xFF835432),
          Map.entry("GREEN", 0xFF5E7C16),
          Map.entry("RED", 0xFFB02E26),
          Map.entry("BLACK", 0xFF1D1D21));

  private BannerMarkers() {}

  /**
   * Every {@code poi} marker of every marker set whose id starts with {@code BANNER_MARKER}.
   * Malformed input gives an empty list; malformed markers are skipped.
   */
  public static List<BannerMarker> parse(String json) {
    List<BannerMarker> out = new ArrayList<>();
    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException e) {
      return out;
    }
    for (Map.Entry<String, JsonElement> set : root.entrySet()) {
      if (!set.getKey().startsWith(SET_PREFIX) || !set.getValue().isJsonObject()) {
        continue;
      }
      JsonElement markers = set.getValue().getAsJsonObject().get("markers");
      if (markers == null || !markers.isJsonObject()) {
        continue;
      }
      for (Map.Entry<String, JsonElement> m : markers.getAsJsonObject().entrySet()) {
        BannerMarker parsed = parseMarker(m.getValue());
        if (parsed != null) {
          out.add(parsed);
        }
      }
    }
    return out;
  }

  private static BannerMarker parseMarker(JsonElement e) {
    try {
      JsonObject o = e.getAsJsonObject();
      JsonElement type = o.get("type");
      if (type == null || !"poi".equals(type.getAsString())) {
        return null;
      }
      JsonObject pos = o.getAsJsonObject("position");
      double x = pos.get("x").getAsDouble();
      double z = pos.get("z").getAsDouble();
      JsonElement label = o.get("label");
      JsonElement icon = o.get("icon");
      return new BannerMarker(
          x,
          z,
          label == null || label.isJsonNull() ? "" : label.getAsString(),
          colorFromIcon(icon == null || icon.isJsonNull() ? null : icon.getAsString()));
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /** ARGB of the dye named in {@code marker_<COLOR>.png}; white when absent or unknown. */
  public static int colorFromIcon(String icon) {
    if (icon == null) {
      return WHITE;
    }
    Matcher m = ICON_COLOR.matcher(icon);
    if (!m.find()) {
      return WHITE;
    }
    return DYES.getOrDefault(m.group(1).toUpperCase(Locale.ROOT), WHITE);
  }
}
