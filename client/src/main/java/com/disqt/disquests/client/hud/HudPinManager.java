package com.disqt.disquests.client.hud;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.network.PacketSender;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HudPinManager {

    public static void toggle(UUID questId) {
        if (isPinned(questId)) {
            ClientSession.removePinnedQuest(questId);
        } else {
            ClientSession.addPinnedQuest(questId);
        }
        PacketSender.pinQuest(questId);
    }

    public static boolean isPinned(UUID questId) {
        return ClientSession.isPinned(questId);
    }

    public static List<Quest> getPinnedQuests() {
        List<Quest> result = new ArrayList<>();
        for (UUID id : ClientSession.getPinnedQuestIds()) {
            Quest quest = ClientCache.getQuestById(id);
            if (quest != null) {
                result.add(quest);
            }
        }
        return result;
    }
}
