package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.graph.Plans;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Completing, reopening and deleting quests, all Owner only. Each may lock or unlock other quests,
 * so each ends through {@link Outcomes#deliverWithUnlocks}.
 */
final class CompletionHandler {

  private final DataManager dataManager;
  private final Delivery delivery;
  private final Outbox outbox;
  private final Outcomes outcomes;

  CompletionHandler(DataManager dataManager, Delivery delivery, Outbox outbox, Outcomes outcomes) {
    this.dataManager = dataManager;
    this.delivery = delivery;
    this.outbox = outbox;
    this.outcomes = outcomes;
  }

  /**
   * Completes the quest and, when asked, the sender's unfinished subquests. Completing drops the
   * quests' pins and join requests; owners who had requests waiting get a fresh handshake, which
   * carries the request count.
   */
  void complete(UUID player, PacketCodec.QuestActionPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.isCompleted() || quest.roleOf(player) != Role.OWNER) return;

    List<UUID> targets =
        Plans.completeTargets(before, player, quest.id(), payload.alsoMySubquests());
    Set<UUID> ownersWithRequests = new LinkedHashSet<>();
    for (UUID target : targets) {
      for (UUID owner : before.get(target).owners()) {
        if (dataManager.getPendingCountByQuest(owner).getOrDefault(target, 0) > 0) {
          ownersWithRequests.add(owner);
        }
      }
    }
    dataManager.completeQuests(targets, player, System.currentTimeMillis());
    outcomes.deliverWithUnlocks(before);
    for (UUID owner : ownersWithRequests) {
      if (outbox.onlineModPlayers().contains(owner)) delivery.handshake(owner);
    }
  }

  /** Reopens only this quest; quests waiting on it lock again by themselves. */
  void reopen(UUID player, UUID questId) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(questId);
    if (quest == null || !quest.isCompleted() || quest.roleOf(player) != Role.OWNER) return;
    dataManager.reopenQuest(questId);
    outcomes.deliverWithUnlocks(before);
  }

  /**
   * Deletes the quest and, when asked, the sender's subquests. Kept subquests move up; one that
   * would be seen by more players is set to Private, and its other online owners are told.
   */
  void delete(UUID player, PacketCodec.QuestActionPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.roleOf(player) != Role.OWNER) return;

    Plans.DeletePlan plan =
        Plans.delete(
            before, player, quest.id(), payload.alsoMySubquests(), dataManager.getKnownPlayers());
    dataManager.applyDeletePlan(plan, System.currentTimeMillis());
    outcomes.deliverWithUnlocks(before);

    QuestGraph after = dataManager.loadGraph();
    for (UUID id : plan.privatize().keySet()) {
      QuestData kept = after.get(id);
      if (kept == null) continue;
      for (UUID owner : kept.owners()) {
        if (owner.equals(player)) continue;
        outcomes.send(owner, new Notice(Notice.Kind.SET_PRIVATE, id, kept.title(), null));
      }
    }
  }
}
