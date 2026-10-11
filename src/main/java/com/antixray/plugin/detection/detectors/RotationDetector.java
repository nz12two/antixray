package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerData.MovementData;

public class RotationDetector extends Detector {

    public RotationDetector() {
        super("RotationDetector", "Detects unnatural head rotation patterns during mining");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        MovementData movement = data.getMovement();
        double yawRate = movement.getYawChangeRate();
        double pitchRate = movement.getPitchChangeRate();

        if (yawRate < 1 && pitchRate < 1) return null;

        double suspicionScore = 0;

        if (yawRate > 20) {
            suspicionScore += Math.min(50, yawRate * 2);
        }

        if (pitchRate > 10) {
            suspicionScore += Math.min(50, pitchRate * 3);
        }

        if (suspicionScore > 30) {
            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(suspicionScore)
                    .confidence(Math.min(100, suspicionScore * 1.5))
                    .reason(String.format("Unusual rotation: yaw changes=%.0f, pitch changes=%.0f", yawRate, pitchRate))
                    .addEvidence("Yaw change rate: " + String.format("%.1f", yawRate))
                    .addEvidence("Pitch change rate: " + String.format("%.1f", pitchRate))
                    .build();
        }

        return null;
    }
}
