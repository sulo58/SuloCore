package com.sulo.core.player;

import com.sulo.core.SuloCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerDataListener implements Listener {
    private final SuloCore plugin;

    public PlayerDataListener(SuloCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getDatabaseManager().isConnected()) return;
        plugin.getDatabaseManager().savePlayer(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName()
        );
    }
}
