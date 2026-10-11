package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;

import java.util.Deque;

public class LuckDetector extends Detector {
    private static final double DIAMOND_SPAWN_RATE = 0.0009;
    private static final int MIN_EVENTS = 100;

    public LuckDetector() {
        super("LuckDetector", "Detects abnormal luck by comparing found rare ores against expected probabilities");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        Deque<MiningEvent> events = data.getRecentEvents();
        if (events.size() < MIN_EVENTS) return null;

        int totalOres = 0;
        int diamonds = 0;
        int ancientDebris = 0;
        int eligibleBlocks = 0;

        for (MiningEvent event : events) {
            if (isOreReplaceable(event.getBlockType())) {
                eligibleBlocks++;
            }
            if (isOre(event.getBlockType())) {
                totalOres++;
                String name = event.getBlockType().name();
                if (name.contains("DIAMOND")) diamonds++;
                if (name.contains("ANCIENT_DEBRIS")) ancientDebris++;
            }
        }

        if (totalOres == 0) return null;

        int denominator = Math.max(eligibleBlocks, 1);
        double expectedDiamonds = denominator * DIAMOND_SPAWN_RATE;
        double diamondRatio = expectedDiamonds > 0 ? diamonds / expectedDiamonds : 0;

        double score = 0;
        double confidence = 0;
        StringBuilder reason = new StringBuilder();

        if (diamondRatio > 3.0 && diamonds >= 3) {
            score = Math.min(100, diamondRatio * 15);
            confidence = Math.min(100, (double) diamonds / totalOres * 200);
            reason.append("Found ").append(diamonds).append(" diamonds (expected ").append(String.format("%.1f", expectedDiamonds))
                    .append("), ratio: ").append(String.format("%.1fx", diamondRatio));
        }

        if (ancientDebris > 0 && events.size() < 200) {
            score = Math.max(score, 60);
            confidence = Math.max(confidence, 50);
            if (reason.length() > 0) reason.append(" | ");
            reason.append("Ancient debris found early: ").append(ancientDebris);
        }

        if (score > 0 && confidence > 0) {
            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(reason.toString())
                    .addEvidence("Diamonds: " + diamonds + " (expected: " + String.format("%.1f", expectedDiamonds) + ")")
                    .addEvidence("Total ores: " + totalOres + " / Eligible blocks: " + eligibleBlocks)
                    .addEvidence("Diamond ratio: " + String.format("%.1fx", diamondRatio))
                    .build();
        }

        return null;
    }

    private boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }

    private boolean isOreReplaceable(org.bukkit.Material material) {
        String name = material.name();
        return name.equals("STONE") || name.equals("DEEPSLATE") ||
               name.equals("GRANITE") || name.equals("DIORITE") ||
               name.equals("ANDESITE") || name.equals("TUFF") ||
               name.equals("NETHERRACK") || name.equals("BLACKSTONE") ||
               name.equals("BASALT");
    }
}
