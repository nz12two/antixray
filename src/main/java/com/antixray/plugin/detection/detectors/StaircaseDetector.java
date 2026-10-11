package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;

import java.util.List;

/**
 * Detects classic 45° staircase mining patterns used by x-ray users.
 * <p>
 * X-ray users often mine in diagonal staircases (alternating forward + down/up)
 * to efficiently expose ores at multiple Y-levels. This detector analyzes the
 * mining path for consecutive segments with ~45° vertical angles.
 */
public class StaircaseDetector extends Detector {
    private static final int MIN_PATH = 20;
    private static final double STAIRCASE_ANGLE_MIN = 30;
    private static final double STAIRCASE_ANGLE_MAX = 60;
    private static final double MIN_STAIRCASE_RATIO = 0.65;
    private static final int MIN_STAIRCASE_SEGMENTS = 5;

    public StaircaseDetector() {
        super("StaircaseDetector", "Detects 45° staircase mining patterns");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_PATH) return null;

        int staircaseSegments = 0;
        int totalSegments = 0;
        Location last = null;
        double totalAngle = 0;

        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            double dx = loc.getX() - last.getX();
            double dy = loc.getY() - last.getY();
            double dz = loc.getZ() - last.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            if (horizontal < 0.1) continue; // vertical-only move
            totalSegments++;

            double angle = Math.toDegrees(Math.atan(Math.abs(dy) / horizontal));
            totalAngle += angle;

            if (angle >= STAIRCASE_ANGLE_MIN && angle <= STAIRCASE_ANGLE_MAX) {
                staircaseSegments++;
            }

            last = loc;
        }

        if (totalSegments < 5) return null;

        double avgAngle = totalAngle / totalSegments;
        double staircaseRatio = (double) staircaseSegments / totalSegments;

        if (staircaseSegments >= MIN_STAIRCASE_SEGMENTS && staircaseRatio >= MIN_STAIRCASE_RATIO) {
            double score = Math.min(100, staircaseRatio * 85 + 10);
            double confidence = Math.min(100, (double) staircaseSegments * 4);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Staircase mining: %.0f%% diagonal segments (%d/%d), avg angle %.1f°",
                            staircaseRatio * 100, staircaseSegments, totalSegments, avgAngle))
                    .addEvidence("Staircase ratio: " + String.format("%.2f", staircaseRatio))
                    .addEvidence("Avg angle: " + String.format("%.1f°", avgAngle))
                    .addEvidence("Segments: " + staircaseSegments + "/" + totalSegments)
                    .build();
        }

        return null;
    }
}
