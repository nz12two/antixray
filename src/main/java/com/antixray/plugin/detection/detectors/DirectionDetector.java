package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

public class DirectionDetector extends Detector {
    private static final int MIN_PATH = 30;

    public DirectionDetector() {
        super("DirectionDetector", "Detects mining patterns that consistently head towards rare ores");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_PATH) return null;

        int totalDirectionChecks = 0;
        Vector mainDirection = null;
        Location last = null;
        int directionChanges = 0;

        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            Vector dir = loc.toVector().subtract(last.toVector());
            double len = dir.length();
            if (len < 0.5) continue;
            totalDirectionChecks++;
            if (mainDirection == null) {
                mainDirection = dir.normalize();
            } else {
                dir.normalize();
                double dot = mainDirection.dot(dir);
                if (dot < 0.3) {
                    directionChanges++;
                }
            }
            last = loc;
        }

        if (totalDirectionChecks < 5) return null;

        double changeRate = (double) directionChanges / totalDirectionChecks;

        if (changeRate < 0.10 && totalDirectionChecks > 10) {
            double score = Math.min(100, (1 - changeRate) * 70);
            double confidence = Math.min(100, (double) totalDirectionChecks * 5);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Highly directional mining: only %d changes in %d segments (%.0f%%)",
                            directionChanges, totalDirectionChecks, changeRate * 100))
                    .addEvidence("Direction change rate: " + String.format("%.2f", changeRate))
                    .addEvidence("Total segments: " + totalDirectionChecks)
                    .build();
        }

        return null;
    }
}
