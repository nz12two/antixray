package com.antixray.plugin.punishment;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record StageConfig(
        boolean enabled,
        double minRisk,
        double minConfidence,
        int minDetectors,
        int minCategories,
        int minMetaFlags,
        double minPersistenceMinutes,
        int minSessions,
        double minFingerprintSimilarity,
        long cooldownSeconds,
        List<String> commands,
        String message,
        boolean webhook
) {
    public static StageConfig fromSection(ConfigurationSection section) {
        if (section == null) {
            return new StageConfig(false, 100, 100, 99, 99, 99,
                    9999, 999, 1.0, 3600, List.of(), "", false);
        }
        return new StageConfig(
                section.getBoolean("enabled", false),
                section.getDouble("min-risk", 100),
                section.getDouble("min-confidence", 100),
                section.getInt("min-detectors", 99),
                section.getInt("min-categories", 99),
                section.getInt("min-meta-flags", 99),
                section.getDouble("min-persistence-minutes", 9999),
                section.getInt("min-sessions", 999),
                section.getDouble("min-fingerprint-similarity", 1.0),
                section.getLong("cooldown-seconds", 3600),
                section.getStringList("commands"),
                section.getString("message", ""),
                section.getBoolean("webhook", false)
        );
    }
}
