package com.antixray.plugin.engine;

import com.antixray.plugin.feature.MiningFeatures;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;

import java.util.*;

public class SpatialEngine {

    public SpatialSnapshot analyze(PlayerData data, MiningFeatures features) {
        SpatialSnapshot snap = new SpatialSnapshot();
        Deque<MiningEvent> events = data.getRecentEvents();
        List<Location> path = data.getMiningPath();

        snap.explorationEfficiency = features.getExplorationEfficiency();
        snap.uniqueChunksVisited = features.getUniqueChunksVisited();
        snap.primaryDimension = features.getPrimaryDimension();
        snap.depthDistribution = features.getDepthDistribution();
        snap.biomeDistribution = features.getBiomeDistribution();
        snap.clusterDensity = features.getClusterDensity();
        snap.hiddenOreRatio = features.getHiddenOreRatio();
        snap.oreSpaceCorrelation = computeOreSpaceCorrelation(events, path);
        snap.averageYLevel = computeAverageY(events);

        return snap;
    }

    private double computeOreSpaceCorrelation(Deque<MiningEvent> events, List<Location> path) {
        if (path.size() < 5) return 0;
        Set<String> oreLocations = new HashSet<>();
        for (MiningEvent e : events) {
            if (!e.getBlockType().name().endsWith("_ORE")) continue;
            Location l = e.getLocation();
            oreLocations.add(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        }
        int follows = 0;
        for (int i = 1; i < path.size(); i++) {
            Location cur = path.get(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String key = (cur.getBlockX() + dx) + "," + cur.getBlockY() + "," + (cur.getBlockZ() + dz);
                    if (oreLocations.contains(key)) { follows++; break; }
                }
            }
        }
        return path.size() > 0 ? (double) follows / path.size() : 0;
    }

    private double computeAverageY(Deque<MiningEvent> events) {
        if (events.isEmpty()) return 0;
        return events.stream().mapToInt(MiningEvent::getBlockY).average().orElse(0);
    }

    public static class SpatialSnapshot {
        public double explorationEfficiency;
        public int uniqueChunksVisited;
        public String primaryDimension;
        public Map<String, Double> depthDistribution = Map.of();
        public Map<String, Double> biomeDistribution = Map.of();
        public double oreSpaceCorrelation;
        public double clusterDensity;
        public double averageYLevel;
        public double hiddenOreRatio;

        public double getSuspicionScore() {
            double score = 0;
            if (hiddenOreRatio > 0.5) score += hiddenOreRatio * 50;
            if (oreSpaceCorrelation > 0.3) score += oreSpaceCorrelation * 40;
            if (explorationEfficiency > 0.5) score += explorationEfficiency * 30;
            if (clusterDensity > 0.4) score += clusterDensity * 25;
            return Math.min(100, score);
        }
    }
}
