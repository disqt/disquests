package com.disqt.disquests.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientSession {

  public enum Tab {
    MY_QUESTS,
    SERVER_QUESTS
  }

  public enum QuestFilter {
    ALL,
    OPEN,
    CLOSED
  }

  private static boolean onServer = false;
  private static String bluemapUrl = null;
  private static int pendingRequestCount = 0;
  private static final List<UUID> pinnedQuestIds = new CopyOnWriteArrayList<>();
  private static final Set<UUID> requestedQuestIds = ConcurrentHashMap.newKeySet();
  private static UUID playerUuid = null;
  private static Map<String, String> bluemapMapNames = Map.of();
  private static String bluemapDefaultMap = "overworld";
  private static List<String> predefinedTags = List.of();
  private static List<String> serverTags = List.of();

  // UI state
  private static Tab activeTab = Tab.MY_QUESTS;
  private static String searchTerm = "";
  private static QuestFilter serverQuestsFilter = QuestFilter.ALL;
  private static String pendingToast = null;

  public static void joinServer(
      String bluemapUrl, int pendingCount, List<UUID> pinnedIds, UUID playerUuid) {
    joinServer(bluemapUrl, pendingCount, pinnedIds, playerUuid, Map.of(), List.of());
  }

  public static void joinServer(
      String bluemapUrl,
      int pendingCount,
      List<UUID> pinnedIds,
      UUID playerUuid,
      Map<String, String> bluemapMapNames) {
    joinServer(bluemapUrl, pendingCount, pinnedIds, playerUuid, bluemapMapNames, List.of());
  }

  public static void joinServer(
      String bluemapUrl,
      int pendingCount,
      List<UUID> pinnedIds,
      UUID playerUuid,
      Map<String, String> bluemapMapNames,
      List<String> predefinedTags) {
    onServer = true;
    ClientSession.bluemapUrl = bluemapUrl;
    pendingRequestCount = pendingCount;
    pinnedQuestIds.clear();
    pinnedQuestIds.addAll(pinnedIds);
    ClientSession.playerUuid = playerUuid;
    ClientSession.bluemapMapNames = bluemapMapNames != null ? bluemapMapNames : Map.of();
    ClientSession.predefinedTags = predefinedTags != null ? List.copyOf(predefinedTags) : List.of();
  }

  public static void leaveServer() {
    onServer = false;
    bluemapUrl = null;
    pendingRequestCount = 0;
    pinnedQuestIds.clear();
    requestedQuestIds.clear();
    pendingToast = null;
    playerUuid = null;
    bluemapMapNames = Map.of();
    bluemapDefaultMap = "overworld";
    predefinedTags = List.of();
    serverTags = List.of();
    activeTab = Tab.MY_QUESTS;
    searchTerm = "";
    serverQuestsFilter = QuestFilter.ALL;
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

  /** Returns the server-provided map name mappings (dimension key -> BlueMap map ID). */
  public static Map<String, String> getBluemapMapNames() {
    return bluemapMapNames;
  }

  public static String getBluemapDefaultMap() {
    return bluemapDefaultMap;
  }

  public static void setBluemapDefaultMap(String defaultMap) {
    bluemapDefaultMap = defaultMap;
  }

  public static List<String> getPredefinedTags() {
    return predefinedTags;
  }

  public static List<String> getServerTags() {
    return serverTags;
  }

  public static void setServerTags(List<String> tags) {
    serverTags = tags != null ? List.copyOf(tags) : List.of();
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
    return Collections.unmodifiableList(pinnedQuestIds);
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

  public static void markRequested(UUID questId) {
    requestedQuestIds.add(questId);
  }

  public static boolean isRequested(UUID questId) {
    return requestedQuestIds.contains(questId);
  }

  public static void setPendingToast(String message) {
    pendingToast = message;
  }

  public static String consumePendingToast() {
    String msg = pendingToast;
    pendingToast = null;
    return msg;
  }

  public static UUID getPlayerUuid() {
    return playerUuid;
  }

  /** Returns the server-assigned player UUID, falling back to the client session UUID. */
  public static UUID getEffectivePlayerUuid() {
    UUID uuid = playerUuid;
    if (uuid == null) {
      uuid = net.minecraft.client.Minecraft.getInstance().getUser().getProfileId();
    }
    return uuid;
  }

  public static Tab getActiveTab() {
    return activeTab;
  }

  public static void setActiveTab(Tab tab) {
    activeTab = tab;
  }

  public static String getSearchTerm() {
    return searchTerm;
  }

  public static void setSearchTerm(String term) {
    searchTerm = term;
  }

  public static QuestFilter getServerQuestsFilter() {
    return serverQuestsFilter;
  }

  public static void setServerQuestsFilter(QuestFilter filter) {
    serverQuestsFilter = filter;
  }
}
