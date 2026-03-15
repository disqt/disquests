package com.disqt.disquests.client;

import com.disqt.disquests.client.data.Quest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientCache {

    private static final CopyOnWriteArrayList<Quest> myQuests = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Quest> serverQuests = new CopyOnWriteArrayList<>();

    public static List<Quest> getMyQuests() {
        return myQuests;
    }

    public static List<Quest> getServerQuests() {
        return serverQuests;
    }

    public static void setMyQuests(List<Quest> quests) {
        myQuests.clear();
        myQuests.addAll(quests);
    }

    public static void setServerQuests(List<Quest> quests) {
        serverQuests.clear();
        serverQuests.addAll(quests);
    }

    public static void addOrUpdateMyQuest(Quest quest) {
        myQuests.removeIf(q -> q.getId().equals(quest.getId()));
        myQuests.add(quest);
    }

    public static void addOrUpdateServerQuest(Quest quest) {
        serverQuests.removeIf(q -> q.getId().equals(quest.getId()));
        serverQuests.add(quest);
    }

    public static void removeQuestById(UUID id) {
        myQuests.removeIf(q -> q.getId().equals(id));
        serverQuests.removeIf(q -> q.getId().equals(id));
    }

    public static void removeFromServerQuests(UUID id) {
        serverQuests.removeIf(q -> q.getId().equals(id));
    }

    public static void removeFromMyQuests(UUID id) {
        myQuests.removeIf(q -> q.getId().equals(id));
    }

    public static Quest getQuestById(UUID id) {
        for (Quest q : myQuests) {
            if (q.getId().equals(id)) return q;
        }
        for (Quest q : serverQuests) {
            if (q.getId().equals(id)) return q;
        }
        return null;
    }

    public static void clear() {
        myQuests.clear();
        serverQuests.clear();
    }
}
