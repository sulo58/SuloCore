package com.sulo.core.features;

import com.sulo.core.SuloCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SidebarManager {
    private final SuloCore plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public SidebarManager(SuloCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 40L);
    }

    public void updateAll() {
        if (!plugin.getConfig().getBoolean("features.scoreboard", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("features.scoreboard", true)
                || !plugin.getConfig().getBoolean("player-settings.default-scoreboard", true)) return;
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("sulocore");
        if (objective == null) objective = board.registerNewObjective("sulocore", "dummy", color("&a&lSuloCore"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(color(plugin.getConfig().getString("scoreboard.title", "&a&lSuloCore")));

        String[] lines = {
                color("&7&m----------------"),
                color("&fOyuncu: &a" + player.getName()),
                color("&fÇevrimiçi: &a" + Bukkit.getOnlinePlayers().size()),
                color("&fDünya: &a" + player.getWorld().getName()),
                color("&fCan: &c" + (int) Math.ceil(player.getHealth()) + "&7/&c" + (int) player.getMaxHealth()),
                color("&fSeviye: &a" + player.getLevel()),
                color("&7&m----------------")
        };
        for (String entry : board.getEntries()) board.resetScores(entry);
        int score = lines.length;
        for (String line : lines) objective.getScore(line).setScore(score--);
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
