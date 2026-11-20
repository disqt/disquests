package com.disqt.disquests.client.gui.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Every Disquests notification: at most one on screen, and a new one replaces it at once instead of
 * queuing behind it.
 *
 * <p>Vanilla's {@link SystemToast#reset} swaps the text, recomputes the width and restarts the
 * timer (a toast already sliding out slides back in), but the toast keeps the slots it was given
 * when it appeared: a message with more lines would spill out of them. So the shown toast is reset
 * in place when the new message needs no more slots, and otherwise hidden and a fresh one added.
 *
 * <p>Whether the last toast is still up is tracked here rather than through {@code
 * ToastManager.getToast}, which returns the first toast with the id and so cannot tell ours from an
 * older one still sliding out.
 */
public final class Toasts {

  /** The id of every Disquests toast; its long is how long a toast stays up, in milliseconds. */
  public static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId(8000L);

  private static final Component TITLE = Component.literal("Disquests");

  /** The last toast shown; vanilla may already have removed it. */
  @Nullable private static DisquestsToast current;

  private Toasts() {}

  public static void show(Component message) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null) return;
    ToastManager manager = minecraft.gui.toastManager();
    DisquestsToast shown = live(manager);
    DisquestsToast fresh = new DisquestsToast(message);
    if (shown != null && fresh.occcupiedSlotCount() <= shown.occcupiedSlotCount()) {
      shown.reset(TITLE, message);
      return;
    }
    if (shown != null) shown.forceHide();
    manager.addToast(fresh);
    current = fresh;
  }

  /** The Disquests toast on screen (or queued), for the E2E journeys; null when there is none. */
  @Nullable
  public static SystemToast current() {
    Minecraft minecraft = Minecraft.getInstance();
    return minecraft == null ? null : live(minecraft.gui.toastManager());
  }

  /**
   * The last toast while vanilla still holds it: it has not finished its slide-out, and the manager
   * still lists a toast with the id (it lists none after {@code ToastManager.clear}, which drops
   * every toast without finishing them).
   */
  @Nullable
  private static DisquestsToast live(ToastManager manager) {
    DisquestsToast shown = current;
    if (shown == null || shown.finished) return null;
    return manager.getToast(SystemToast.class, ID) != null ? shown : null;
  }

  private static final class DisquestsToast extends SystemToast {
    private boolean finished;

    DisquestsToast(Component message) {
      super(ID, TITLE, message);
    }

    @Override
    public void onFinishedRendering() {
      finished = true;
    }
  }
}
