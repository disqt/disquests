package com.disqt.disquests.server.papermc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Sends on the mod's plugin channel to online players who listen on it. */
final class BukkitOutbox implements Outbox {

  private final Plugin plugin;

  BukkitOutbox(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void send(UUID player, byte[] data) {
    Player p = Bukkit.getPlayer(player);
    if (p != null && isModPlayer(p)) p.sendPluginMessage(plugin, DisquestsPlugin.CHANNEL, data);
  }

  @Override
  public Collection<UUID> onlineModPlayers() {
    List<UUID> players = new ArrayList<>();
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (isModPlayer(p)) players.add(p.getUniqueId());
    }
    return players;
  }

  @Override
  public String name(UUID player) {
    Player p = Bukkit.getPlayer(player);
    return p == null ? null : p.getName();
  }

  private static boolean isModPlayer(Player player) {
    return player.getListeningPluginChannels().contains(DisquestsPlugin.CHANNEL);
  }
}
