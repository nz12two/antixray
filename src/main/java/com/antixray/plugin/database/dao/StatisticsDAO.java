package com.antixray.plugin.database.dao;

import com.antixray.plugin.database.DatabaseProvider;

public class StatisticsDAO {
    private final DatabaseProvider provider;

    public StatisticsDAO(DatabaseProvider provider) {
        this.provider = provider;
    }

    public String getCreateTableSQL() {
        return "CREATE TABLE IF NOT EXISTS player_statistics (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "blocks_per_minute DOUBLE DEFAULT 0," +
                "ores_per_minute DOUBLE DEFAULT 0," +
                "diamonds_per_hour DOUBLE DEFAULT 0," +
                "ancient_debris_per_hour DOUBLE DEFAULT 0," +
                "mining_efficiency DOUBLE DEFAULT 0," +
                "ore_to_block_ratio DOUBLE DEFAULT 0," +
                "hidden_to_exposed_ratio DOUBLE DEFAULT 0," +
                "average_tunnel_length DOUBLE DEFAULT 0," +
                "straight_tunnel_pct DOUBLE DEFAULT 0," +
                "branch_mining_pct DOUBLE DEFAULT 0," +
                "last_updated BIGINT" +
                ")";
    }
}