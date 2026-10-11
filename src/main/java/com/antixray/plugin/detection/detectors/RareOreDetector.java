package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;

import java.util.Deque;

public class RareOreDetector extends Detector {
    private static final int MIN_EVENTS = 100;

    public RareOreDetector() {
        super("RareOreDetector", "Detects when a player finds rare ores (diamond, emerald, ancient debris) at an abnormal rate");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        Deque<MiningEvent> events = data.getRecentEvents();
        if (events.size() < MIN_EVENTS) return null;

        int totalRareOres = 0;
        int totalOres = 0;

        for (MiningEvent event : events) {
            if (!isOre(event.getBlockType())) continue;
            totalOres++;
            String name = event.getBlockType().name();
            if (name.contains("DIAMOND") || name.contains("EMERALD")
                    || name.contains("ANCIENT_DEBRIS")) {
                totalRareOres++;
            }
        }

        if (totalOres < 3) return null;

        double rareRatio = (double) totalRareOres / totalOres;
        double expectedRareRatio = 0.15;

        if (rareRatio > expectedRareRatio && totalRareOres >= 2) {
            double score = Math.min(100, (rareRatio / expectedRareRatio) * 25);
            double confidence = Math.min(100, (double) totalRareOres * 20);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Rare ore ratio: %.0f%% (%d/%d ores are rare)",
                            rareRatio * 100, totalRareOres, totalOres))
                    .addEvidence("Rare ores: " + totalRareOres + "/" + totalOres)
                    .addEvidence("Rare ratio: " + String.format("%.2f", rareRatio))
                    .addEvidence("Expected: " + String.format("%.2f", expectedRareRatio))
                    .build();
        }

        return null;
    }

    private boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }
}
