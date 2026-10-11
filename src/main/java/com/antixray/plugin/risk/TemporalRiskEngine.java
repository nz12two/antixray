package com.antixray.plugin.risk;

import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;

import java.util.*;

public class TemporalRiskEngine {
    private final Map<UUID, TemporalProfile> profiles = new java.util.concurrent.ConcurrentHashMap<>();
    private final double decayFactor;
    private final double historicalWeight;
    private final double previousWeight;
    private final double currentWeight;
    private final long decayIntervalMs;

    public TemporalRiskEngine(double decayFactor, double historicalWeight, double previousWeight, double currentWeight) {
        this(decayFactor, historicalWeight, previousWeight, currentWeight, 3 * 60 * 1000L);
    }

    public TemporalRiskEngine(double decayFactor, double historicalWeight, double previousWeight, double currentWeight, long decayIntervalMs) {
        this.decayFactor = decayFactor;
        this.historicalWeight = historicalWeight;
        this.previousWeight = previousWeight;
        this.currentWeight = currentWeight;
        this.decayIntervalMs = decayIntervalMs;
    }

    public double compute(UUID playerId, PlayerData data, List<DetectionResult> results,
                          double currentProbScore, Map<String, Double> weights) {
        TemporalProfile profile = profiles.computeIfAbsent(playerId, TemporalProfile::new);

        double currentRisk = Math.max(0, Math.min(100, currentProbScore));

        double temporalRisk = (currentRisk * currentWeight)
                + (profile.previousRisk * previousWeight)
                + (profile.historicalRisk * historicalWeight);

        temporalRisk = temporalRisk / (currentWeight + previousWeight + historicalWeight);

        applyDecay(profile);

        profile.previousRisk = currentRisk;
        profile.historicalRisk = profile.historicalRisk * (1 - historicalWeight) + temporalRisk * historicalWeight;
        profile.lastUpdateTime = System.currentTimeMillis();

        return Math.max(0, Math.min(100, temporalRisk));
    }

    private void applyDecay(TemporalProfile profile) {
        long elapsed = System.currentTimeMillis() - profile.lastUpdateTime;
        if (elapsed >= decayIntervalMs) {
            int cycles = (int) (elapsed / decayIntervalMs);
            for (int i = 0; i < cycles; i++) {
                profile.previousRisk *= decayFactor;
                profile.historicalRisk *= decayFactor;
            }
            profile.lastUpdateTime += cycles * decayIntervalMs;
        }
    }

    public TemporalProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, TemporalProfile::new);
    }

    public void reset(UUID playerId) { profiles.remove(playerId); }

    public static class TemporalProfile {
        public final UUID playerId;
        public double previousRisk;
        public double historicalRisk;
        public long lastUpdateTime;

        public TemporalProfile(UUID playerId) {
            this.playerId = playerId;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
}
