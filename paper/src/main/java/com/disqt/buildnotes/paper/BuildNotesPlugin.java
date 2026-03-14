package com.disqt.buildnotes.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

public class BuildNotesPlugin extends JavaPlugin {

    public static final String CHANNEL = "buildnotes:main";

    private DataManager dataManager;
    private PermissionManager permissionManager;
    private ServerPacketHandler packetHandler;

    @Override
    public void onEnable() {
        dataManager = new DataManager(getDataFolder().toPath());
        dataManager.initialize();

        permissionManager = new PermissionManager(getDataFolder().toPath());

        packetHandler = new ServerPacketHandler(this, dataManager, permissionManager);

        Messenger messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, CHANNEL);
        messenger.registerIncomingPluginChannel(this, CHANNEL, packetHandler);

        Bukkit.getPluginManager().registerEvents(packetHandler, this);

        getCommand("buildnotes").setExecutor(new Commands(permissionManager));

        getLogger().info("BuildNotes enabled");
    }

    @Override
    public void onDisable() {
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(this, CHANNEL);
        messenger.unregisterIncomingPluginChannel(this, CHANNEL, packetHandler);

        if (dataManager != null) {
            dataManager.close();
        }

        getLogger().info("BuildNotes disabled");
    }
}
