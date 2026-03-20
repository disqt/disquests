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
import com.disqt.disquests.client.migration.BuildNotesMigrator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClientPacketHandler {

    public static void handleRawPayload(RawPayload payload, ClientPlayNetworking.Context context) {
        ByteBufReader r = new ByteBufReader(payload.data());
        PacketType type;
        try {
            type = PacketCodec.readType(r);
        } catch (Exception e) {
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
                org.slf4j.LoggerFactory.getLogger("Disquests")
                    .warn("Failed to handle S2C packet {}", type, e);
            }
        });
    }

    private static void handleHandshake(ByteBufReader r) {
        PacketCodec.HandshakePayload payload = PacketCodec.readHandshake(r);
        ClientSession.joinServer(payload.bluemapUrl(), payload.pendingRequestCount(),
                payload.pinnedQuestIds(), payload.playerUuid(), payload.bluemapMapNames());
        PacketSender.requestSync();

        // Migrate old BuildNotes if present
        ServerInfo serverInfo = MinecraftClient.getInstance().getCurrentServerEntry();
        if (serverInfo != null) {
            String address = serverInfo.address;
            // Strip port if present to match folder name
            if (address.contains(":")) {
                address = address.substring(0, address.indexOf(":"));
            }
            BuildNotesMigrator.migrateIfNeeded(address);
        }
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
        boolean isMine = data.ownerUuid().equals(myUuid) ||
                data.contributors().stream().anyMatch(c -> c.uuid().equals(myUuid));

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
            if (justJoined) {
                mainScreen.showToast("Joined \"" + quest.getTitle() + "\" \u2014 see My Quests");
            }
        } else if (justJoined) {
            ClientSession.setPendingToast("Joined \"" + quest.getTitle() + "\" \u2014 see My Quests");
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

            String toastMsg = "Joined \"" + quest.getTitle() + "\" \u2014 see My Quests";
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof MainScreen mainScreen) {
                mainScreen.refreshListContents();
                mainScreen.showToast(toastMsg);
            } else {
                ClientSession.setPendingToast(toastMsg);
            }
        }
    }
}
