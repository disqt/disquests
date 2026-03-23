package com.disqt.disquests.client;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.model.CollaborationRequestData;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientCache {

    private static final CopyOnWriteArrayList<Quest> myQuests = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Quest> serverQuests = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<UUID, List<CollaborationRequestData>> pendingRequests = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> pendingCounts = new ConcurrentHashMap<>();
    private static volatile long version = 0;

    /** Incremented on every mutation. UI can poll this to detect changes. */
    public static long getVersion() { return version; }

    public static List<Quest> getMyQuests() {
        return myQuests;
    }

    public static List<Quest> getServerQuests() {
        return serverQuests;
    }

    public static void setMyQuests(List<Quest> quests) {
        myQuests.clear();
        myQuests.addAll(quests);
        version++;
    }

    public static void setServerQuests(List<Quest> quests) {
        serverQuests.clear();
        serverQuests.addAll(quests);
        version++;
    }

    public static void addOrUpdateMyQuest(Quest quest) {
        myQuests.removeIf(q -> q.getId().equals(quest.getId()));
        myQuests.add(quest);
        version++;
    }

    public static void addOrUpdateServerQuest(Quest quest) {
        serverQuests.removeIf(q -> q.getId().equals(quest.getId()));
        serverQuests.add(quest);
        version++;
    }

    public static void removeQuestById(UUID id) {
        myQuests.removeIf(q -> q.getId().equals(id));
        serverQuests.removeIf(q -> q.getId().equals(id));
        version++;
    }

    public static void removeFromServerQuests(UUID id) {
        serverQuests.removeIf(q -> q.getId().equals(id));
        version++;
    }

    public static void removeFromMyQuests(UUID id) {
        myQuests.removeIf(q -> q.getId().equals(id));
        version++;
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

    public static void setPendingCounts(Map<UUID, Integer> counts) {
        pendingCounts.clear();
        pendingCounts.putAll(counts);
    }

    public static int getPendingCount(UUID questId) {
        return pendingCounts.getOrDefault(questId, 0);
    }

    public static void setPendingRequests(List<CollaborationRequestData> requests) {
        pendingRequests.clear();
        for (CollaborationRequestData req : requests) {
            pendingRequests.computeIfAbsent(req.questId(), k -> new CopyOnWriteArrayList<>()).add(req);
        }
    }

    public static List<CollaborationRequestData> getPendingRequestsForQuest(UUID questId) {
        return pendingRequests.getOrDefault(questId, List.of());
    }

    public static void removePendingRequest(UUID questId, UUID requestId) {
        List<CollaborationRequestData> reqs = pendingRequests.get(questId);
        if (reqs != null) {
            reqs.removeIf(r -> r.id().equals(requestId));
            if (reqs.isEmpty()) pendingRequests.remove(questId);
        }
        pendingCounts.computeIfPresent(questId, (k, v) -> v > 1 ? v - 1 : null);
    }

    public static void addPendingRequest(CollaborationRequestData request) {
        pendingRequests.computeIfAbsent(request.questId(), k -> new CopyOnWriteArrayList<>()).add(request);
        pendingCounts.merge(request.questId(), 1, Integer::sum);
    }

    public static void clear() {
        myQuests.clear();
        serverQuests.clear();
        pendingRequests.clear();
        pendingCounts.clear();
        version++;
    }
}
