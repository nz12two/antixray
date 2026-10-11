package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;

public class HistoricalBehaviorDetector extends Detector {
    private static final int MIN_FLAGS = 3;

    public HistoricalBehaviorDetector() {
        super("HistoricalBehaviorDetector", "Evaluates risk based on the player's historical flag and punishment record");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        PlayerData.RiskProfile profile = data.getRiskProfile();
        int totalFlags = profile.getTotalFlags();
        double peakScore = profile.getPeakRiskScore();

        if (totalFlags < MIN_FLAGS) return null;

        long timeSinceLastFlag = System.currentTimeMillis() - profile.getLastFlagTime();
        long hoursSinceLastFlag = timeSinceLastFlag / 3_600_000;

        double recencyFactor = Math.max(0, 1 - (hoursSinceLastFlag / 72.0));

        double score = Math.min(100, (totalFlags * 15) + (peakScore * 0.3) * recencyFactor);
        double confidence = Math.min(100, totalFlags * 20);

        if (score > 20) {
            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Historical flags: %d total flags, peak risk %.0f, %.0fh since last flag",
                            totalFlags, peakScore, (double) hoursSinceLastFlag))
                    .addEvidence("Total flags: " + totalFlags)
                    .addEvidence("Peak risk score: " + String.format("%.0f", peakScore))
                    .addEvidence("Hours since last flag: " + String.format("%.0f", hoursSinceLastFlag))
                    .build();
        }

        return null;
    }
}
