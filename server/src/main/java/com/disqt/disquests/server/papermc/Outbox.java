package com.disqt.disquests.server.papermc;

import java.util.Collection;
import java.util.UUID;

/** Where the server's packets go: the online players who run the mod. */
public interface Outbox {

  /** Sends one packet; dropped when the player is offline or does not run the mod. */
  void send(UUID player, byte[] data);

  /** The online players listening on the mod's channel. */
  Collection<UUID> onlineModPlayers();

  /** The player's current name, or null when they are offline. */
  String name(UUID player);
}
