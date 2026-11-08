package com.disqt.disquests.common;

public final class ProtocolVersion {
  /** Pre-tags protocol. No version sent in REQUEST_SYNC. */
  public static final int V0 = 0;

  /** Tags protocol. Adds tags to quests, predefinedTags to handshake, SYNC_TAGS packet. */
  public static final int V1 = 1;

  /** Polygon protocol. Adds an optional polygon vertex list to quests. */
  public static final int V2 = 2;

  /** Completion protocol. Adds completedAt, completedBy and completedByName to quests. */
  public static final int V3 = 3;

  /** The current protocol version this build speaks. */
  public static final int CURRENT = V3;

  private ProtocolVersion() {}
}
