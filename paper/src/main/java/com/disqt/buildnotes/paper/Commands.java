package com.disqt.buildnotes.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class Commands implements CommandExecutor {

    private final PermissionManager permissionManager;

    public Commands(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "allow" -> handleAllow(sender, args);
            case "disallow" -> handleDisallow(sender, args);
            case "list" -> handleList(sender);
            case "allow_all" -> handleAllowAll(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleAllow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /buildnotes allow <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found: " + args[1]);
            return;
        }
        permissionManager.addEditor(target.getUniqueId());
        sender.sendMessage("Added " + target.getName() + " as a BuildNotes editor.");
    }

    private void handleDisallow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /buildnotes disallow <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found: " + args[1]);
            return;
        }
        permissionManager.removeEditor(target.getUniqueId());
        sender.sendMessage("Removed " + target.getName() + " from BuildNotes editors.");
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage("BuildNotes Permissions:");
        sender.sendMessage("  Allow all: " + permissionManager.isAllowAll());
        sender.sendMessage("  Editors:");
        for (UUID uuid : permissionManager.getEditors()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            sender.sendMessage("    - " + (name != null ? name : uuid.toString()));
        }
        if (permissionManager.getEditors().isEmpty()) {
            sender.sendMessage("    (none)");
        }
    }

    private void handleAllowAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /buildnotes allow_all <true|false>");
            return;
        }
        boolean value = Boolean.parseBoolean(args[1]);
        permissionManager.setAllowAll(value);
        sender.sendMessage("BuildNotes allow_all set to: " + value);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("BuildNotes commands:");
        sender.sendMessage("  /buildnotes allow <player> - Grant editor access");
        sender.sendMessage("  /buildnotes disallow <player> - Revoke editor access");
        sender.sendMessage("  /buildnotes list - List permissions");
        sender.sendMessage("  /buildnotes allow_all <true|false> - Toggle open editing");
    }
}
