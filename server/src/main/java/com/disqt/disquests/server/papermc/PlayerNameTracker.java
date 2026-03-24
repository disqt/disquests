package com.disqt.disquests.server.papermc;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerNameTracker implements Listener {
    private final DataManager dataManager;

    public PlayerNameTracker(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        dataManager.upsertPlayerName(
            event.getPlayer().getUniqueId(),
            event.getPlayer().getName()
        );
    }
}
