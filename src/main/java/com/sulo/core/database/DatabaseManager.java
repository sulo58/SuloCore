package com.sulo.core.database;

import com.sulo.core.SuloCore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager {

    private final SuloCore plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(SuloCore plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            plugin.getLogger().info("MySQL is disabled in config.yml.");
            return;
        }

        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database", "sulocore");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");
        int poolSize = Math.max(2, plugin.getConfig().getInt("database.pool-size", 10));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTimeout(
                plugin.getConfig().getLong("database.connection-timeout", 10000L));
        config.setPoolName("SuloCore-MySQL");

        try {
            dataSource = new HikariDataSource(config);
            try (Connection ignored = dataSource.getConnection()) {
                plugin.getLogger().info("MySQL connection successful.");
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("MySQL connection failed: " + exception.getMessage());
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("MySQL is not connected.");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }
}
