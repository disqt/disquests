package com.disqt.disquests.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Commands implements CommandExecutor {
    private final DisquestsPlugin plugin;

    public Commands(DisquestsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Usage: /disquests reload");
            return true;
        }
        plugin.getDisquestsConfig().reload(plugin);
        sender.sendMessage("Disquests config reloaded.");
        return true;
    }
}
