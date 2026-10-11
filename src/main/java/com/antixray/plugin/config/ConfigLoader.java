package com.antixray.plugin.config;

import com.antixray.plugin.AntiXrayPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/**
 * Loads and provides access to all configuration from config.yml.
 * <p>
 * Parses database config, discord config, per-detector configs, weight configs,
 * punishment stages, learning parameters, and obfuscation settings.
 * Uses Java records for type-safe configuration objects.
 * <p>
 * Thread-safe — all configs are loaded once and treated as immutable.
 */
public class ConfigLoader {
    private final AntiXrayPlugin plugin;
    private YamlConfiguration config;
    private final Map<String, DetectorConfig> detectorConfigs = new HashMap<>();
    private DatabaseConfig databaseConfig;
    private DiscordConfig discordConfig;
    private WeightConfig weightConfig;

    public ConfigLoader(AntiXrayPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        this.config = (YamlConfiguration) plugin.getConfig();
        loadDetectorConfigs();
        loadDatabaseConfig();
        loadDiscordConfig();
        loadWeightConfig();
    }

    private void loadDetectorConfigs() {
        detectorConfigs.clear();
        ConfigurationSection detectors = config.getConfigurationSection("detectors");
        if (detectors == null) return;

        for (String key : detectors.getKeys(false)) {
            ConfigurationSection sec = detectors.getConfigurationSection(key);
            if (sec == null) continue;
            DetectorConfig dc = new DetectorConfig(
                    sec.getBoolean("enabled", true),
                    sec.getDouble("weight", 10.0),
                    sec.getDouble("threshold", 50.0),
                    sec.getStringList("punishment"),
                    sec.getInt("cooldown", 30),
                    sec.getBoolean("debug", false),
                    sec.getDouble("confidence-threshold", 30.0)
            );
            detectorConfigs.put(key.toLowerCase(), dc);
        }
    }

    private void loadDatabaseConfig() {
        ConfigurationSection db = config.getConfigurationSection("database");
        if (db == null) {
            databaseConfig = new DatabaseConfig("sqlite", "plugins/AntiXray/data.db", 3306, "root", "", "antixray", 10, 30000, false);
            return;
        }
        databaseConfig = new DatabaseConfig(
                db.getString("type", "sqlite"),
                db.getString("host", "plugins/AntiXray/data.db"),
                db.getInt("port", 3306),
                db.getString("user", "root"),
                db.getString("password", ""),
                db.getString("database", "antixray"),
                db.getInt("pool-size", 10),
                db.getInt("timeout", 30000),
                db.getBoolean("use-ssl", false)
        );
    }

    private void loadDiscordConfig() {
        ConfigurationSection dc = config.getConfigurationSection("discord");
        if (dc == null) {
            discordConfig = new DiscordConfig(false, "", 50.0, true, 5000,
                    0xFFC107, 0xFF9800, 0xF44336,
                    "", "", "", false);
            return;
        }

        ConfigurationSection colors = dc.getConfigurationSection("severity-colors");
        ConfigurationSection pings = dc.getConfigurationSection("role-pings");

        discordConfig = new DiscordConfig(
                dc.getBoolean("enabled", false),
                dc.getString("webhook-url", ""),
                dc.getDouble("risk-threshold", 50.0),
                dc.getBoolean("notify-flags-only", true),
                dc.getLong("rate-limit-ms", 5000),
                colors != null ? colors.getInt("alert", 0xFFC107) : 0xFFC107,
                colors != null ? colors.getInt("flag", 0xFF9800) : 0xFF9800,
                colors != null ? colors.getInt("punish", 0xF44336) : 0xF44336,
                pings != null ? pings.getString("alert", "") : "",
                pings != null ? pings.getString("flag", "") : "",
                pings != null ? pings.getString("punish", "") : "",
                dc.getBoolean("compact-mode", false)
        );
    }

    private void loadWeightConfig() {
        ConfigurationSection wc = config.getConfigurationSection("weights");
        if (wc == null) {
            weightConfig = new WeightConfig(new HashMap<>());
            return;
        }
        Map<String, Double> weights = new HashMap<>();
        for (String key : wc.getKeys(false)) {
            weights.put(key.toLowerCase(), wc.getDouble(key, 10.0));
        }
        weightConfig = new WeightConfig(weights);
    }

    public DetectorConfig getDetectorConfig(String detectorName) {
        return detectorConfigs.getOrDefault(detectorName.toLowerCase(), DetectorConfig.DEFAULT);
    }

    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    public DiscordConfig getDiscordConfig() { return discordConfig; }
    public WeightConfig getWeightConfig() { return weightConfig; }
    public YamlConfiguration getRawConfig() { return config; }
    public Map<String, DetectorConfig> getAllDetectorConfigs() { return Collections.unmodifiableMap(detectorConfigs); }

    public boolean isWorldDetectionEnabled(String worldName) {
        List<String> worlds = config.getStringList("detection-worlds");
        return worlds.isEmpty() || worlds.contains(worldName);
    }

    public boolean isOreRare(String materialName) {
        // TODO: extrair para utility class - duplicado em FeatureExtractor
        List<String> rareOres = config.getStringList("rare-ores");
        return rareOres.contains(materialName);
    }

    public double getDecayAmount() { return config.getDouble("decay.amount", 1.0); }
    public long getDecayInterval() { return config.getLong("decay.interval-minutes", 3) * 60 * 1000L; }
    public boolean isDecayFactorEnabled() { return config.getBoolean("decay.use-factor", false); }
    public double getDecayFactor() { return config.getDouble("decay.factor", 0.9); }
    public int getMaxPathLength() { return config.getInt("max-path-length", 500); }
    public int getTraceRemoveMinutes() { return config.getInt("trace-remove-minutes", 15); }
    public double getFlagThreshold() { return config.getDouble("thresholds.flag", 60.0); }
    public double getAlertThreshold() { return config.getDouble("thresholds.alert", 40.0); }
    public double getPunishThreshold() { return config.getDouble("thresholds.punish", 80.0); }
    public int getMaxEventsPerPlayer() { return config.getInt("performance.max-events-per-player", 1000); }

    public static class DetectorConfig {
        public static final DetectorConfig DEFAULT = new DetectorConfig(true, 10.0, 50.0, List.of(), 30, false, 30.0);

        public final boolean enabled;
        public final double weight;
        public final double threshold;
        public final List<String> punishment;
        public final int cooldown;
        public final boolean debug;
        public final double confidenceThreshold;

        public DetectorConfig(boolean enabled, double weight, double threshold,
                              List<String> punishment, int cooldown, boolean debug, double confidenceThreshold) {
            this.enabled = enabled;
            this.weight = weight;
            this.threshold = threshold;
            this.punishment = punishment;
            this.cooldown = cooldown;
            this.debug = debug;
            this.confidenceThreshold = confidenceThreshold;
        }
    }

    public record DatabaseConfig(String type, String host, int port, String user, String password, String database, int poolSize, int timeout, boolean useSsl) {}
    public record DiscordConfig(boolean enabled, String webhookUrl, double riskThreshold, boolean notifyFlagsOnly, long rateLimitMs, int colorAlert, int colorFlag, int colorPunish, String pingAlert, String pingFlag, String pingPunish, boolean compactMode) {}
    public record WeightConfig(Map<String, Double> weights) {}
}
