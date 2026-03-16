package com.disqt.disquests.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientSession {

    private static boolean onServer = false;
    private static String bluemapUrl = null;
    private static int pendingRequestCount = 0;
    private static final List<UUID> pinnedQuestIds = new ArrayList<>();
    private static UUID playerUuid = null;

    // UI state
    private static int activeTab = 0; // 0=My Quests, 1=Server Quests
    private static String searchTerm = "";
    private static int serverQuestsFilter = 0; // 0=All, 1=Open, 2=Closed

    public static void joinServer(String bluemapUrl, int pendingCount, List<UUID> pinnedIds, UUID playerUuid) {
        onServer = true;
        ClientSession.bluemapUrl = bluemapUrl;
        pendingRequestCount = pendingCount;
        pinnedQuestIds.clear();
        pinnedQuestIds.addAll(pinnedIds);
        ClientSession.playerUuid = playerUuid;
    }

    public static void leaveServer() {
        onServer = false;
        bluemapUrl = null;
        pendingRequestCount = 0;
        pinnedQuestIds.clear();
        playerUuid = null;
        activeTab = 0;
        searchTerm = "";
        serverQuestsFilter = 0;
    }

    public static boolean isOnServer() {
        return onServer;
    }

    public static String getBluemapUrl() {
        return bluemapUrl;
    }

    public static void setBluemapUrl(String url) {
        bluemapUrl = url;
    }

    public static boolean hasBluemap() {
        return bluemapUrl != null && !bluemapUrl.isEmpty();
    }

    public static int getPendingRequestCount() {
        return pendingRequestCount;
    }

    public static void setPendingRequestCount(int count) {
        pendingRequestCount = count;
    }

    public static void incrementPendingRequestCount() {
        pendingRequestCount++;
    }

    public static List<UUID> getPinnedQuestIds() {
        return pinnedQuestIds;
    }

    public static boolean isPinned(UUID questId) {
        return questId != null && pinnedQuestIds.contains(questId);
    }

    public static void addPinnedQuest(UUID questId) {
        if (!pinnedQuestIds.contains(questId)) {
            pinnedQuestIds.add(questId);
        }
    }

    public static void removePinnedQuest(UUID questId) {
        pinnedQuestIds.remove(questId);
    }

    public static UUID getPlayerUuid() {
        return playerUuid;
    }

    public static int getActiveTab() {
        return activeTab;
    }

    public static void setActiveTab(int tab) {
        activeTab = tab;
    }

    public static String getSearchTerm() {
        return searchTerm;
    }

    public static void setSearchTerm(String term) {
        searchTerm = term;
    }

    public static int getServerQuestsFilter() {
        return serverQuestsFilter;
    }

    public static void setServerQuestsFilter(int filter) {
        serverQuestsFilter = filter;
    }
}
