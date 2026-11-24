package com.sulo.core;

import com.sulo.core.database.DatabaseManager;
import com.sulo.core.language.LanguageManager;
import com.sulo.core.player.PlayerDataListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SuloCore extends JavaPlugin {
    private DatabaseManager databaseManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        languageManager = new LanguageManager(this);
        databaseManager = new DatabaseManager(this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        if (getCommand("sulocore") != null) {
            getCommand("sulocore").setExecutor(this::handleCommand);
        }
        getLogger().info("SuloCore enabled. Version: " + getDescription().getVersion());
        databaseManager.connectAsync().thenAccept(connected -> {
            if (connected) {
                Bukkit.getScheduler().runTask(this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        databaseManager.savePlayer(player.getUniqueId().toString(), player.getName());
                    }
                });
            }
        });
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("SuloCore disabled.");
    }

    private boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sulocore.admin")) {
            sender.sendMessage(languageManager.message("no-permission"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(languageManager.message("info")
                    .replace("%version%", getDescription().getVersion()));
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            String state = databaseManager.isConnected()
                    ? languageManager.message("database-connected")
                    : languageManager.message("database-disconnected");
            sender.sendMessage(languageManager.message("status").replace("%database%", state));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            languageManager.reload();
            databaseManager.disconnect();
            databaseManager.connectAsync();
            sender.sendMessage(languageManager.message("reloaded"));
            return true;
        }
        sender.sendMessage("§cUsage: /sulocore [info|status|reload]");
        return true;
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
}
