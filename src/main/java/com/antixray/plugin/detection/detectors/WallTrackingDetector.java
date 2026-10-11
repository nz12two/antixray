package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

public class WallTrackingDetector extends Detector {
    private static final int MIN_EVENTS = 50;
    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public WallTrackingDetector() {
        super("WallTrackingDetector", "Detects players who follow along specific walls/veins when mining");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_EVENTS) return null;

        int wallFollowCount = 0;
        int totalChecks = 0;
        int sameOreStreak = 0;

        for (int i = 1; i < path.size(); i++) {
            Location current = path.get(i);
            Location prev = path.get(i - 1);
            totalChecks++;

            if (current.getBlock().getType() == prev.getBlock().getType()) {
                sameOreStreak++;
                if (sameOreStreak > 3) {
                    wallFollowCount++;
                }
            } else {
                sameOreStreak = 0;
            }

            boolean adjacentToOre = false;
            for (BlockFace face : FACES) {
                Block adj = current.getBlock().getRelative(face);
                if (isTrackedOre(adj.getType())) {
                    adjacentToOre = true;
                    break;
                }
            }
            if (adjacentToOre) {
                wallFollowCount++;
            }
        }

        if (totalChecks == 0) return null;
        double wallRatio = (double) wallFollowCount / totalChecks;

        if (wallRatio > 0.60) {
            double score = Math.min(100, wallRatio * 100);
            double confidence = Math.min(100, (double) totalChecks);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Wall/vein tracking: %.0f%% of blocks follow ore boundaries (%d/%d)",
                            wallRatio * 100, wallFollowCount, totalChecks))
                    .addEvidence("Wall follow ratio: " + String.format("%.2f", wallRatio))
                    .addEvidence("Follow count: " + wallFollowCount + " / Total: " + totalChecks)
                    .build();
        }

        return null;
    }

    private boolean isTrackedOre(Material material) {
        if (!material.name().endsWith("_ORE")) return false;
        String name = material.name();
        return name.contains("DIAMOND") || name.contains("EMERALD")
                || name.contains("ANCIENT_DEBRIS") || name.contains("NETHERITE");
    }
}
