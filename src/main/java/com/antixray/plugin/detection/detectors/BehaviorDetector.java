package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;

import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class BehaviorDetector extends Detector {
    private static final int MIN_EVENTS = 50;

    public BehaviorDetector() {
        super("BehaviorDetector", "Detects abnormal behavioral patterns during mining sessions");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        Deque<MiningEvent> events = data.getRecentEvents();
        if (events.size() < MIN_EVENTS) return null;

        int consecutiveOres = 0;
        int maxConsecutiveOres = 0;
        int oreCount = 0;
        int totalEvents = events.size();

        Set<String> oreTypes = new HashSet<>();

        for (MiningEvent event : events) {
            if (isOre(event.getBlockType())) {
                consecutiveOres++;
                maxConsecutiveOres = Math.max(maxConsecutiveOres, consecutiveOres);
                oreCount++;
                oreTypes.add(event.getBlockType().name());
            } else {
                consecutiveOres = 0;
            }
        }

        if (oreCount < 3) return null;

        double oreConcentration = (double) oreCount / totalEvents;
        double score = 0;

        if (maxConsecutiveOres >= 4) {
            score += Math.min(50, maxConsecutiveOres * 12);
        }
        if (oreConcentration > 0.30) {
            score += Math.min(50, oreConcentration * 100);
        }

        if (score > 30) {
            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(Math.min(100, oreCount * 12))
                    .reason(String.format("Suspicious behavior: %d ores in %d events (%.0f%%), max streak=%d",
                            oreCount, totalEvents, oreConcentration * 100, maxConsecutiveOres))
                    .addEvidence("Ore concentration: " + String.format("%.2f", oreConcentration))
                    .addEvidence("Max consecutive ores: " + maxConsecutiveOres)
                    .addEvidence("Ore types: " + String.join(", ", oreTypes))
                    .build();
        }

        return null;
    }

    private boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }
}
