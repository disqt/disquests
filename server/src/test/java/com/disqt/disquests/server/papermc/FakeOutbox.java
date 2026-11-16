package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An {@link Outbox} for tests: a set of online mod players, each with the protocol version their
 * client speaks, and every packet sent to them, decoded at that version. A packet the decoder does
 * not consume to the last byte fails the test, so the recorded payloads are what a client reads.
 */
final class FakeOutbox implements Outbox {

  /** One packet as the client reads it. */
  record Packet(PacketType type, Object payload) {}

  /** A decoded SYNC_MY_QUESTS. */
  record MyQuests(List<QuestData> quests, Map<UUID, Integer> pendingCounts) {}

  private final Map<UUID, String> online = new LinkedHashMap<>();
  private final Map<UUID, Integer> versions = new HashMap<>();
  private final Map<UUID, List<Packet>> sent = new HashMap<>();

  /** Puts the player online with a client speaking {@code protocolVersion}. */
  void join(UUID player, String name, int protocolVersion) {
    online.put(player, name);
    versions.put(player, protocolVersion);
  }

  int protocolVersion(UUID player) {
    return versions.getOrDefault(player, ProtocolVersion.V0);
  }

  @Override
  public void send(UUID player, byte[] data) {
    if (!online.containsKey(player)) return;
    sent.computeIfAbsent(player, k -> new ArrayList<>()).add(decode(data, protocolVersion(player)));
  }

  @Override
  public Collection<UUID> onlineModPlayers() {
    return List.copyOf(online.keySet());
  }

  @Override
  public String name(UUID player) {
    return online.get(player);
  }

  void clear() {
    sent.clear();
  }

  List<Packet> packetsFor(UUID player) {
    return List.copyOf(sent.getOrDefault(player, List.of()));
  }

  List<QuestData> updatesFor(UUID player) {
    return payloads(player, PacketType.UPDATE_QUEST, QuestData.class);
  }

  List<UUID> deletesFor(UUID player) {
    return payloads(player, PacketType.DELETE_QUEST_S2C, UUID.class);
  }

  List<Notice> noticesFor(UUID player) {
    return payloads(player, PacketType.NOTICE, Notice.class);
  }

  List<MyQuests> myQuestsFor(UUID player) {
    return payloads(player, PacketType.SYNC_MY_QUESTS, MyQuests.class);
  }

  @SuppressWarnings("unchecked")
  List<List<QuestData>> serverQuestsFor(UUID player) {
    return (List<List<QuestData>>)
        (List<?>) payloads(player, PacketType.SYNC_SERVER_QUESTS, List.class);
  }

  @SuppressWarnings("unchecked")
  List<List<String>> tagsFor(UUID player) {
    return (List<List<String>>) (List<?>) payloads(player, PacketType.SYNC_TAGS, List.class);
  }

  List<PacketCodec.HandshakePayload> handshakesFor(UUID player) {
    return payloads(player, PacketType.HANDSHAKE, PacketCodec.HandshakePayload.class);
  }

  List<PacketCodec.CollaborationRequestPayload> collaborationRequestsFor(UUID player) {
    return payloads(
        player, PacketType.COLLABORATION_REQUEST, PacketCodec.CollaborationRequestPayload.class);
  }

  List<PacketCodec.CollaborationResponsePayload> collaborationResponsesFor(UUID player) {
    return payloads(
        player, PacketType.COLLABORATION_RESPONSE, PacketCodec.CollaborationResponsePayload.class);
  }

  private <T> List<T> payloads(UUID player, PacketType type, Class<T> payloadClass) {
    return packetsFor(player).stream()
        .filter(p -> p.type() == type)
        .map(p -> payloadClass.cast(p.payload()))
        .toList();
  }

  private static Packet decode(byte[] data, int version) {
    ByteBufReader buf = new ByteBufReader(data);
    PacketType type = PacketCodec.readType(buf);
    Object payload =
        switch (type) {
          case HANDSHAKE -> PacketCodec.readHandshake(buf);
          case SYNC_MY_QUESTS ->
              new MyQuests(
                  PacketCodec.readSyncMyQuests(buf, version), PacketCodec.readPendingCounts(buf));
          case SYNC_SERVER_QUESTS -> PacketCodec.readSyncServerQuests(buf, version);
          case UPDATE_QUEST -> PacketCodec.readUpdateQuest(buf, version);
          case DELETE_QUEST_S2C -> PacketCodec.readDeleteQuestS2C(buf);
          case COLLABORATION_REQUEST -> PacketCodec.readCollaborationRequest(buf);
          case COLLABORATION_RESPONSE -> PacketCodec.readCollaborationResponse(buf, version);
          case SYNC_PENDING_REQUESTS -> PacketCodec.readSyncPendingRequests(buf);
          case SYNC_TAGS -> PacketCodec.readSyncTags(buf);
          case NOTICE -> PacketCodec.readNotice(buf);
          default -> throw new AssertionError("The server sent a client-bound " + type);
        };
    if (buf.remaining() != 0) {
      throw new AssertionError(type + " left " + buf.remaining() + " bytes unread at V" + version);
    }
    return new Packet(type, payload);
  }
}
