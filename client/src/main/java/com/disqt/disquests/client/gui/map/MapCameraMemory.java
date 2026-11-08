package com.disqt.disquests.client.gui.map;

import java.util.HashMap;
import java.util.Map;

/**
 * The last map camera per dimension, plus the dimension remembered last. Minecraft-free so it can
 * be unit-tested; {@code ClientSession} holds one per server session.
 */
public final class MapCameraMemory {
  /** The whole world: centre (0, 0) at the most zoomed-out step. */
  public static final MapMath.Camera WORLD_VIEW = new MapMath.Camera(0, 0, 0);

  private final Map<String, MapMath.Camera> cameras = new HashMap<>();
  private String lastDimension;

  /** Remembers {@code camera} for {@code dimension}; ignored when either is null. */
  public void remember(String dimension, MapMath.Camera camera) {
    if (dimension == null || camera == null) {
      return;
    }
    cameras.put(dimension, camera);
    lastDimension = dimension;
  }

  /** The camera last remembered for {@code dimension}, or null. */
  public MapMath.Camera get(String dimension) {
    return dimension != null ? cameras.get(dimension) : null;
  }

  /** The dimension of the most recent {@link #remember}, or null when nothing is remembered. */
  public String lastDimension() {
    return lastDimension;
  }

  public void clear() {
    cameras.clear();
    lastDimension = null;
  }
}
