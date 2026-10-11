package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerData.MovementData;

public class SuspiciousTurnDetector extends Detector {
    private static final int MIN_TURNS = 5;

    public SuspiciousTurnDetector() {
        super("SuspiciousTurnDetector", "Detects players making suspicious rapid turns, often indicative of X-Ray toggling");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        MovementData movement = data.getMovement();
        int suspiciousTurns = movement.getSuspiciousTurns();

        if (suspiciousTurns < MIN_TURNS) return null;

        double score = Math.min(100, suspiciousTurns * 12);
        double confidence = Math.min(100, suspiciousTurns * 15);

        return new DetectionResult.Builder()
                .detectorName(getName())
                .score(score)
                .confidence(confidence)
                .reason(String.format("Suspicious turns: %d rapid head movements detected", suspiciousTurns))
                .addEvidence("Suspicious turns: " + suspiciousTurns)
                .build();
    }
}
