package com.sulo.core.database;

import com.sulo.core.SuloCore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

public final class DatabaseManager {
    private final SuloCore plugin;
    private volatile HikariDataSource dataSource;

    public DatabaseManager(SuloCore plugin) { this.plugin = plugin; }

    public CompletableFuture<Boolean> connectAsync() {
        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            plugin.getLogger().warning("MySQL is disabled in config.yml. Persistent player data is unavailable.");
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            HikariDataSource candidate = null;
            try {
                String host = plugin.getConfig().getString("database.host", "localhost");
                int port = plugin.getConfig().getInt("database.port", 3306);
                String database = plugin.getConfig().getString("database.database", "sulocore");
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
                config.setUsername(plugin.getConfig().getString("database.username", "root"));
                config.setPassword(plugin.getConfig().getString("database.password", ""));
                int poolSize = Math.max(2, plugin.getConfig().getInt("database.pool-size", 10));
                config.setMaximumPoolSize(poolSize);
                config.setMinimumIdle(Math.min(2, poolSize));
                config.setConnectionTimeout(plugin.getConfig().getLong("database.connection-timeout", 10000L));
                config.setPoolName("SuloCore-MySQL");
                config.setInitializationFailTimeout(10000L);
                candidate = new HikariDataSource(config);
                try (Connection connection = candidate.getConnection()) { createTables(connection); }
                dataSource = candidate;
                plugin.getLogger().info("MySQL connected; SuloCore tables are ready.");
                return true;
            } catch (Exception ex) {
                if (candidate != null) candidate.close();
                plugin.getLogger().severe("MySQL connection/setup failed: " + ex.getMessage());
                return false;
            }
        });
    }

    private void createTables(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_players (uuid CHAR(36) PRIMARY KEY, username VARCHAR(16) NOT NULL, balance DECIMAL(18,2) NOT NULL DEFAULT 0.00, xp BIGINT NOT NULL DEFAULT 0, level INT NOT NULL DEFAULT 1, first_join TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_player_stats (uuid CHAR(36) PRIMARY KEY, playtime_seconds BIGINT NOT NULL DEFAULT 0, kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, blocks_placed BIGINT NOT NULL DEFAULT 0, blocks_broken BIGINT NOT NULL DEFAULT 0, CONSTRAINT fk_sc_stats_player FOREIGN KEY (uuid) REFERENCES sc_players(uuid) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_homes (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, home_name VARCHAR(32) NOT NULL, world_name VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw FLOAT NOT NULL DEFAULT 0, pitch FLOAT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_player_home (uuid, home_name), INDEX idx_homes_uuid (uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_warps (warp_name VARCHAR(32) PRIMARY KEY, world_name VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw FLOAT NOT NULL DEFAULT 0, pitch FLOAT NOT NULL DEFAULT 0, created_by CHAR(36) NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_punishments (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, staff_uuid CHAR(36) NOT NULL, punishment_type VARCHAR(16) NOT NULL, reason VARCHAR(255) NOT NULL, expires_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_punishments_uuid (uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_warnings (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, staff_uuid CHAR(36) NOT NULL, reason VARCHAR(255) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_warnings_uuid (uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_player_settings (uuid CHAR(36) PRIMARY KEY, private_messages BOOLEAN NOT NULL DEFAULT TRUE, teleport_requests BOOLEAN NOT NULL DEFAULT TRUE, scoreboard_enabled BOOLEAN NOT NULL DEFAULT TRUE, language VARCHAR(5) NOT NULL DEFAULT 'tr') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_transactions (id BIGINT AUTO_INCREMENT PRIMARY KEY, sender_uuid CHAR(36) NULL, receiver_uuid CHAR(36) NULL, amount DECIMAL(18,2) NOT NULL, transaction_type VARCHAR(24) NOT NULL, note VARCHAR(255) NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_transactions_sender (sender_uuid), INDEX idx_transactions_receiver (receiver_uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sc_audit_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_uuid CHAR(36) NULL, action VARCHAR(64) NOT NULL, target_uuid CHAR(36) NULL, details TEXT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_audit_actor (actor_uuid), INDEX idx_audit_target (target_uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    public void savePlayer(String uuid, String username) {
        if (!isConnected()) return;
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO sc_players (uuid, username, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE username=VALUES(username), last_seen=CURRENT_TIMESTAMP";
            try (Connection c = getConnection(); var p = c.prepareStatement(sql)) {
                p.setString(1, uuid); p.setString(2, username); p.executeUpdate();
                try (var stats = c.prepareStatement("INSERT IGNORE INTO sc_player_stats (uuid) VALUES (?)")) {
                    stats.setString(1, uuid); stats.executeUpdate();
                }
                try (var settings = c.prepareStatement("INSERT IGNORE INTO sc_player_settings (uuid) VALUES (?)")) {
                    settings.setString(1, uuid); settings.executeUpdate();
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Could not save player " + username + ": " + ex.getMessage());
            }
        });
    }

    public Connection getConnection() throws SQLException {
        HikariDataSource current = dataSource;
        if (current == null || current.isClosed()) throw new SQLException("MySQL is not connected.");
        return current.getConnection();
    }
    public boolean isConnected() {
        HikariDataSource current = dataSource;
        return current != null && !current.isClosed();
    }
    public void disconnect() {
        HikariDataSource current = dataSource;
        dataSource = null;
        if (current != null && !current.isClosed()) {
            current.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }
}