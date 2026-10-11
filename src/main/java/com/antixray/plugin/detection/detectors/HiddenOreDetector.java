package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.learning.LearningModule.ServerProfile;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;

import java.util.Deque;

public class HiddenOreDetector extends Detector {
    private static final int MIN_EVENTS = 100;

    public HiddenOreDetector() {
        super("HiddenOreDetector", "Detects when a player finds an unusually high ratio of hidden (not exposed to air) ores");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        Deque<MiningEvent> events = data.getRecentEvents();
        if (events.size() < MIN_EVENTS) return null;

        int hiddenOre = 0;
        int exposedOre = 0;
        int totalOre = 0;

        for (MiningEvent event : events) {
            if (!isOre(event.getBlockType())) continue;
            totalOre++;
            if (event.isHiddenOre()) {
                hiddenOre++;
            } else {
                exposedOre++;
            }
        }

        if (totalOre < 3) return null;

        double hiddenRatio = (double) hiddenOre / totalOre;

        ServerProfile profile = AntiXrayPlugin.getServerProfile();
        double expectedHiddenRatio = profile != null ? profile.getAverageHiddenOreRatio() : 0.35;
        if (expectedHiddenRatio <= 0) expectedHiddenRatio = 0.35;
        double deviation = hiddenRatio - expectedHiddenRatio;

        if (deviation > 0.25) {
            double score = Math.min(100, deviation * 150);
            double confidence = Math.min(100, (double) totalOre * 10);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Hidden ore ratio: %.0f%% (%d hidden / %d total ores, expected ~%.0f%%)",
                            hiddenRatio * 100, hiddenOre, totalOre, expectedHiddenRatio * 100))
                    .addEvidence("Hidden: " + hiddenOre + " / Exposed: " + exposedOre)
                    .addEvidence("Hidden ratio: " + String.format("%.2f", hiddenRatio))
                    .addEvidence("Deviation: " + String.format("%.2f", deviation))
                    .build();
        }

        return null;
    }

    private boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }
}
