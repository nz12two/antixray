package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;

public class MiningEfficiencyDetector extends Detector {
    private static final double MAX_EFFICIENCY_THRESHOLD = 25.0;

    public MiningEfficiencyDetector() {
        super("MiningEfficiencyDetector", "Detects abnormally high mining efficiency (ore blocks found per unique block mined)");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        double efficiency = data.getStatistics().getMiningEfficiency();
        if (efficiency <= 0) return null;
        if (data.getRecentEvents().size() < 100) return null;

        if (efficiency > MAX_EFFICIENCY_THRESHOLD) {
            double score = Math.min(100, (efficiency / MAX_EFFICIENCY_THRESHOLD) * 50);
            double confidence = Math.min(100, efficiency * 3);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Mining efficiency: %.1f%% (threshold: %.0f%%)",
                            efficiency, MAX_EFFICIENCY_THRESHOLD))
                    .addEvidence("Efficiency: " + String.format("%.1f%%", efficiency))
                    .addEvidence("Threshold: " + String.format("%.0f%%", MAX_EFFICIENCY_THRESHOLD))
                    .build();
        }

        return null;
    }
}
