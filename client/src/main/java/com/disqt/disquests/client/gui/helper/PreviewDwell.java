package com.disqt.disquests.client.gui.helper;

import java.util.Objects;

/**
 * When a hover preview may show: only after the pointer has rested on the same thing for {@link
 * #DELAY_MS}. Moving to something else restarts the wait; leaving (nothing hovered) resets it.
 * Minecraft-free, so it can be unit-tested; the caller passes the time.
 *
 * @param <K> what is hovered (a row, a chip); compared with {@link Objects#equals}
 */
public final class PreviewDwell<K> {

  public static final long DELAY_MS = 500;

  private final long delayMs;
  private K hovered;
  private long since;

  public PreviewDwell() {
    this(DELAY_MS);
  }

  public PreviewDwell(long delayMs) {
    this.delayMs = delayMs;
  }

  /**
   * The pointer is on {@code now} (null for nothing) at {@code nowMs}: returns it once it has been
   * there for the delay, else null.
   */
  public K update(K now, long nowMs) {
    if (now == null) {
      hovered = null;
      return null;
    }
    if (!Objects.equals(now, hovered)) {
      hovered = now;
      since = nowMs;
    }
    return nowMs - since >= delayMs ? now : null;
  }
}
