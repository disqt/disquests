package com.disqt.disquests.server.papermc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor {
  private final DisquestsPlugin plugin;
  private final DataManager dataManager;
  private final ServerPacketHandler packetHandler;

  public Commands(
      DisquestsPlugin plugin, DataManager dataManager, ServerPacketHandler packetHandler) {
    this.plugin = plugin;
    this.dataManager = dataManager;
    this.packetHandler = packetHandler;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sender.sendMessage("Usage: /disquests <reload|reset>");
      return true;
    }

    if (args[0].equalsIgnoreCase("reload")) {
      plugin.getDisquestsConfig().reload(plugin);
      sender.sendMessage("Disquests config reloaded.");
      return true;
    }

    if (args[0].equalsIgnoreCase("reset")) {
      if (!plugin.getDisquestsConfig().isDebug()) {
        sender.sendMessage("Reset is only available in debug mode.");
        return true;
      }
      if (sender instanceof Player) {
        sender.sendMessage("Reset can only be run from console/RCON.");
        return true;
      }
      dataManager.resetDatabase();
      packetHandler.resendHandshakes();
      sender.sendMessage("Disquests database reset. Handshakes resent.");
      return true;
    }

    sender.sendMessage("Usage: /disquests <reload|reset>");
    return true;
  }
}
