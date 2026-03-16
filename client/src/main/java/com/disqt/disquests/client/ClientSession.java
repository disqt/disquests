package com.disqt.disquests.client;

import java.util.UUID;

public class ClientSession {

    private static boolean onServer = false;
    private static String bluemapUrl = null;
    private static int pendingRequestCount = 0;
    private static UUID pinnedQuestId = null;
    private static UUID playerUuid = null;

    // UI state
    private static int activeTab = 0; // 0=My Quests, 1=Server Quests
    private static String searchTerm = "";
    private static int serverQuestsFilter = 0; // 0=All, 1=Open, 2=Closed

    public static void joinServer(String bluemapUrl, int pendingCount, UUID pinnedId, UUID playerUuid) {
        onServer = true;
        ClientSession.bluemapUrl = bluemapUrl;
        pendingRequestCount = pendingCount;
        pinnedQuestId = pinnedId;
        ClientSession.playerUuid = playerUuid;
    }

    public static void leaveServer() {
        onServer = false;
        bluemapUrl = null;
        pendingRequestCount = 0;
        pinnedQuestId = null;
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

    public static UUID getPinnedQuestId() {
        return pinnedQuestId;
    }

    public static void setPinnedQuestId(UUID id) {
        pinnedQuestId = id;
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
