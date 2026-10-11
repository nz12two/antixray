package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

public class TunnelDetector extends Detector {
    private static final int MIN_PATH_LENGTH = 30;
    private static final double STRAIGHT_THRESHOLD = 0.98;

    public TunnelDetector() {
        super("TunnelDetector", "Detects unnatural straight-line tunneling patterns");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_PATH_LENGTH) return null;

        int straightSegments = 0;
        int totalSegments = 0;
        Vector lastDirection = null;
        Location last = null;

        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            Vector dir = loc.toVector().subtract(last.toVector());
            double len = dir.length();
            if (len < 0.3) continue;
            totalSegments++;
            dir.normalize();
            if (lastDirection != null) {
                double dot = lastDirection.dot(dir);
                if (dot > STRAIGHT_THRESHOLD) straightSegments++;
            }
            lastDirection = dir;
            last = loc;
        }

        if (totalSegments < 5) return null;
        double straightRatio = (double) straightSegments / totalSegments;

        if (straightRatio > 0.85) {
            double score = Math.min(100, straightRatio * 95);
            double confidence = Math.min(100, (double) totalSegments / 2);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Straight tunnel mining: %.0f%% straight segments (%d/%d)",
                            straightRatio * 100, straightSegments, totalSegments))
                    .addEvidence("Straight ratio: " + String.format("%.2f", straightRatio))
                    .addEvidence("Total segments: " + totalSegments)
                    .build();
        }

        return null;
    }
}
