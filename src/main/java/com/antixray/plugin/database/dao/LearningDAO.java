package com.antixray.plugin.database.dao;

import com.antixray.plugin.database.DatabaseProvider;

public class LearningDAO {
    private final DatabaseProvider provider;

    public LearningDAO(DatabaseProvider provider) {
        this.provider = provider;
    }

    public String getCreateTableSQL() {
        return "CREATE TABLE IF NOT EXISTS server_learning (" +
                "id INT PRIMARY KEY," +
                "avg_blocks_per_minute DOUBLE DEFAULT 0," +
                "avg_ore_rate DOUBLE DEFAULT 0," +
                "avg_hidden_ore_ratio DOUBLE DEFAULT 0," +
                "total_blocks_tracked BIGINT DEFAULT 0," +
                "total_ores_tracked BIGINT DEFAULT 0," +
                "last_updated BIGINT" +
                ")";
    }
}