package com.disqt.disquests.client.network;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.PacketType;
import com.disqt.disquests.common.model.QuestData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
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
        ClientSession.joinServer(payload.bluemapUrl(), payload.pendingRequestCount(), payload.pinnedQuestId(), payload.playerUuid());
        PacketSender.requestSync();
    }

    private static void handleSyncMyQuests(ByteBufReader r) {
        List<QuestData> dataList = PacketCodec.readSyncMyQuests(r);
        List<Quest> quests = new ArrayList<>(dataList.size());
        for (QuestData data : dataList) {
            quests.add(Quest.fromNetwork(data));
        }
        ClientCache.setMyQuests(quests);
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
        UUID sessionUuid = ClientSession.getPlayerUuid();
        final UUID myUuid = sessionUuid != null ? sessionUuid : MinecraftClient.getInstance().getSession().getUuidOrNull();
        boolean isMine = data.ownerUuid().equals(myUuid) ||
                data.contributors().stream().anyMatch(c -> c.uuid().equals(myUuid));
        if (isMine) {
            ClientCache.addOrUpdateMyQuest(quest);
            ClientCache.removeFromServerQuests(quest.getId());
        } else {
            ClientCache.addOrUpdateServerQuest(quest);
            ClientCache.removeFromMyQuests(quest.getId());
        }
    }

    private static void handleDeleteQuestS2C(ByteBufReader r) {
        UUID questId = PacketCodec.readDeleteQuestS2C(r);
        ClientCache.removeQuestById(questId);
        if (questId.equals(ClientSession.getPinnedQuestId())) {
            ClientSession.setPinnedQuestId(null);
        }
    }

    private static void handleCollaborationRequest(ByteBufReader r) {
        PacketCodec.readCollaborationRequest(r);
        ClientSession.incrementPendingRequestCount();
    }

    private static void handleCollaborationResponse(ByteBufReader r) {
        PacketCodec.CollaborationResponsePayload payload = PacketCodec.readCollaborationResponse(r);
        if (payload.approved() && payload.quest() != null) {
            Quest quest = Quest.fromNetwork(payload.quest());
            ClientCache.addOrUpdateMyQuest(quest);
            ClientCache.removeFromServerQuests(payload.questId());
        }
    }
}
