package com.antixray.plugin.database;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.config.ConfigLoader.DatabaseConfig;
import com.antixray.plugin.database.dao.*;
import com.antixray.plugin.database.impl.*;

import java.sql.SQLException;

/**
 * Database module that manages connection lifecycle via HikariCP.
 * <p>
 * Supports SQLite, MySQL, MariaDB, and PostgreSQL. Creates all required tables
 * and indices on enable. Injects DatabaseProvider into all DAOs (PlayerDAO,
 * EvidenceDAO, StatisticsDAO, PunishmentDAO, LearningDAO).
 */
public class DatabaseModule extends Module {
    private final AntiXrayPlugin plugin;
    private DatabaseProvider provider;
    private PlayerDAO playerDAO;
    private EvidenceDAO evidenceDAO;
    private StatisticsDAO statisticsDAO;
    private PunishmentDAO punishmentDAO;
    private LearningDAO learningDAO;

    public DatabaseModule(AntiXrayPlugin plugin) {
        super("database");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        DatabaseConfig config = plugin.getConfigLoader().getDatabaseConfig();
        this.provider = createProvider(config);

        try {
            provider.initialize();
            plugin.getLogger().info("Database connected: " + config.type());

            this.playerDAO = new PlayerDAO(provider);
            this.evidenceDAO = new EvidenceDAO(provider);
            this.statisticsDAO = new StatisticsDAO(provider);
            this.punishmentDAO = new PunishmentDAO(provider);
            this.learningDAO = new LearningDAO(provider);

            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (provider != null) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Error shutting down database: " + e.getMessage());
            }
        }
    }

    private DatabaseProvider createProvider(DatabaseConfig config) {
        return switch (config.type().toLowerCase()) {
            case "mysql" -> new MySQLProvider(config);
            case "mariadb" -> new MariaDBProvider(config);
            case "postgresql" -> new PostgreSQLProvider(config);
            default -> new SQLiteProvider(config);
        };
    }

    private void createTables() throws SQLException {
        provider.executeUpdate(playerDAO.getCreateTableSQL());
        provider.executeUpdate(evidenceDAO.getCreateTableSQL());
        provider.executeUpdate(statisticsDAO.getCreateTableSQL());
        provider.executeUpdate(punishmentDAO.getCreateTableSQL());
        provider.executeUpdate(learningDAO.getCreateTableSQL());

        // Índices separados para compatibilidade cross-database
        // NOTA: MySQL não suporta IF NOT EXISTS em CREATE INDEX, então engole exceção de índice duplicado
        String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_punishments_player ON punishments(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_evidence_player ON evidence(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_evidence_timestamp ON evidence(timestamp)"
        };
        for (String idxSql : indexStatements) {
            try {
                provider.executeUpdate(idxSql);
            } catch (SQLException ignored) {
                // MySQL/MariaDB não suporta IF NOT EXISTS — índice já existe
            }
        }
    }

    public DatabaseProvider getProvider() { return provider; }
    public PlayerDAO getPlayerDAO() { return playerDAO; }
    public EvidenceDAO getEvidenceDAO() { return evidenceDAO; }
    public StatisticsDAO getStatisticsDAO() { return statisticsDAO; }
    public PunishmentDAO getPunishmentDAO() { return punishmentDAO; }
    public LearningDAO getLearningDAO() { return learningDAO; }
}
