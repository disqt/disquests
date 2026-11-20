package com.disqt.disquests.client.data;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The quest graph over everything the client knows, rebuilt only when the cache changes. */
public final class QuestGraphs {

  private static long builtAtVersion = -1;
  private static QuestGraph cached;

  private QuestGraphs() {}

  public static synchronized QuestGraph current() {
    long version = ClientCache.getVersion();
    if (cached == null || version != builtAtVersion) {
      cached = build(ClientCache.getMyQuests(), ClientCache.getServerQuests());
      builtAtVersion = version;
    }
    return cached;
  }

  /** One graph over both lists; a quest in both counts once, the first list winning. */
  static QuestGraph build(List<Quest> mine, List<Quest> server) {
    Map<UUID, QuestData> byId = new LinkedHashMap<>();
    for (Quest q : mine) byId.putIfAbsent(q.getId(), q.toData());
    for (Quest q : server) byId.putIfAbsent(q.getId(), q.toData());
    return QuestGraph.of(byId.values());
  }
}
