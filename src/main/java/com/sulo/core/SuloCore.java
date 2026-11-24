package com.sulo.core;

import com.sulo.core.database.DatabaseManager;
import com.sulo.core.language.LanguageManager;
import com.sulo.core.player.PlayerDataListener;
import com.sulo.core.features.CoreFeatures;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SuloCore extends JavaPlugin {
    private DatabaseManager databaseManager;
    private LanguageManager languageManager;
    private CoreFeatures coreFeatures;

    @Override public void onEnable() {
        saveDefaultConfig();
        languageManager = new LanguageManager(this);
        databaseManager = new DatabaseManager(this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        coreFeatures = new CoreFeatures(this);
        getLogger().info("SuloCore enabled. Version: " + getDescription().getVersion());
        databaseManager.connectAsync().thenAccept(connected -> {
            if (connected) Bukkit.getScheduler().runTask(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers())
                    databaseManager.savePlayer(player.getUniqueId().toString(), player.getName());
            });
        });
    }
    @Override public void onDisable() {
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("SuloCore disabled.");
    }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
}
