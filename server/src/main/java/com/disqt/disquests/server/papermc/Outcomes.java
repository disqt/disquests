package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.graph.Plans;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * What players learn after a change: the quests through {@link Delivery}, what the change unlocked,
 * and one-off {@link Notice}s. Notices go to online V4 clients only.
 */
final class Outcomes {

  private final DataManager dataManager;
  private final Delivery delivery;
  private final Outbox outbox;
  private final ToIntFunction<UUID> protocolVersion;

  Outcomes(
      DataManager dataManager,
      Delivery delivery,
      Outbox outbox,
      ToIntFunction<UUID> protocolVersion) {
    this.dataManager = dataManager;
    this.delivery = delivery;
    this.outbox = outbox;
    this.protocolVersion = protocolVersion;
  }

  /**
   * Runs the writes, then the delivery, even when a write throws: players still receive what was
   * written before the failure. The writes' exception wins; one from the delivery is attached to it
   * as suppressed.
   */
  static void writeThenDeliver(Runnable writes, Runnable deliver) {
    try {
      writes.run();
    } catch (RuntimeException | Error failure) {
      try {
        deliver.run();
      } catch (RuntimeException | Error deliveryFailure) {
        failure.addSuppressed(deliveryFailure);
      }
      throw failure;
    }
    deliver.run();
  }

  /**
   * For a change that can complete, reopen, delete or relink prerequisites: adds the auto-join
   * players of every quest it unlocked (offline players too), delivers the difference from {@code
   * before}, then tells who can see each unlocked quest, marking it for those who held a role on
   * one of its prerequisites.
   */
  void deliverWithUnlocks(QuestGraph before) {
    QuestGraph written = dataManager.loadGraph();
    List<Plans.Unlock> unlocks = Plans.unlocks(before, written, everyone());
    writeThenDeliver(
        () -> {
          for (Plans.Unlock unlock : unlocks) {
            for (UUID player : unlock.autoJoin()) {
              dataManager.setRole(unlock.quest(), player, Role.READ);
            }
          }
        },
        () -> delivery.deliver(before, dataManager.loadGraph()));
    QuestGraph after = dataManager.loadGraph();
    for (Plans.Unlock unlock : unlocks) {
      String title = after.get(unlock.quest()).title();
      for (UUID player : unlock.toNotify()) {
        Notice.Kind kind =
            unlock.followers().contains(player)
                ? Notice.Kind.QUEST_UNLOCKED_FOLLOWED
                : Notice.Kind.QUEST_UNLOCKED;
        send(player, new Notice(kind, unlock.quest(), title, null));
      }
    }
  }

  /**
   * Tells the player a change was refused. The notice names {@code quest}, the one they tried to
   * change, only when they see it fully; a {@link Refusal#NOT_VISIBLE} is about a quest they cannot
   * see and names nothing.
   */
  void refused(UUID player, QuestGraph g, UUID quest, Refusal reason) {
    boolean named = reason != Refusal.NOT_VISIBLE && g.seesFully(player, quest);
    QuestData q = named ? g.get(quest) : null;
    send(
        player,
        new Notice(
            Notice.Kind.CHANGE_REFUSED,
            q == null ? null : q.id(),
            q == null ? "" : q.title(),
            reason));
  }

  /** Sends the notice when the player is online with a V4 client. */
  void send(UUID player, Notice notice) {
    if (protocolVersion.applyAsInt(player) < ProtocolVersion.V4) return;
    outbox.send(player, PacketCodec.writeNotice(notice));
  }

  /** Every player the server knows, online or not. */
  Set<UUID> everyone() {
    Set<UUID> players = new LinkedHashSet<>(dataManager.getKnownPlayers());
    players.addAll(outbox.onlineModPlayers());
    return players;
  }
}
