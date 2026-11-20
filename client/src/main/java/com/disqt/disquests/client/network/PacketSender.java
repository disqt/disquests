package com.disqt.disquests.client.network;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class PacketSender {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger("Disquests.PacketSender");

  public static void requestSync() {
    send(PacketCodec.writeRequestSync(ProtocolVersion.CURRENT));
  }

  public static void saveQuest(
      UUID questId,
      String title,
      String content,
      CoordinatesData coords,
      boolean isRegion,
      CoordinatesData coords2,
      String map,
      List<String> tags,
      List<PointData> polygon) {
    saveQuest(
        questId, title, content, coords, isRegion, coords2, map, tags, polygon, LinkChanges.NONE);
  }

  /** Saves a quest with link edits; against a pre-V4 server the link edits are left out. */
  public static void saveQuest(
      UUID questId,
      String title,
      String content,
      CoordinatesData coords,
      boolean isRegion,
      CoordinatesData coords2,
      String map,
      List<String> tags,
      List<PointData> polygon,
      LinkChanges links) {
    LinkChanges effective = ClientSession.supportsTrees() ? links : LinkChanges.NONE;
    send(
        PacketCodec.writeSaveQuest(
            questId, title, content, coords, isRegion, coords2, map, tags, polygon, effective));
  }

  public static void deleteQuest(UUID questId) {
    send(PacketCodec.writeDeleteQuest(questId));
  }

  /** Deletes a quest; {@code alsoMySubquests} deletes the subquests the player owns with it. */
  public static void deleteQuest(UUID questId, boolean alsoMySubquests) {
    if (!ClientSession.supportsTrees()) {
      deleteQuest(questId);
      return;
    }
    send(PacketCodec.writeDeleteQuest(questId, alsoMySubquests));
  }

  public static void joinQuest(UUID questId) {
    send(PacketCodec.writeJoinQuest(questId));
  }

  public static void requestCollaboration(UUID questId) {
    send(PacketCodec.writeRequestCollaboration(questId));
  }

  public static void respondCollaboration(UUID requestId, boolean approved) {
    send(PacketCodec.writeRespondCollaboration(requestId, approved));
  }

  public static void updateContributors(UUID questId, List<PacketCodec.ContributorOpEntry> ops) {
    send(PacketCodec.writeUpdateContributors(questId, ops));
  }

  public static void updateVisibility(UUID questId, Visibility visibility) {
    send(PacketCodec.writeUpdateVisibility(questId, visibility));
  }

  public static void pinQuest(UUID questId) {
    send(PacketCodec.writePinQuest(questId));
  }

  public static void leaveQuest(UUID questId) {
    send(PacketCodec.writeLeaveQuest(questId));
  }

  public static void completeQuest(UUID questId) {
    send(PacketCodec.writeCompleteQuest(questId));
  }

  /** Completes a quest; {@code alsoMySubquests} completes the subquests the player owns too. */
  public static void completeQuest(UUID questId, boolean alsoMySubquests) {
    if (!ClientSession.supportsTrees()) {
      completeQuest(questId);
      return;
    }
    send(PacketCodec.writeCompleteQuest(questId, alsoMySubquests));
  }

  public static void updatePeople(UUID questId, List<PersonOp> ops) {
    if (!requireTrees("updatePeople")) return;
    send(PacketCodec.writeUpdatePeople(questId, ops));
  }

  /** Copies {@code player}'s role on this quest onto the subquests the sender owns. */
  public static void applyRoleDown(UUID questId, UUID player) {
    if (!requireTrees("applyRoleDown")) return;
    send(PacketCodec.writeApplyRoleDown(questId, player));
  }

  /** Answers the "set to Private" notice: restore the old visibility or keep Private. */
  public static void resolvePrivatized(UUID questId, boolean restore) {
    if (!requireTrees("resolvePrivatized")) return;
    send(PacketCodec.writeResolvePrivatized(questId, restore));
  }

  private static boolean requireTrees(String what) {
    if (ClientSession.supportsTrees()) return true;
    LOGGER.debug("Not sending {}: the server does not support quest trees", what);
    return false;
  }

  public static void reopenQuest(UUID questId) {
    send(PacketCodec.writeReopenQuest(questId));
  }

  private static void send(byte[] data) {
    ClientPlayNetworking.send(new RawPayload(data));
  }
}
