package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.learning.LearningModule.ServerProfile;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;

import java.util.Deque;

public class CaveExplorationDetector extends Detector {
    private static final int MIN_EVENTS = 100;

    public CaveExplorationDetector() {
        super("CaveExplorationDetector", "Detects players who find rare ores in caves at an abnormally high rate");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        Deque<MiningEvent> events = data.getRecentEvents();
        if (events.size() < MIN_EVENTS) return null;

        int caveEvents = 0;
        int caveOres = 0;
        int nonCaveOres = 0;
        int nonCaveEvents = 0;

        for (MiningEvent event : events) {
            boolean inCave = event.getConditions().contains(MiningEvent.MiningCondition.CAVE);
            if (inCave) {
                caveEvents++;
                if (isOre(event.getBlockType())) caveOres++;
            } else {
                nonCaveEvents++;
                if (isOre(event.getBlockType())) nonCaveOres++;
            }
        }

        if (caveEvents < 10 || nonCaveEvents < 10) return null;

        double caveOreRate = (double) caveOres / caveEvents;
        double nonCaveOreRate = (double) nonCaveOres / nonCaveEvents;

        ServerProfile profile = AntiXrayPlugin.getServerProfile();
        double expectedOreRate = profile != null ? profile.getAverageOreRate() : 0.05;
        if (expectedOreRate <= 0) expectedOreRate = 0.05;

        double rateThreshold = Math.max(nonCaveOreRate * 3, expectedOreRate * 4);

        if (caveOreRate > rateThreshold && caveOreRate > 0.10) {
            double score = Math.min(100, (caveOreRate / Math.max(0.01, nonCaveOreRate)) * 20);
            double confidence = Math.min(100, (double) caveEvents * 3);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Cave ore rate %.0f%% vs non-cave %.0f%% (%.1fx higher, server avg %.2f)",
                            caveOreRate * 100, nonCaveOreRate * 100, caveOreRate / Math.max(0.001, nonCaveOreRate), expectedOreRate))
                    .addEvidence("Cave ores: " + caveOres + "/" + caveEvents + " events")
                    .addEvidence("Non-cave ores: " + nonCaveOres + "/" + nonCaveEvents + " events")
                    .addEvidence("Server avg ore rate: " + String.format("%.3f", expectedOreRate))
                    .build();
        }

        return null;
    }

    private boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }
}
