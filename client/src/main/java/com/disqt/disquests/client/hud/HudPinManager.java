package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.network.PacketSender;

import java.util.UUID;

public class HudPinManager {

    public static void pin(UUID questId) {
        ClientSession.setPinnedQuestId(questId);
        PacketSender.pinQuest(questId);
    }

    public static void unpin() {
        ClientSession.setPinnedQuestId(null);
        PacketSender.pinQuest(null);
    }

    public static void toggle(UUID questId) {
        if (questId.equals(ClientSession.getPinnedQuestId())) {
            unpin();
        } else {
            pin(questId);
        }
    }

    public static boolean isPinned(UUID questId) {
        return questId != null && questId.equals(ClientSession.getPinnedQuestId());
    }

    public static Quest getPinnedQuest() {
        UUID id = ClientSession.getPinnedQuestId();
        return id != null ? ClientCache.getQuestById(id) : null;
    }
}
