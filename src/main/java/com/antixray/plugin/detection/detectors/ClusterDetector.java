package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;

import java.util.*;

public class ClusterDetector extends Detector {
    private static final int MIN_EVENTS = 50;
    private static final double CLUSTER_RADIUS = 8.0;

    public ClusterDetector() {
        super("ClusterDetector", "Detects unnatural clustering of rare ore discoveries within small areas");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_EVENTS) return null;

        Deque<MiningEvent> events = data.getRecentEvents();
        Map<String, List<Location>> oreLocations = new HashMap<>();

        for (MiningEvent event : events) {
            if (!isTrackedOre(event.getBlockType())) continue;
            String type = event.getBlockType().name();
            oreLocations.computeIfAbsent(type, k -> new ArrayList<>()).add(event.getLocation());
        }

        int tightClusters = 0;
        int totalClusters = 0;

        for (List<Location> locations : oreLocations.values()) {
            if (locations.size() < 2) continue;
            totalClusters++;

            for (int i = 0; i < locations.size(); i++) {
                for (int j = i + 1; j < locations.size(); j++) {
                    double dist = locations.get(i).distance(locations.get(j));
                    if (dist < CLUSTER_RADIUS) {
                        tightClusters++;
                    }
                }
            }
        }

        if (totalClusters == 0) return null;
        double clusterRatio = (double) tightClusters / totalClusters;

        if (clusterRatio > 2.0) {
            double score = Math.min(100, clusterRatio * 20);
            double confidence = Math.min(100, tightClusters * 15);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Ore clustering: %d tight clusters in %d ore types (%.1f ratio)",
                            tightClusters, totalClusters, clusterRatio))
                    .addEvidence("Tight clusters: " + tightClusters)
                    .addEvidence("Ore types: " + totalClusters)
                    .addEvidence("Cluster ratio: " + String.format("%.1f", clusterRatio))
                    .build();
        }

        return null;
    }

    private boolean isTrackedOre(org.bukkit.Material material) {
        if (!material.name().endsWith("_ORE")) return false;
        String name = material.name();
        return name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("ANCIENT_DEBRIS");
    }
}
