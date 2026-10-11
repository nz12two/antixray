package com.antixray.plugin.database.dao;

import com.antixray.plugin.database.DatabaseProvider;

public class EvidenceDAO {
    private final DatabaseProvider provider;

    public EvidenceDAO(DatabaseProvider provider) {
        this.provider = provider;
    }

    public String getCreateTableSQL() {
        return "CREATE TABLE IF NOT EXISTS evidence (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(16) NOT NULL," +
                "timestamp BIGINT NOT NULL," +
                "risk_score DOUBLE NOT NULL," +
                "detector_scores TEXT," +
                "triggered_detectors TEXT," +
                "world_name VARCHAR(64)," +
                "session_id VARCHAR(16)," +
                "detailed_reason TEXT," +
                "metadata TEXT" +
                ")";
    }
}