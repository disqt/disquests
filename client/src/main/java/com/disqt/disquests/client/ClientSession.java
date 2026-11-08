package com.disqt.disquests.client;

import com.disqt.disquests.client.gui.map.MapCameraMemory;
import com.disqt.disquests.client.gui.map.MapMath;
import com.disqt.disquests.client.gui.map.SharedTileCache;
import com.disqt.disquests.common.ProtocolVersion;
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
    SERVER_QUESTS,
    ARCHIVED
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
  private static int serverProtocolVersion = ProtocolVersion.V1;
  private static boolean serverWorldMapEnabled = true;
  private static boolean serverPlayerMarkerEnabled = true;
  private static final MapCameraMemory cameraMemory = new MapCameraMemory();

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
    SharedTileCache.reset();
    playerUuid = null;
    bluemapMapNames = Map.of();
    bluemapDefaultMap = "overworld";
    predefinedTags = List.of();
    serverTags = List.of();
    serverProtocolVersion = ProtocolVersion.V1;
    serverWorldMapEnabled = true;
    serverPlayerMarkerEnabled = true;
    cameraMemory.clear();
    activeTab = Tab.MY_QUESTS;
    searchTerm = "";
    serverQuestsFilter = QuestFilter.ALL;
  }

  public static int getServerProtocolVersion() {
    return serverProtocolVersion;
  }

  public static void setServerProtocolVersion(int version) {
    serverProtocolVersion = version;
  }

  public static boolean supportsMap() {
    return serverProtocolVersion >= ProtocolVersion.V2;
  }

  /** The server's {@code world-map} switch from the handshake (true when the server sent none). */
  public static boolean isServerWorldMapEnabled() {
    return serverWorldMapEnabled;
  }

  public static void setServerWorldMapEnabled(boolean enabled) {
    serverWorldMapEnabled = enabled;
  }

  /**
   * The server's {@code player-marker} switch from the handshake (true when the server sent none).
   * When false the map shows no player marker and no "Centre on me" button, and opens on the
   * remembered camera or the whole world instead of the player.
   */
  public static boolean isPlayerMarkerEnabled() {
    return serverPlayerMarkerEnabled;
  }

  public static void setServerPlayerMarkerEnabled(boolean enabled) {
    serverPlayerMarkerEnabled = enabled;
  }

  /** Remembers the map camera last used in {@code dimension}, for this server session. */
  public static void rememberCamera(String dimension, MapMath.Camera camera) {
    cameraMemory.remember(dimension, camera);
  }

  /** The map camera last used in {@code dimension}, or null. */
  public static MapMath.Camera rememberedCamera(String dimension) {
    return cameraMemory.get(dimension);
  }

  /** The dimension of the last remembered map camera, or null. */
  public static String lastCameraDimension() {
    return cameraMemory.lastDimension();
  }

  /** Forgets every remembered map camera (also done on {@link #leaveServer}). */
  public static void forgetCameras() {
    cameraMemory.clear();
  }

  /**
   * Whether the in-game map is shown: the server is V2+, the server and the client setting both
   * allow it, BlueMap is configured, and the settings probe for the default map has not failed.
   * Only the last term changes during a session, and only from true to false.
   */
  public static boolean mapAvailable() {
    return supportsMap()
        && serverWorldMapEnabled
        && DisquestsClient.CONFIG.worldMap()
        && hasBluemap()
        && !mapUnreachable();
  }

  /** True once the BlueMap settings probe for the default map has failed. */
  public static boolean mapUnreachable() {
    return hasBluemap() && SharedTileCache.get().settingsFailed(defaultMapId());
  }

  /** BlueMap map id of the server's default dimension, or null when it has no mapping. */
  public static String defaultMapId() {
    return bluemapMapNames.get(bluemapDefaultMap);
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
