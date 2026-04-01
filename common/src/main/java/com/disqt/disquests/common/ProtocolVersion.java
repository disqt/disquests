package com.disqt.disquests.common;

public final class ProtocolVersion {
  /** Pre-tags protocol. No version sent in REQUEST_SYNC. */
  public static final int V0 = 0;

  /** Tags protocol. Adds tags to quests, predefinedTags to handshake, SYNC_TAGS packet. */
  public static final int V1 = 1;

  /** The current protocol version this build speaks. */
  public static final int CURRENT = V1;

  private ProtocolVersion() {}
}
