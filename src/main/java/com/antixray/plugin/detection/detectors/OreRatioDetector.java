package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.learning.LearningModule.ServerProfile;
import com.antixray.plugin.model.MiningStatistics;
import com.antixray.plugin.model.PlayerData;

public class OreRatioDetector extends Detector {
    private static final int MIN_EVENTS = 100;

    public OreRatioDetector() {
        super("OreRatioDetector", "Detects abnormal ore-to-block ratios compared to expected statistical averages");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        MiningStatistics stats = data.getStatistics();
        double oreRatio = stats.getOreToBlockRatio();

        if (stats.getLastUpdated() == 0) return null;
        if (data.getRecentEvents().size() < MIN_EVENTS) return null;

        ServerProfile profile = AntiXrayPlugin.getServerProfile();
        double expectedRatio = profile != null ? profile.getAverageOreRate() : 0.05;
        if (expectedRatio <= 0) expectedRatio = 0.05;

        if (oreRatio > expectedRatio * 2) {
            double score = Math.min(100, (oreRatio / expectedRatio) * 30);
            double confidence = Math.min(100, (double) data.getRecentEvents().size() / 5);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Ore ratio: %.3f (expected ~%.3f, %.1fx higher)",
                            oreRatio, expectedRatio, oreRatio / expectedRatio))
                    .addEvidence("Ore/block ratio: " + String.format("%.4f", oreRatio))
                    .addEvidence("Expected: " + String.format("%.4f", expectedRatio))
                    .addEvidence("Multiplier: " + String.format("%.1fx", oreRatio / expectedRatio))
                    .build();
        }

        return null;
    }
}
