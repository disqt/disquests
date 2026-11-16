package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.DimensionKeys;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.TagConstraints;
import com.disqt.disquests.common.graph.LinkRules;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

/**
 * {@code SAVE_QUEST}: the quest's text, then, when the save carries them, its own links and the
 * tree moves made in its editor.
 *
 * <p>Text, location and tags need Write; a completed quest's are frozen. Visibility, prerequisites
 * and auto-join need Owner on the saved quest. Each tree move is checked by {@link LinkRules}
 * against the tree as it is when its turn comes, so earlier moves of the same save count, and is
 * applied or refused on its own. A save without links changes no link.
 */
final class SaveHandler {

  private static final int MAX_TITLE_LENGTH = 256;
  private static final int MAX_CONTENT_LENGTH = 65536;
  private static final int MAX_MAP_LENGTH = 256;

  private final DataManager dataManager;
  private final Config config;
  private final WikiLinkResolver wikiLinks;
  private final Outcomes outcomes;
  private final ToIntFunction<UUID> protocolVersion;
  private final Logger logger;

  SaveHandler(
      DataManager dataManager,
      Config config,
      WikiLinkResolver wikiLinks,
      Outcomes outcomes,
      ToIntFunction<UUID> protocolVersion,
      Logger logger) {
    this.dataManager = dataManager;
    this.config = config;
    this.wikiLinks = wikiLinks;
    this.outcomes = outcomes;
    this.protocolVersion = protocolVersion;
    this.logger = logger;
  }

  void save(UUID player, String name, PacketCodec.SaveQuestPayload payload) {
    if (payload.title().length() > MAX_TITLE_LENGTH
        || payload.content().length() > MAX_CONTENT_LENGTH
        || (payload.map() != null && payload.map().length() > MAX_MAP_LENGTH)) {
      logger.warning("Quest field too long from " + name);
      return;
    }
    if (!QuestLocation.isValid(payload.polygon())) {
      logger.warning("Invalid quest polygon from " + name);
      return;
    }
    QuestGraph before = dataManager.loadGraph();
    QuestData existing = before.get(payload.questId());
    LinkChanges links = payload.links();

    if (existing != null) {
      Role role = existing.roleOf(player);
      if (role == null || !role.atLeast(Role.WRITE)) return; // silently ignore unauthorized edits
    }

    Outcomes.writeThenDeliver(
        () -> {
          if (existing == null) {
            Visibility visibility =
                links.visibility() == null ? Visibility.PRIVATE : links.visibility();
            dataManager.saveQuest(newQuest(player, name, payload, visibility));
          } else if (!existing.isCompleted()) {
            dataManager.saveQuest(edited(player, existing, payload));
          }
          applyOwnLinks(player, payload.questId(), links, existing == null);
          applyTreeOps(player, links.treeOps());
        },
        () -> outcomes.deliverWithUnlocks(before));
  }

  // --- text ---

  private QuestData newQuest(
      UUID player, String name, PacketCodec.SaveQuestPayload payload, Visibility visibility) {
    return new QuestData(
        payload.questId(),
        payload.title(),
        wikiLinks.reverseResolve(payload.content(), player),
        player,
        name,
        visibility,
        List.of(),
        System.currentTimeMillis() / 1000,
        payload.coords(),
        payload.isRegion(),
        payload.coords2(),
        DimensionKeys.normalize(payload.map()),
        tags(payload),
        payload.polygon());
  }

  /** The stored quest with the saved text, location and tags; people and links as stored. */
  private QuestData edited(UUID player, QuestData existing, PacketCodec.SaveQuestPayload payload) {
    QuestData updated =
        new QuestData(
            existing.id(),
            payload.title(),
            wikiLinks.reverseResolve(payload.content(), player),
            existing.ownerUuid(),
            existing.ownerName(),
            existing.visibility(),
            existing.contributors(),
            System.currentTimeMillis() / 1000,
            payload.coords(),
            payload.isRegion(),
            payload.coords2(),
            DimensionKeys.normalize(payload.map()),
            tags(payload),
            payload.polygon());
    return keepExistingPolygonForUnchangedRectangle(player, existing, updated);
  }

  private static List<String> tags(PacketCodec.SaveQuestPayload payload) {
    return payload.tags().stream()
        .map(t -> t.toLowerCase(Locale.ROOT))
        .filter(t -> !t.isEmpty() && t.length() <= TagConstraints.MAX_TAG_LENGTH)
        .filter(t -> TagConstraints.TAG_PATTERN.matcher(t).matches())
        .distinct()
        .limit(TagConstraints.MAX_TAGS)
        .toList();
  }

  /**
   * A pre-V2 client only sees the bounding rectangle of a polygon. If it saves that rectangle back
   * unchanged, keep the stored polygon instead of flattening it to a rectangle.
   */
  private QuestData keepExistingPolygonForUnchangedRectangle(
      UUID player, QuestData existing, QuestData updated) {
    if (protocolVersion.applyAsInt(player) >= ProtocolVersion.V2) return updated;
    if (existing.polygon().size() < 2) return updated;
    if (updated.coordinates() == null || updated.coordinates2() == null) return updated;
    CoordinatesData[] box = QuestLocation.boundingBox(existing.polygon(), 0);
    if (sameXZ(updated.coordinates(), box[0]) && sameXZ(updated.coordinates2(), box[1])) {
      return updated.withPolygon(existing.polygon());
    }
    return updated;
  }

  private static boolean sameXZ(CoordinatesData a, CoordinatesData b) {
    return Math.abs(a.x() - b.x()) <= 1e-6 && Math.abs(a.z() - b.z()) <= 1e-6;
  }

  // --- the saved quest's own links ---

  /**
   * Visibility, prerequisites and auto-join, all Owner-only. A new quest's visibility was set when
   * it was created. Each added prerequisite is checked against the prerequisites as they are after
   * the ones before it.
   */
  private void applyOwnLinks(UUID player, UUID id, LinkChanges links, boolean isNew) {
    Visibility visibility = isNew ? null : links.visibility();
    boolean prerequisites =
        !links.addPrerequisites().isEmpty()
            || !links.removePrerequisites().isEmpty()
            || links.dropHidden()
            || links.dropDeleted();
    if (visibility == null && !prerequisites && links.autoJoin() == null) return;

    QuestGraph g = dataManager.loadGraph();
    QuestData q = g.get(id);
    if (q.roleOf(player) != Role.OWNER) {
      outcomes.refused(player, g, id, Refusal.NEED_OWNER);
      return;
    }
    if (q.isCompleted()) {
      outcomes.refused(player, g, id, Refusal.COMPLETED);
      return;
    }
    if (visibility != null && visibility != q.visibility()) {
      dataManager.changeVisibility(id, visibility);
      g = dataManager.loadGraph();
    }
    if (prerequisites) applyPrerequisites(player, g, q, links);
    if (links.autoJoin() != null && links.autoJoin() != q.autoJoin()) {
      dataManager.setAutoJoin(id, links.autoJoin());
    }
  }

  private void applyPrerequisites(UUID player, QuestGraph g, QuestData q, LinkChanges links) {
    List<UUID> kept = new ArrayList<>(q.prerequisites());
    // Only prerequisites the sender sees are removed by id; hidden and deleted ones go only
    // through the drop-all flags, so naming an id cannot probe whether a hidden quest is one.
    for (UUID removed : links.removePrerequisites()) {
      if (g.get(removed) != null && g.canSee(player, removed)) kept.remove(removed);
    }
    if (links.dropHidden()) kept.removeIf(p -> g.get(p) != null && !g.canSee(player, p));
    if (links.dropDeleted()) kept.removeIf(p -> g.get(p) == null);
    for (UUID added : links.addPrerequisites()) {
      QuestGraph now = withPrerequisites(g, q.id(), kept);
      Optional<Refusal> refusal = LinkRules.prerequisite(now, player, q.id(), added);
      if (refusal.isEmpty()) kept.add(added);
      else if (refusal.get() != Refusal.ALREADY_THERE) {
        outcomes.refused(player, now, q.id(), refusal.get());
      }
    }
    if (!kept.equals(q.prerequisites())) dataManager.setPrerequisites(q.id(), List.copyOf(kept));
  }

  private static QuestGraph withPrerequisites(QuestGraph g, UUID id, List<UUID> prerequisites) {
    List<QuestData> quests = new ArrayList<>();
    for (QuestData q : g.all()) {
      quests.add(q.id().equals(id) ? q.withPrerequisites(List.copyOf(prerequisites)) : q);
    }
    return QuestGraph.of(quests);
  }

  // --- tree moves ---

  /**
   * Each move against a freshly loaded tree. A move whose result is already there is skipped
   * without a notice: someone else made it first.
   */
  private void applyTreeOps(UUID player, List<TreeOp> ops) {
    int maxDepth = config.getMaxQuestDepth();
    for (TreeOp op : ops) {
      QuestGraph g = dataManager.loadGraph();
      Optional<Refusal> refusal =
          switch (op.kind()) {
            case ATTACH -> LinkRules.attach(g, player, op.quest(), op.target(), maxDepth);
            case DETACH -> LinkRules.detach(g, player, op.quest());
            case SWAP -> LinkRules.swap(g, player, op.quest(), op.target(), maxDepth);
          };
      if (refusal.isPresent()) {
        if (refusal.get() != Refusal.ALREADY_THERE) {
          outcomes.refused(player, g, op.quest(), refusal.get());
        }
        continue;
      }
      switch (op.kind()) {
        case ATTACH -> dataManager.setParent(op.quest(), op.target());
        case DETACH -> dataManager.setParent(op.quest(), null);
        case SWAP -> {
          // The subquest takes the container's place first, so no step makes a loop.
          QuestData place = g.get(g.get(op.quest()).parentId());
          Map<UUID, UUID> parents = new LinkedHashMap<>();
          parents.put(op.target(), place == null ? null : place.id());
          parents.put(op.quest(), op.target());
          dataManager.setParents(parents);
        }
      }
    }
  }
}
