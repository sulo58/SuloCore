package com.sulo.core;

import com.sulo.core.database.DatabaseManager;
import com.sulo.core.language.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class SuloCore extends JavaPlugin {

    private DatabaseManager databaseManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        languageManager = new LanguageManager(this);
        databaseManager = new DatabaseManager(this);

        if (getCommand("sulocore") != null) {
            getCommand("sulocore").setExecutor(this::handleCommand);
        }

        getLogger().info("SuloCore enabled. Version: " + getDescription().getVersion());
        databaseManager.connect();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        getLogger().info("SuloCore disabled.");
    }

    private boolean handleCommand(CommandSender sender, Command command,
                                  String label, String[] args) {
        if (!sender.hasPermission("sulocore.admin")) {
            sender.sendMessage(languageManager.message("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(languageManager.message("info")
                    .replace("%version%", getDescription().getVersion()));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            languageManager.reload();
            sender.sendMessage(languageManager.message("reloaded"));
            return true;
        }

        sender.sendMessage("§cUsage: /sulocore [info|reload]");
        return true;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}
