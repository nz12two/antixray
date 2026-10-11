package com.antixray.plugin.database.impl;

import com.antixray.plugin.config.ConfigLoader.DatabaseConfig;
import com.antixray.plugin.database.DatabaseProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class PostgreSQLProvider implements DatabaseProvider {
    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public PostgreSQLProvider(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s?ssl=%b",
                config.host(), config.port(), config.database(), config.useSsl()));
        hikariConfig.setUsername(config.user());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(config.poolSize());
        hikariConfig.setConnectionTimeout(config.timeout());
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void executeUpdate(String sql) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public String getDatabaseType() {
        return "postgresql";
    }
}
