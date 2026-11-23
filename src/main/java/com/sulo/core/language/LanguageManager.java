package com.sulo.core.language;

import com.sulo.core.SuloCore;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;

public final class LanguageManager {

    private final SuloCore plugin;
    private YamlConfiguration messages;

    public LanguageManager(SuloCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String language = plugin.getConfig().getString("language", "tr")
                .toLowerCase(Locale.ROOT);
        if (!language.equals("tr") && !language.equals("en")) {
            language = "tr";
        }

        String fileName = language.equals("en") ? "messages_en.yml" : "messages_tr.yml";
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String message(String key) {
        String prefix = color(messages.getString("prefix", ""));
        String content = color(messages.getString(key, "&cMissing message: " + key));
        return prefix + content;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
