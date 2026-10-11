package com.antixray.plugin.database.dao;

import com.antixray.plugin.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerDAO {
    private final DatabaseProvider provider;
    private final String dbType;

    public PlayerDAO(DatabaseProvider provider) {
        this.provider = provider;
        this.dbType = provider.getDatabaseType();
    }

    public String getCreateTableSQL() {
        return "CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(16) NOT NULL," +
                "total_blocks_mined BIGINT DEFAULT 0," +
                "total_ores_found BIGINT DEFAULT 0," +
                "total_flags INT DEFAULT 0," +
                "peak_risk_score DOUBLE DEFAULT 0," +
                "current_risk_score DOUBLE DEFAULT 0," +
                "last_seen BIGINT," +
                "first_seen BIGINT" +
                ")";
    }

    public void savePlayer(UUID uuid, String name) throws SQLException {
        String sql = buildUpsertSQL();
        long now = System.currentTimeMillis();
        try (Connection conn = provider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setLong(3, now);
            stmt.setLong(4, now);
            stmt.setString(5, name);
            stmt.setLong(6, now);
            stmt.executeUpdate();
        }
    }

    private String buildUpsertSQL() {
        return switch (dbType) {
            case "mysql", "mariadb" ->
                "INSERT INTO players (uuid, player_name, first_seen, last_seen) " +
                "VALUES (?, ?, ?, ?) AS new " +
                "ON DUPLICATE KEY UPDATE player_name=new.player_name, last_seen=new.last_seen";
            case "postgresql" ->
                "INSERT INTO players (uuid, player_name, first_seen, last_seen) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET player_name=EXCLUDED.player_name, last_seen=EXCLUDED.last_seen";
            default -> // sqlite
                "INSERT INTO players (uuid, player_name, first_seen, last_seen) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET player_name=excluded.player_name, last_seen=excluded.last_seen";
        };
    }

    public void updateRiskScore(UUID uuid, double score) throws SQLException {
        String sql = switch (dbType) {
            case "mysql", "mariadb", "postgresql" ->
                "UPDATE players SET current_risk_score=?, peak_risk_score=GREATEST(peak_risk_score,?), last_seen=? WHERE uuid=?";
            default -> // sqlite
                "UPDATE players SET current_risk_score=?, peak_risk_score=MAX(peak_risk_score,?), last_seen=? WHERE uuid=?";
        };
        try (Connection conn = provider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, score);
            stmt.setDouble(2, score);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setString(4, uuid.toString());
            stmt.executeUpdate();
        }
    }

    public double getRiskScore(UUID uuid) throws SQLException {
        String sql = "SELECT current_risk_score FROM players WHERE uuid=?";
        try (Connection conn = provider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("current_risk_score");
            }
        }
        return 0;
    }
}
