package com.disqt.disquests.client.gui.component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * The screen side of a tree-row drag: finds the row or drop area under the cursor and keeps exactly
 * one of them highlighted. Works on {@link Zone}s, so it stays Minecraft-free: {@link
 * TreeRowComponent} is one, and {@link #area} wraps any other rectangle, such as a "take out of the
 * tree" strip.
 */
public final class DropTracker {

  /** Something a row can be dropped on, in absolute GUI coordinates. */
  public interface Zone {
    String id();

    int x();

    int y();

    int width();

    int height();

    void highlighted(boolean highlighted);
  }

  private final List<Zone> zones;
  private Zone current;

  /** Zones are hit-tested in list order; the first one containing the point wins. */
  public DropTracker(List<? extends Zone> zones) {
    this.zones = List.copyOf(zones);
  }

  /** A drop area whose bounds are read when tested, so it follows layout changes. */
  public static Zone area(
      String id,
      IntSupplier x,
      IntSupplier y,
      IntSupplier width,
      IntSupplier height,
      Consumer<Boolean> highlight) {
    return new Zone() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public int x() {
        return x.getAsInt();
      }

      @Override
      public int y() {
        return y.getAsInt();
      }

      @Override
      public int width() {
        return width.getAsInt();
      }

      @Override
      public int height() {
        return height.getAsInt();
      }

      @Override
      public void highlighted(boolean highlighted) {
        highlight.accept(highlighted);
      }
    };
  }

  /** The zone containing the point, skipping {@code source} (a row is not dropped on itself). */
  public static Zone at(List<? extends Zone> zones, Zone source, int x, int y) {
    for (Zone z : zones) {
      if (z == source) continue;
      if (x >= z.x() && x < z.x() + z.width() && y >= z.y() && y < z.y() + z.height()) return z;
    }
    return null;
  }

  /** The cursor moved during a drag: highlights the zone now under it, returns it (or null). */
  public Zone moved(Zone source, int x, int y) {
    Zone under = at(zones, source, x, y);
    if (under != current) {
      if (current != null) current.highlighted(false);
      if (under != null) under.highlighted(true);
      current = under;
    }
    return under;
  }

  /** The zone under the cursor at the drop, or null; clears the highlight. */
  public Zone dropped(Zone source, int x, int y) {
    Zone under = at(zones, source, x, y);
    clear();
    return under;
  }

  /** Clears the highlight (drag cancelled or finished). */
  public void clear() {
    if (current != null) current.highlighted(false);
    current = null;
  }

  /** The zone highlighted now, or null. */
  public Zone current() {
    return current;
  }
}
