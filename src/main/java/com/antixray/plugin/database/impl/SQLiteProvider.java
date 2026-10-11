package com.antixray.plugin.database.impl;

import com.antixray.plugin.config.ConfigLoader.DatabaseConfig;
import com.antixray.plugin.database.DatabaseProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteProvider implements DatabaseProvider {
    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public SQLiteProvider(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() throws SQLException {
        String path = config.host();
        File dbFile = new File(path);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + path);
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setConnectionTimeout(config.timeout());
        // PRAGMAs são aplicados em toda nova conexão
        hikariConfig.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON");

        dataSource = new HikariDataSource(hikariConfig);

        // Aplica PRAGMAs uma vez na inicialização
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initialize();
        }
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
        return "sqlite";
    }
}
