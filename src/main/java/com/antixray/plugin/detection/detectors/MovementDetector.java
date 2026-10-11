package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerData.MovementData;

import java.util.Deque;

public class MovementDetector extends Detector {
    private static final int MIN_EVENTS = 20;

    public MovementDetector() {
        super("MovementDetector", "Detects movement patterns inconsistent with normal mining behavior");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        MovementData movement = data.getMovement();
        Deque<MiningEvent> events = data.getRecentEvents();
        if (events.size() < MIN_EVENTS) return null;

        long miningDuration = System.currentTimeMillis() - data.getLastMiningTime();
        if (miningDuration > 120_000) return null;

        double totalDistance = movement.getTotalDistance();
        double speed = movement.getSpeed();

        double score = 0;

        if (totalDistance < 2.0 && events.size() > 20) {
            score += Math.min(40, (20 - totalDistance) * 2);
        }

        if (speed > 5.0) {
            score += Math.min(30, speed * 5);
        }

        if (score > 25) {
            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(Math.min(100, events.size() * 3))
                    .reason(String.format("Suspicious movement: distance=%.1f, speed=%.1f", totalDistance, speed))
                    .addEvidence("Total distance: " + String.format("%.1f", totalDistance))
                    .addEvidence("Speed: " + String.format("%.1f", speed))
                    .build();
        }

        return null;
    }
}
