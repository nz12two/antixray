package com.antixray.plugin.punishment;

import java.util.*;

public class PunishmentEvaluation {
    private final UUID playerId;
    private final String playerName;
    private final double riskScore;
    private final double confidence;
    private final int activeDetectors;
    private final int categoryCount;
    private final int metaFlagCount;
    private final double persistenceMinutes;
    private final int sessionCount;
    private final double fingerprintSimilarity;
    private final boolean hasReplayEvidence;
    private final boolean hasRecentFlags;
    private final List<String> categories;

    private PunishmentEvaluation(Builder b) {
        this.playerId = b.playerId;
        this.playerName = b.playerName;
        this.riskScore = b.riskScore;
        this.confidence = b.confidence;
        this.activeDetectors = b.activeDetectors;
        this.categoryCount = b.categoryCount;
        this.metaFlagCount = b.metaFlagCount;
        this.persistenceMinutes = b.persistenceMinutes;
        this.sessionCount = b.sessionCount;
        this.fingerprintSimilarity = b.fingerprintSimilarity;
        this.hasReplayEvidence = b.hasReplayEvidence;
        this.hasRecentFlags = b.hasRecentFlags;
        this.categories = b.categories;
    }

    public boolean qualifiesFor(StageConfig cfg) {
        if (!cfg.enabled()) return false;
        if (riskScore < cfg.minRisk()) return false;
        if (confidence < cfg.minConfidence()) return false;
        if (activeDetectors < cfg.minDetectors()) return false;
        if (categoryCount < cfg.minCategories()) return false;
        if (metaFlagCount < cfg.minMetaFlags()) return false;
        if (persistenceMinutes < cfg.minPersistenceMinutes()) return false;
        if (sessionCount < cfg.minSessions()) return false;
        if (fingerprintSimilarity < cfg.minFingerprintSimilarity()) return false;
        return true;
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public double riskScore() { return riskScore; }
    public double confidence() { return confidence; }
    public int activeDetectors() { return activeDetectors; }
    public int categoryCount() { return categoryCount; }
    public int metaFlagCount() { return metaFlagCount; }
    public double persistenceMinutes() { return persistenceMinutes; }
    public int sessionCount() { return sessionCount; }
    public double fingerprintSimilarity() { return fingerprintSimilarity; }
    public boolean hasReplayEvidence() { return hasReplayEvidence; }
    public boolean hasRecentFlags() { return hasRecentFlags; }
    public List<String> categories() { return categories; }

    public String summary() {
        return String.format(
                "risk=%.1f conf=%.1f det=%d cat=%d meta=%d persist=%.0fmin sess=%d fp=%.0f%%",
                riskScore, confidence, activeDetectors, categoryCount, metaFlagCount,
                persistenceMinutes, sessionCount, fingerprintSimilarity * 100);
    }

    public static class Builder {
        private UUID playerId;
        private String playerName;
        private double riskScore;
        private double confidence;
        private int activeDetectors;
        private int categoryCount;
        private int metaFlagCount;
        private double persistenceMinutes;
        private int sessionCount;
        private double fingerprintSimilarity;
        private boolean hasReplayEvidence;
        private boolean hasRecentFlags;
        private List<String> categories = List.of();

        public Builder playerId(UUID v) { this.playerId = v; return this; }
        public Builder playerName(String v) { this.playerName = v; return this; }
        public Builder riskScore(double v) { this.riskScore = v; return this; }
        public Builder confidence(double v) { this.confidence = v; return this; }
        public Builder activeDetectors(int v) { this.activeDetectors = v; return this; }
        public Builder categoryCount(int v) { this.categoryCount = v; return this; }
        public Builder metaFlagCount(int v) { this.metaFlagCount = v; return this; }
        public Builder persistenceMinutes(double v) { this.persistenceMinutes = v; return this; }
        public Builder sessionCount(int v) { this.sessionCount = v; return this; }
        public Builder fingerprintSimilarity(double v) { this.fingerprintSimilarity = v; return this; }
        public Builder hasReplayEvidence(boolean v) { this.hasReplayEvidence = v; return this; }
        public Builder hasRecentFlags(boolean v) { this.hasRecentFlags = v; return this; }
        public Builder categories(List<String> v) { this.categories = v; return this; }

        public PunishmentEvaluation build() {
            Objects.requireNonNull(playerId, "playerId");
            return new PunishmentEvaluation(this);
        }
    }
}
