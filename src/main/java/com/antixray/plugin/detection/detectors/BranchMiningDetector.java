package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

public class BranchMiningDetector extends Detector {
    private static final int MIN_PATH = 50;
    private static final double BRANCH_ANGLE_MIN = 45;
    private static final double BRANCH_ANGLE_MAX = 135;

    public BranchMiningDetector() {
        super("BranchMiningDetector", "Detects characteristic branch-mining patterns (strip mining)");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_PATH) return null;

        int branches = 0;
        int totalChanges = 0;
        Vector lastDir = null;
        Location last = null;

        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            Vector dir = loc.toVector().subtract(last.toVector());
            double len = dir.length();
            if (len < 0.3) continue;
            totalChanges++;
            dir.normalize();
            if (lastDir != null) {
                double dot = lastDir.dot(dir);
                double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
                if (angle > BRANCH_ANGLE_MIN && angle < BRANCH_ANGLE_MAX) {
                    branches++;
                }
            }
            lastDir = dir;
            last = loc;
        }

        if (totalChanges < 5) return null;
        double branchRatio = (double) branches / totalChanges;

        if (branchRatio > 0.30) {
            double score = Math.min(100, branchRatio * 120);
            double confidence = Math.min(100, (double) totalChanges);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Branch mining pattern: %.0f%% branch turns (%d/%d changes)",
                            branchRatio * 100, branches, totalChanges))
                    .addEvidence("Branch ratio: " + String.format("%.2f", branchRatio))
                    .addEvidence("Branches: " + branches + " / Total changes: " + totalChanges)
                    .build();
        }

        return null;
    }
}
