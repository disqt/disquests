package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.graph.Audience;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.QuestView;
import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The only way quests leave the server. Every quest a player receives is their {@link
 * QuestView#forRecipient view}, with wiki-links resolved for them, and the rules for older clients
 * live here and nowhere else:
 *
 * <ul>
 *   <li>from V4 a client holds every quest it can see, locked or completed; the client decides what
 *       it shows where;
 *   <li>below V4 a quest locked for the player is not sent (it arrives once it unlocks);
 *   <li>at V3 a completed quest is held only by players with a role on it: it leaves the board;
 *   <li>below V3 a completed quest is not sent.
 * </ul>
 *
 * A quest that a client should not hold is sent as {@code DELETE_QUEST_S2C} when it held it before.
 */
final class Delivery {

  private final DataManager dataManager;
  private final Config config;
  private final Outbox outbox;
  private final WikiLinkResolver links;
  private final ToIntFunction<UUID> protocolVersion;

  Delivery(
      DataManager dataManager,
      Config config,
      Outbox outbox,
      WikiLinkResolver links,
      ToIntFunction<UUID> protocolVersion) {
    this.dataManager = dataManager;
    this.config = config;
    this.outbox = outbox;
    this.links = links;
    this.protocolVersion = protocolVersion;
  }

  /**
   * Sends every online mod player the difference between their views of {@code before} and {@code
   * after}: an update for each quest they now hold that changed, a delete for each they held and no
   * longer may, and their tag suggestions when those changed. Players whose view did not change
   * receive nothing.
   */
  void deliver(QuestGraph before, QuestGraph after) {
    for (UUID player : outbox.onlineModPlayers()) {
      int version = protocolVersion.applyAsInt(player);
      for (Audience.Change change : Audience.changes(before, after, player)) {
        UUID id = change.questId();
        QuestData was = held(before, player, QuestView.forRecipient(before, player, id), version);
        QuestData now = held(after, player, change.view(), version);
        if (now != null) {
          outbox.send(player, PacketCodec.writeUpdateQuest(resolved(after, now, player), version));
        } else if (was != null) {
          outbox.send(player, PacketCodec.writeDeleteQuestS2C(id));
        }
      }
      if (version >= ProtocolVersion.V1) {
        List<String> tags = tagsFor(after, player);
        if (!tags.equals(tagsFor(before, player))) {
          outbox.send(player, PacketCodec.writeSyncTags(tags));
        }
      }
    }
  }

  /**
   * The full state for one player: their quests (those they hold a role on), the board (other
   * quests they can see that are not Private; below V4 not completed either), their pending join
   * requests and, from V1, their tag suggestions.
   */
  void sync(UUID player, int version) {
    QuestGraph g = dataManager.loadGraph();
    List<QuestData> mine = new ArrayList<>();
    List<QuestData> board = new ArrayList<>();
    for (QuestData q : g.all()) {
      QuestData view = held(g, player, QuestView.forRecipient(g, player, q.id()), version);
      if (view == null) continue;
      // A quest seen without a role is never Private (canSee), so the rest is the board.
      if (q.roleOf(player) != null) mine.add(resolved(g, view, player));
      else board.add(resolved(g, view, player));
    }
    outbox.send(
        player,
        PacketCodec.writeSyncMyQuests(mine, dataManager.getPendingCountByQuest(player), version));
    outbox.send(player, PacketCodec.writeSyncServerQuests(board, version));
    outbox.send(
        player,
        PacketCodec.writeSyncPendingRequests(dataManager.getPendingRequestsForOwner(player)));
    if (version >= ProtocolVersion.V1) {
      outbox.send(player, PacketCodec.writeSyncTags(tagsFor(g, player)));
    }
  }

  /** The handshake, with only the pinned quests the player can see and that are not locked. */
  void handshake(UUID player) {
    QuestGraph g = dataManager.loadGraph();
    List<UUID> pins =
        dataManager.getPinnedQuestIds(player).stream()
            .filter(id -> g.seesFully(player, id))
            .toList();
    outbox.send(
        player,
        PacketCodec.writeHandshake(
            config.getBluemapUrl(),
            dataManager.getPendingRequestCount(player),
            pins,
            player,
            config.getBluemapMapNames(),
            config.getPredefinedTags(),
            config.getBluemapDefaultMap(),
            protocolVersion.applyAsInt(player),
            config.isWorldMapEnabled(),
            config.isPlayerMarkerEnabled(),
            config.getMaxQuestDepth()));
  }

  /**
   * The quest as the player's client may hold it now, wiki-links resolved, or null when it may not
   * hold it. For packets that carry a quest besides the update itself.
   */
  QuestData sendableView(UUID player, UUID quest) {
    QuestGraph g = dataManager.loadGraph();
    QuestData view =
        held(
            g,
            player,
            QuestView.forRecipient(g, player, quest),
            protocolVersion.applyAsInt(player));
    return view == null ? null : resolved(g, view, player);
  }

  /** The view when a client at {@code version} holds the quest, else null. */
  private static QuestData held(QuestGraph g, UUID player, QuestData view, int version) {
    if (view == null) return null;
    if (view.locked() && version < ProtocolVersion.V4) return null;
    if (view.isCompleted()
        && (version < ProtocolVersion.V3
            || (version < ProtocolVersion.V4 && g.get(view.id()).roleOf(player) == null))) {
      return null;
    }
    return view;
  }

  private QuestData resolved(QuestGraph g, QuestData view, UUID player) {
    String content = links.resolveForRecipient(g, view.content(), player);
    return Objects.equals(content, view.content()) ? view : view.withContent(content);
  }

  /** The predefined tags and the tags of every quest the player sees fully, sorted. */
  private List<String> tagsFor(QuestGraph g, UUID player) {
    TreeSet<String> tags = new TreeSet<>();
    for (String tag : config.getPredefinedTags()) tags.add(tag.toLowerCase(Locale.ROOT));
    for (QuestData q : g.all()) {
      if (g.seesFully(player, q.id())) tags.addAll(q.tags());
    }
    return List.copyOf(tags);
  }
}
