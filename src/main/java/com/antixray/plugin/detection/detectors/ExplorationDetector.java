package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import org.bukkit.Location;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExplorationDetector extends Detector {
    private static final int MIN_EXPLORATION_EVENTS = 50;

    public ExplorationDetector() {
        super("ExplorationDetector", "Detects if a player explores chunks in a pattern consistent with X-Ray (efficient chunk scanning)");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        List<Location> path = data.getMiningPath();
        if (path.size() < MIN_EXPLORATION_EVENTS) return null;

        Set<String> uniqueChunks = new HashSet<>();
        int totalChunksVisited = 0;

        for (Location loc : path) {
            String chunkKey = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
            if (!uniqueChunks.contains(chunkKey)) {
                uniqueChunks.add(chunkKey);
                totalChunksVisited++;
            }
        }

        Deque<MiningEvent> events = data.getRecentEvents();
        Map<String, Integer> eventChunks = new HashMap<>();
        for (MiningEvent event : events) {
            if (!isTrackedOre(event.getBlockType())) continue;
            Location loc = event.getLocation();
            String chunkKey = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
            eventChunks.merge(chunkKey, 1, Integer::sum);
        }

        int chunksWithOre = eventChunks.size();
        if (totalChunksVisited < 3 || chunksWithOre == 0) return null;

        double oreChunkRatio = (double) chunksWithOre / totalChunksVisited;

        if (oreChunkRatio > 0.50) {
            double score = Math.min(100, oreChunkRatio * 120);
            double confidence = Math.min(100, (double) chunksWithOre * 20);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Efficient exploration: found ores in %.0f%% of visited chunks (%d/%d)",
                            oreChunkRatio * 100, chunksWithOre, totalChunksVisited))
                    .addEvidence("Chunks with ores: " + chunksWithOre + "/" + totalChunksVisited)
                    .addEvidence("Ore/chunk ratio: " + String.format("%.2f", oreChunkRatio))
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
