package com.disqt.disquests.client.network;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.NoticeText;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.data.QuestRouting;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.gui.map.SharedTileCache;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.QuestData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public class ClientPacketHandler {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger("Disquests.ClientPacketHandler");

  public static void handleRawPayload(RawPayload payload, ClientPlayNetworking.Context context) {
    ByteBufReader r = new ByteBufReader(payload.data());
    PacketType type;
    try {
      type = PacketCodec.readType(r);
    } catch (Exception e) {
      LOGGER.warn("Failed to read packet type", e);
      return;
    }

    context
        .client()
        .execute(
            () -> {
              try {
                switch (type) {
                  case HANDSHAKE -> handleHandshake(r);
                  case SYNC_MY_QUESTS -> handleSyncMyQuests(r);
                  case SYNC_SERVER_QUESTS -> handleSyncServerQuests(r);
                  case UPDATE_QUEST -> handleUpdateQuest(r);
                  case DELETE_QUEST_S2C -> handleDeleteQuestS2C(r);
                  case COLLABORATION_REQUEST -> handleCollaborationRequest(r);
                  case COLLABORATION_RESPONSE -> handleCollaborationResponse(r);
                  case SYNC_PENDING_REQUESTS -> handleSyncPendingRequests(r);
                  case SYNC_TAGS -> handleSyncTags(r);
                  case NOTICE -> handleNotice(r);
                  default -> {}
                }
              } catch (Exception e) {
                LOGGER.warn("Failed to handle S2C packet {}", type, e);
              }
            });
  }

  private static final SystemToast.SystemToastId NOTICE_TOAST =
      new SystemToast.SystemToastId(8001L);

  private static void handleNotice(ByteBufReader r) {
    Notice notice = PacketCodec.readNotice(r);
    if (notice.kind() == Notice.Kind.QUEST_UNLOCKED_FOLLOWED && notice.questId() != null) {
      ClientSession.newMarks().mark(notice.questId());
    }
    Minecraft client = Minecraft.getInstance();
    Component text = Messages.toComponent(NoticeText.of(notice, ClientSession.getMaxQuestDepth()));
    SystemToast.add(client.gui.toastManager(), NOTICE_TOAST, Component.literal("Disquests"), text);
    if (client.gui.screen() instanceof MainScreen mainScreen) {
      mainScreen.refreshListContents();
    }
  }

  private static void showOrDeferToast(String message) {
    Minecraft client = Minecraft.getInstance();
    if (client.gui.screen() instanceof MainScreen mainScreen) {
      mainScreen.refreshListContents();
      mainScreen.showToast(message);
    } else {
      ClientSession.setPendingToast(message);
    }
  }

  /** Lang key of the toast for a quest that just reached My Quests. */
  public static final String NOW_ON_KEY = "gui.disquests.toast.now_on";

  /**
   * "You are now on "X": see My Quests", for every way a quest reaches My Quests: a join, an
   * accepted request, an owner adding the player, a role applied to subquests, an auto-join.
   */
  private static String nowOnText(Quest quest) {
    return Component.translatable(NOW_ON_KEY, quest.getTitle() != null ? quest.getTitle() : "")
        .getString();
  }

  private static void handleHandshake(ByteBufReader r) {
    PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(r);
    ClientSession.joinServer(
        payload.bluemapUrl(),
        payload.pendingRequestCount(),
        payload.pinnedQuestIds(),
        payload.playerUuid(),
        payload.bluemapMapNames(),
        payload.predefinedTags());
    ClientSession.setBluemapDefaultMap(payload.bluemapDefaultMap());
    ClientSession.setServerProtocolVersion(payload.serverProtocolVersion());
    ClientSession.setServerWorldMapEnabled(payload.worldMapEnabled());
    ClientSession.setServerPlayerMarkerEnabled(payload.playerMarkerEnabled());
    ClientSession.setMaxQuestDepth(payload.maxQuestDepth());
    if (ClientSession.supportsMap()
        && ClientSession.isServerWorldMapEnabled()
        && ClientSession.hasBluemap()) {
      // Probe BlueMap at connect time so an unreachable map is known before the board opens.
      SharedTileCache.get().settings(ClientSession.defaultMapId());
    }
    PacketSender.requestSync();
  }

  /**
   * A pre-V2 server sends no polygon, so a legacy rectangle would otherwise read as a pin and lose
   * its second corner on the next save. Normalising turns it into its 4-corner polygon, whose
   * bounding box (what a V1 server stores) is the original rectangle.
   */
  private static Quest toQuest(QuestData data) {
    return Quest.fromNetwork(normalize(data));
  }

  private static QuestData normalize(QuestData data) {
    if (ClientSession.getServerProtocolVersion() < ProtocolVersion.V2) {
      return QuestLocation.normalize(data);
    }
    return data;
  }

  private static QuestRouting.Split route(List<QuestData> dataList) {
    List<QuestData> normalized = new ArrayList<>(dataList.size());
    for (QuestData data : dataList) {
      normalized.add(normalize(data));
    }
    return QuestRouting.split(normalized, ClientSession.getEffectivePlayerUuid());
  }

  private static void handleSyncMyQuests(ByteBufReader r) {
    List<QuestData> dataList =
        PacketCodec.readSyncMyQuests(r, ClientSession.getServerProtocolVersion());
    Map<UUID, Integer> counts = PacketCodec.readPendingCounts(r);
    QuestRouting.Split split = route(dataList);
    ClientCache.setMyQuests(split.mine());
    // A quest I hold no role on never belongs in My Quests, whichever sync carried it.
    split.other().forEach(ClientCache::addOrUpdateServerQuest);
    ClientCache.setPendingCounts(counts);
  }

  private static void handleSyncServerQuests(ByteBufReader r) {
    List<QuestData> dataList =
        PacketCodec.readSyncServerQuests(r, ClientSession.getServerProtocolVersion());
    QuestRouting.Split split = route(dataList);
    ClientCache.setServerQuests(split.other());
    // A quest I hold a role on belongs in My Quests, whichever sync carried it.
    split.mine().forEach(ClientCache::addOrUpdateMyQuest);
  }

  private static void handleUpdateQuest(ByteBufReader r) {
    QuestData data = PacketCodec.readUpdateQuest(r, ClientSession.getServerProtocolVersion());
    Quest quest = toQuest(data);
    final UUID myUuid = ClientSession.getEffectivePlayerUuid();
    boolean isMine = QuestRouting.isMine(quest, myUuid);

    // Detect join: quest is now mine but wasn't previously in my quests
    boolean wasInMyQuests =
        ClientCache.getMyQuests().stream().anyMatch(q -> q.getId().equals(quest.getId()));
    boolean justJoined = isMine && !wasInMyQuests;

    if (isMine) {
      ClientCache.addOrUpdateMyQuest(quest);
      ClientCache.removeFromServerQuests(quest.getId());
    } else {
      ClientCache.addOrUpdateServerQuest(quest);
      ClientCache.removeFromMyQuests(quest.getId());
    }
    if (quest.isCompleted()) {
      // Completed quests leave the HUD; the server drops the pin as well.
      ClientSession.removePinnedQuest(quest.getId());
    }

    // Refresh the main screen's list; an open QuestScreen rebuilds itself on its next tick when
    // what it draws changed (QuestScreen#refreshIfPageChanged).
    Minecraft client = Minecraft.getInstance();
    if (client.gui.screen() instanceof MainScreen mainScreen) {
      mainScreen.refreshListContents();
    }
    if (justJoined) {
      showOrDeferToast(nowOnText(quest));
    }
  }

  private static void handleDeleteQuestS2C(ByteBufReader r) {
    UUID questId = PacketCodec.readDeleteQuestS2C(r);
    ClientCache.removeQuestById(questId);
    ClientSession.removePinnedQuest(questId);
  }

  private static void handleCollaborationRequest(ByteBufReader r) {
    PacketCodec.CollaborationRequestPayload payload = PacketCodec.readCollaborationRequest(r);
    ClientSession.incrementPendingRequestCount();
    // Store in cache for real-time display
    CollaborationRequestData requestData =
        new CollaborationRequestData(
            payload.requestId(),
            payload.questId(),
            payload.questTitle(),
            null,
            payload.requesterName(),
            System.currentTimeMillis() / 1000L);
    ClientCache.addPendingRequest(requestData);
  }

  private static void handleSyncPendingRequests(ByteBufReader r) {
    List<CollaborationRequestData> requests = PacketCodec.readSyncPendingRequests(r);
    ClientCache.setPendingRequests(requests);
  }

  private static void handleSyncTags(ByteBufReader r) {
    List<String> tags = PacketCodec.readSyncTags(r);
    ClientSession.setServerTags(tags);
    LOGGER.debug("Received {} server tags", tags.size());
  }

  private static void handleCollaborationResponse(ByteBufReader r) {
    PacketCodec.CollaborationResponsePayload payload =
        PacketCodec.readCollaborationResponse(r, ClientSession.getServerProtocolVersion());
    if (payload.approved() && payload.quest() != null) {
      Quest quest = toQuest(payload.quest());
      ClientCache.addOrUpdateMyQuest(quest);
      ClientCache.removeFromServerQuests(payload.questId());

      showOrDeferToast(nowOnText(quest));
    }
  }
}
