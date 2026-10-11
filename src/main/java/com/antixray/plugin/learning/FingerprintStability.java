package com.antixray.plugin.learning;

import java.util.*;
import java.util.stream.Collectors;

public class FingerprintStability {
    private final SimilarityEngine engine;
    private final int minSessions;
    private final double stabilityThreshold;

    public FingerprintStability() {
        this(new SimilarityEngine(), 3, 0.80);
    }

    public FingerprintStability(SimilarityEngine engine, int minSessions, double stabilityThreshold) {
        this.engine = engine;
        this.minSessions = minSessions;
        this.stabilityThreshold = stabilityThreshold;
    }

    public StabilityResult evaluate(List<Fingerprint> recentSessions) {
        List<Fingerprint> valid = recentSessions.stream()
                .filter(f -> f.sessionDurationMinutes() >= 3)
                .collect(Collectors.toList());

        if (valid.size() < minSessions) {
            return new StabilityResult(false, 0, valid.size(), 0, List.of());
        }

        List<Double> similarities = new ArrayList<>();
        for (int i = 0; i < valid.size() - 1; i++) {
            for (int j = i + 1; j < valid.size(); j++) {
                double sim = engine.compare(valid.get(i), valid.get(j)).finalScore();
                similarities.add(sim);
            }
        }

        double avgSimilarity = similarities.stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(0);

        double minSimilarity = similarities.stream()
                .mapToDouble(Double::doubleValue)
                .min().orElse(0);

        boolean stable = avgSimilarity >= stabilityThreshold && minSimilarity >= stabilityThreshold * 0.7;

        return new StabilityResult(stable, avgSimilarity, valid.size(), minSimilarity, similarities);
    }

    public record StabilityResult(boolean stable, double averageSimilarity,
                                   int sessionCount, double minSimilarity,
                                   List<Double> pairwiseSimilarities) {
        public String explain() {
            if (sessionCount < 3) {
                return "§7Need at least 3 sessions (have " + sessionCount + ")";
            }
            String status = stable ? "§aSTABLE" : "§cUNSTABLE";
            return String.format(
                    "§8Stability: %s §8(avg=§b%.0f%%§8 min=§b%.0f%%§8 sessions=§e%d§8)",
                    status, averageSimilarity * 100, minSimilarity * 100, sessionCount);
        }
    }
}
