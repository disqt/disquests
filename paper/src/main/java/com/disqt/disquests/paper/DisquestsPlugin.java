package com.disqt.disquests.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

public class DisquestsPlugin extends JavaPlugin {
    public static final String CHANNEL = "disquests:main";

    private DataManager dataManager;
    private Config disquestsConfig;
    private ServerPacketHandler packetHandler;

    @Override
    public void onEnable() {
        disquestsConfig = new Config(this);
        dataManager = new DataManager(getDataFolder().toPath());
        dataManager.initialize();

        packetHandler = new ServerPacketHandler(this, dataManager, disquestsConfig);

        Messenger messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, CHANNEL);
        messenger.registerIncomingPluginChannel(this, CHANNEL, packetHandler);

        Bukkit.getPluginManager().registerEvents(packetHandler, this);
        Bukkit.getPluginManager().registerEvents(new PlayerNameTracker(dataManager), this);

        getCommand("disquests").setExecutor(new Commands(this, dataManager, packetHandler));

        getLogger().info("Disquests enabled");
    }

    @Override
    public void onDisable() {
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(this, CHANNEL);
        messenger.unregisterIncomingPluginChannel(this, CHANNEL, packetHandler);
        if (dataManager != null) dataManager.close();
        getLogger().info("Disquests disabled");
    }

    public DataManager getDataManager() { return dataManager; }
    public Config getDisquestsConfig() { return disquestsConfig; }
}
