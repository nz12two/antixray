package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;

import java.util.List;

public class PathFindingDetector extends Detector {
    private static final int MIN_PATH = 30;

    public PathFindingDetector() {
        super("PathFindingDetector", "Detects A* or bot-like pathfinding behavior in mining patterns");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_PATH) return null;

        int optimalMoves = 0;
        int totalMoves = 0;
        double totalAngleChange = 0;
        int angleChanges = 0;
        Location last = null;
        double lastSegmentAngle = Double.NaN;

        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            double dist = last.distance(loc);
            totalMoves++;

            if (dist > 0.5 && dist < 2.5) {
                optimalMoves++;
            }

            // Compute direction angle of this segment using atan2
            if (dist > 0.01) {
                double segmentAngle = Math.toDegrees(Math.atan2(
                    loc.getZ() - last.getZ(),
                    loc.getX() - last.getX()
                ));

                if (!Double.isNaN(lastSegmentAngle)) {
                    double angleDiff = Math.abs(segmentAngle - lastSegmentAngle);
                    // Normalize to [0, 180]
                    if (angleDiff > 180) angleDiff = 360 - angleDiff;
                    totalAngleChange += angleDiff;
                    angleChanges++;
                }
                lastSegmentAngle = segmentAngle;
            }

            last = loc;
        }

        if (totalMoves < 5) return null;
        double optimalRatio = (double) optimalMoves / totalMoves;
        double avgAngleChange = angleChanges > 0 ? totalAngleChange / angleChanges : 0;

        if (optimalRatio > 0.90 && avgAngleChange < 15) {
            double score = Math.min(100, optimalRatio * 95);
            double confidence = Math.min(100, (double) totalMoves * 4);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Pathfinding pattern: %.0f%% optimal moves, avg angle change %.1f°",
                            optimalRatio * 100, avgAngleChange))
                    .addEvidence("Optimal ratio: " + String.format("%.2f", optimalRatio))
                    .addEvidence("Avg angle: " + String.format("%.1f", avgAngleChange) + "°")
                    .build();
        }

        return null;
    }
}
