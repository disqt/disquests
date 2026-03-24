package com.disqt.disquests.client.network;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.client.gui.screen.MainScreen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClientPacketHandler {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Disquests.ClientPacketHandler");

    public static void handleRawPayload(RawPayload payload, ClientPlayNetworking.Context context) {
        ByteBufReader r = new ByteBufReader(payload.data());
        PacketType type;
        try {
            type = PacketCodec.readType(r);
        } catch (Exception e) {
            LOGGER.warn("Failed to read packet type", e);
            return;
        }

        context.client().execute(() -> {
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
                    default -> {}
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to handle S2C packet {}", type, e);
            }
        });
    }

    private static void showOrDeferToast(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof MainScreen mainScreen) {
            mainScreen.refreshListContents();
            mainScreen.showToast(message);
        } else {
            ClientSession.setPendingToast(message);
        }
    }

    private static void handleHandshake(ByteBufReader r) {
        PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(r);
        ClientSession.joinServer(payload.bluemapUrl(), payload.pendingRequestCount(),
                payload.pinnedQuestIds(), payload.playerUuid(), payload.bluemapMapNames());
        PacketSender.requestSync();

    }

    private static void handleSyncMyQuests(ByteBufReader r) {
        List<QuestData> dataList = PacketCodec.readSyncMyQuests(r);
        Map<UUID, Integer> counts = PacketCodec.readPendingCounts(r);
        List<Quest> quests = new ArrayList<>(dataList.size());
        for (QuestData data : dataList) {
            quests.add(Quest.fromNetwork(data));
        }
        ClientCache.setMyQuests(quests);
        ClientCache.setPendingCounts(counts);
    }

    private static void handleSyncServerQuests(ByteBufReader r) {
        List<QuestData> dataList = PacketCodec.readSyncServerQuests(r);
        List<Quest> quests = new ArrayList<>(dataList.size());
        for (QuestData data : dataList) {
            quests.add(Quest.fromNetwork(data));
        }
        ClientCache.setServerQuests(quests);
    }

    private static void handleUpdateQuest(ByteBufReader r) {
        QuestData data = PacketCodec.readUpdateQuest(r);
        Quest quest = Quest.fromNetwork(data);
        final UUID myUuid = ClientSession.getEffectivePlayerUuid();
        boolean isMine = quest.isOwner(myUuid) || quest.isContributor(myUuid);

        // Detect join: quest is now mine but wasn't previously in my quests
        boolean wasInMyQuests = ClientCache.getMyQuests().stream()
                .anyMatch(q -> q.getId().equals(quest.getId()));
        boolean justJoined = isMine && !wasInMyQuests;

        if (isMine) {
            ClientCache.addOrUpdateMyQuest(quest);
            ClientCache.removeFromServerQuests(quest.getId());
        } else {
            ClientCache.addOrUpdateServerQuest(quest);
            ClientCache.removeFromMyQuests(quest.getId());
        }

        // Refresh the MainScreen list if it's currently open
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof MainScreen mainScreen) {
            mainScreen.refreshListContents();
        }
        if (justJoined) {
            showOrDeferToast("Joined \"" + quest.getTitle() + "\" \u2014 see My Quests");
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
        CollaborationRequestData requestData = new CollaborationRequestData(
                payload.requestId(), payload.questId(), payload.questTitle(),
                null, payload.requesterName(), System.currentTimeMillis() / 1000L);
        ClientCache.addPendingRequest(requestData);
    }

    private static void handleSyncPendingRequests(ByteBufReader r) {
        List<CollaborationRequestData> requests = PacketCodec.readSyncPendingRequests(r);
        ClientCache.setPendingRequests(requests);
    }

    private static void handleCollaborationResponse(ByteBufReader r) {
        PacketCodec.CollaborationResponsePayload payload = PacketCodec.readCollaborationResponse(r);
        if (payload.approved() && payload.quest() != null) {
            Quest quest = Quest.fromNetwork(payload.quest());
            ClientCache.addOrUpdateMyQuest(quest);
            ClientCache.removeFromServerQuests(payload.questId());

            showOrDeferToast("Joined \"" + quest.getTitle() + "\" \u2014 see My Quests");
        }
    }
}
