package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.graph.Plans;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

/**
 * Who holds which role on a quest: people changes by owners, sharing a role down the tree, joining,
 * join requests and leaving, and the visibility packets that decide who may join. Rights come from
 * the stored roles; a player who cannot see a quest learns nothing from a refusal.
 */
final class PeopleHandler {

  private final DataManager dataManager;
  private final Delivery delivery;
  private final Outbox outbox;
  private final Outcomes outcomes;
  private final ToIntFunction<UUID> protocolVersion;
  private final Logger logger;

  PeopleHandler(
      DataManager dataManager,
      Delivery delivery,
      Outbox outbox,
      Outcomes outcomes,
      ToIntFunction<UUID> protocolVersion,
      Logger logger) {
    this.dataManager = dataManager;
    this.delivery = delivery;
    this.outbox = outbox;
    this.outcomes = outcomes;
    this.protocolVersion = protocolVersion;
    this.logger = logger;
  }

  // --- people, by owners ---

  /**
   * {@code UPDATE_PEOPLE}: all the changes or none. A removal "with my subquests" also takes the
   * player off the sender's own subquests, except where they are the last owner.
   */
  void updatePeople(UUID player, PacketCodec.UpdatePeoplePayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.isCompleted() || !before.canSee(player, quest.id())) return;
    Plans.PeopleResult result;
    try {
      result = Plans.people(quest, player, payload.ops());
    } catch (IllegalArgumentException e) {
      logger.warning("Malformed people change: " + e.getMessage());
      return;
    }
    if (result.refusal().isPresent()) {
      outcomes.refused(player, before, quest.id(), result.refusal().get());
      return;
    }
    dataManager.setPeople(quest.id(), named(result.people()), result.displayOwner());
    for (PersonOp op : payload.ops()) {
      if (op.kind() == PersonOp.Kind.REMOVE_WITH_MY_SUBQUESTS) {
        removeFromMySubquests(before, player, quest.id(), op.player());
      }
    }
    delivery.deliver(before, dataManager.loadGraph());
  }

  /** People added by a change have no name yet. */
  private List<ContributorData> named(List<ContributorData> people) {
    List<ContributorData> named = new ArrayList<>();
    for (ContributorData c : people) {
      named.add(
          c.name() != null
              ? c
              : new ContributorData(c.uuid(), dataManager.getPlayerName(c.uuid()), c.role()));
    }
    return named;
  }

  private void removeFromMySubquests(QuestGraph g, UUID actor, UUID quest, UUID player) {
    for (UUID id : Plans.mySubquests(g, actor, quest)) {
      QuestData sub = g.get(id);
      Role role = sub.roleOf(player);
      if (role == null || (role == Role.OWNER && sub.owners().size() == 1)) continue;
      dataManager.removePerson(id, player);
    }
  }

  /** {@code APPLY_ROLE_DOWN}: an owner gives one person their role on the owner's subquests. */
  void applyRoleDown(UUID player, PacketCodec.ApplyRoleDownPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.roleOf(player) != Role.OWNER) return;
    Map<UUID, Role> changes = Plans.roleDown(before, player, quest.id(), payload.player());
    for (Map.Entry<UUID, Role> change : changes.entrySet()) {
      dataManager.setRole(change.getKey(), payload.player(), change.getValue());
    }
    delivery.deliver(before, dataManager.loadGraph());
  }

  /**
   * {@code UPDATE_CONTRIBUTORS} from a client before V4, Owner only. ADD (a player without a role)
   * and UPDATE (a player with one) give Write or Read from {@code canEdit}; REMOVE removes. Each op
   * stands alone; one that would remove or demote the last owner is ignored.
   */
  void updateContributors(UUID player, PacketCodec.UpdateContributorsPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.isCompleted() || quest.roleOf(player) != Role.OWNER) return;
    try {
      for (PacketCodec.ContributorOpEntry op : payload.ops()) {
        UUID target = op.playerUuid();
        if (target == null && op.playerName() != null) {
          target = dataManager.getPlayerUuidByName(op.playerName());
        }
        if (target == null) continue;
        QuestData current = dataManager.getQuest(quest.id());
        Role role = op.canEdit() ? Role.WRITE : Role.READ;
        PersonOp personOp =
            switch (op.action()) {
              case ADD ->
                  current.roleOf(target) == null
                      ? new PersonOp(PersonOp.Kind.SET_ROLE, target, role)
                      : null;
              case UPDATE ->
                  current.roleOf(target) != null
                      ? new PersonOp(PersonOp.Kind.SET_ROLE, target, role)
                      : null;
              case REMOVE -> new PersonOp(PersonOp.Kind.REMOVE, target, null);
            };
        if (personOp == null) continue;
        Plans.PeopleResult result = Plans.people(current, player, List.of(personOp));
        if (result.refusal().isEmpty()) {
          dataManager.setPeople(quest.id(), named(result.people()), result.displayOwner());
        }
      }
    } finally {
      // Each op is its own write: deliver what was written even if a later op fails.
      delivery.deliver(before, dataManager.loadGraph());
    }
  }

  // --- visibility ---

  /** {@code UPDATE_VISIBILITY} from a client before V4, Owner only. */
  void updateVisibility(UUID player, PacketCodec.UpdateVisibilityPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.isCompleted() || quest.roleOf(player) != Role.OWNER) return;
    dataManager.changeVisibility(quest.id(), payload.visibility());
    delivery.deliver(before, dataManager.loadGraph());
  }

  /**
   * {@code RESOLVE_PRIVATIZED}, Owner only: sets back the visibility the quest had before a delete
   * made it Private, or keeps it Private; either way the remembered state is cleared.
   */
  void resolvePrivatized(UUID player, PacketCodec.ResolvePrivatizedPayload payload) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(payload.questId());
    if (quest == null || quest.roleOf(player) != Role.OWNER || quest.privatized() == null) return;
    if (payload.restore()) dataManager.changeVisibility(quest.id(), quest.privatized().previous());
    dataManager.clearPrivatized(quest.id());
    delivery.deliver(before, dataManager.loadGraph());
  }

  // --- joining and leaving ---

  /** An Open quest the player sees fully and holds no role on: they join it with Read. */
  void join(UUID player, UUID questId) {
    QuestGraph before = dataManager.loadGraph();
    if (!joinable(before, player, questId, Visibility.OPEN)) return;
    dataManager.setRole(questId, player, Role.READ);
    delivery.deliver(before, dataManager.loadGraph());
  }

  /** A Closed quest the player sees fully and holds no role on: every online owner is asked. */
  void request(UUID player, String name, UUID questId) {
    QuestGraph g = dataManager.loadGraph();
    if (!joinable(g, player, questId, Visibility.CLOSED)) return;
    QuestData quest = g.get(questId);
    UUID requestId;
    try {
      requestId = dataManager.createCollaborationRequest(questId, player);
    } catch (RuntimeException e) {
      if (!(e.getCause() instanceof java.sql.SQLException)) {
        logger.warning("Failed to create collaboration request: " + e.getMessage());
      }
      return; // a duplicate request is ignored
    }
    for (UUID owner : quest.owners()) {
      outbox.send(
          owner, PacketCodec.writeCollaborationRequest(requestId, questId, quest.title(), name));
    }
  }

  private static boolean joinable(QuestGraph g, UUID player, UUID questId, Visibility visibility) {
    QuestData quest = g.get(questId);
    return quest != null
        && !quest.isCompleted()
        && quest.visibility() == visibility
        && quest.roleOf(player) == null
        && g.canSee(player, questId)
        && !g.lockedFor(player, questId);
  }

  /**
   * Any owner answers a join request; the first answer settles it. Approving gives Read. The other
   * online owners get a fresh handshake, so their request count drops.
   */
  void respond(UUID player, PacketCodec.RespondCollaborationPayload payload) {
    CollaborationRequestData request = dataManager.getCollaborationRequest(payload.requestId());
    if (request == null) return;
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(request.questId());
    if (quest == null || quest.roleOf(player) != Role.OWNER) return;

    dataManager.deleteCollaborationRequest(payload.requestId());
    UUID requester = request.requesterUuid();
    int requesterVersion = protocolVersion.applyAsInt(requester);
    if (payload.approved()) {
      if (quest.roleOf(requester) == null) dataManager.setRole(quest.id(), requester, Role.READ);
      outbox.send(
          requester,
          PacketCodec.writeCollaborationResponse(
              quest.id(), true, delivery.sendableView(requester, quest.id()), requesterVersion));
      delivery.deliver(before, dataManager.loadGraph());
    } else {
      outbox.send(
          requester,
          PacketCodec.writeCollaborationResponse(quest.id(), false, null, requesterVersion));
    }
    for (UUID owner : quest.owners()) {
      if (!owner.equals(player) && outbox.onlineModPlayers().contains(owner)) {
        delivery.handshake(owner);
      }
    }
  }

  /** Any role may leave, while the quest is locked too, except the last owner. */
  void leave(UUID player, UUID questId) {
    QuestGraph before = dataManager.loadGraph();
    QuestData quest = before.get(questId);
    if (quest == null || quest.isCompleted()) return;
    Role role = quest.roleOf(player);
    if (role == null || (role == Role.OWNER && quest.owners().size() == 1)) return;
    dataManager.removePerson(questId, player);
    dataManager.unpinQuest(player, questId);
    delivery.deliver(before, dataManager.loadGraph());
  }
}
