package com.antixray.plugin.database.dao;

import com.antixray.plugin.database.DatabaseProvider;

public class PunishmentDAO {
    private final DatabaseProvider provider;

    public PunishmentDAO(DatabaseProvider provider) {
        this.provider = provider;
    }

    public String getCreateTableSQL() {
        return "CREATE TABLE IF NOT EXISTS punishments (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(16) NOT NULL," +
                "action VARCHAR(32) NOT NULL," +
                "risk_score DOUBLE NOT NULL," +
                "timestamp BIGINT NOT NULL" +
                ")";
    }
}