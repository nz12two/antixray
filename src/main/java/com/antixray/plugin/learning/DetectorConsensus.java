package com.antixray.plugin.learning;

import java.util.*;

public class DetectorConsensus {

    public enum Category {
        SPATIAL, BEHAVIOR, STATISTICAL, TEMPORAL, MOVEMENT, EXPLORATION
    }

    private static final Map<String, Category> DETECTOR_CATEGORIES = Map.ofEntries(
            Map.entry("Luck", Category.SPATIAL),
            Map.entry("Tunnel", Category.SPATIAL),
            Map.entry("BranchMining", Category.SPATIAL),
            Map.entry("WallTracking", Category.SPATIAL),
            Map.entry("HiddenOre", Category.SPATIAL),
            Map.entry("OreRatio", Category.SPATIAL),
            Map.entry("CaveExploration", Category.EXPLORATION),
            Map.entry("Cluster", Category.SPATIAL),
            Map.entry("OreSpaceCorrelation", Category.SPATIAL),
            Map.entry("PathFinding", Category.MOVEMENT),
            Map.entry("Rotation", Category.MOVEMENT),
            Map.entry("SuspiciousTurn", Category.MOVEMENT),
            Map.entry("Movement", Category.MOVEMENT),
            Map.entry("Direction", Category.MOVEMENT),
            Map.entry("Exploration", Category.EXPLORATION),
            Map.entry("Behavior", Category.BEHAVIOR),
            Map.entry("SessionPattern", Category.TEMPORAL),
            Map.entry("HistoricalBehavior", Category.BEHAVIOR),
            Map.entry("MiningEfficiency", Category.STATISTICAL),
            Map.entry("RareOre", Category.STATISTICAL),
            Map.entry("OreTargeting", Category.SPATIAL),
            Map.entry("SystematicMining", Category.BEHAVIOR)
    );

    public record ConsensusResult(boolean hasConsensus, boolean hasDiversity,
                                   int detectorCount, int categoryCount,
                                   double strength, List<String> categories) {}

    public ConsensusResult evaluate(Map<String, Double> detectorScores, int minDetectors) {
        if (detectorScores == null || detectorScores.isEmpty()) {
            return new ConsensusResult(false, false, 0, 0, 0, List.of());
        }

        Map<String, Double> active = new HashMap<>();
        for (var entry : detectorScores.entrySet()) {
            if (entry.getValue() >= 50) active.put(entry.getKey(), entry.getValue());
        }

        if (active.size() < minDetectors) {
            return new ConsensusResult(false, false, active.size(), 0, 0, List.of());
        }

        Set<Category> categories = new HashSet<>();
        for (String name : active.keySet()) {
            Category cat = DETECTOR_CATEGORIES.getOrDefault(name, Category.STATISTICAL);
            categories.add(cat);
        }

        boolean hasDiversity = categories.size() >= 3;
        double avgScore = active.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double strength = avgScore / 100.0;

        List<String> catList = categories.stream().map(Category::name).toList();

        return new ConsensusResult(true, hasDiversity, active.size(), categories.size(), strength, catList);
    }

    public static Category categoryOf(String detectorName) {
        return DETECTOR_CATEGORIES.getOrDefault(detectorName, Category.STATISTICAL);
    }
}
