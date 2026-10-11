package com.antixray.plugin.statistics.calculators;

import com.antixray.plugin.model.MiningStatistics;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

public class TunnelAnalyzer {

    public static void analyze(List<Location> path, MiningStatistics stats) {
        if (path.size() < 2) return;

        int branches = 0;
        int straightSegments = 0;
        int totalSegments = 0;
        double totalLength = 0;
        Vector lastDirection = null;
        Location lastLoc = null;

        for (int i = 0; i < path.size(); i++) {
            Location current = path.get(i);
            if (lastLoc == null) { lastLoc = current; continue; }

            Vector dir = current.toVector().subtract(lastLoc.toVector());
            double len = dir.length();
            if (len < 0.5) continue;

            totalLength += len;
            totalSegments++;
            dir.normalize();

            if (lastDirection != null) {
                double dot = lastDirection.dot(dir);
                double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));

                if (angle < 15) {
                    straightSegments++;
                } else if (angle > 45 && angle < 135) {
                    branches++;
                }
            }

            lastDirection = dir;
            lastLoc = current;
        }

        if (totalSegments > 0) {
            stats.setAverageTunnelLength(totalLength / totalSegments);
            stats.setStraightTunnelPercentage((double) straightSegments / totalSegments * 100);
            stats.setBranchMiningPercentage((double) branches / totalSegments * 100);
        }
    }
}
