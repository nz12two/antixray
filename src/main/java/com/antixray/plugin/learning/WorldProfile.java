package com.antixray.plugin.learning;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldProfile {
    private final Map<String, WorldStat> worlds = new ConcurrentHashMap<>();
    private static final double ALPHA = 0.05;

    public void recordBlockBreak(String worldName, long timestamp) {
        worlds.computeIfAbsent(worldName, WorldStat::new).recordBlock(timestamp);
    }

    public void recordOreDiscovery(String worldName, boolean hidden, long timestamp) {
        worlds.computeIfAbsent(worldName, WorldStat::new).recordOre(hidden, timestamp);
    }

    public double getAverageBlocksPerMinute(String worldName) {
        WorldStat ws = worlds.get(worldName);
        return ws != null ? ws.averageBlocksPerMinute : 0;
    }

    public double getAverageOreRate(String worldName) {
        WorldStat ws = worlds.get(worldName);
        return ws != null ? ws.averageOreRate : 0;
    }

    public double getAverageHiddenRatio(String worldName) {
        WorldStat ws = worlds.get(worldName);
        return ws != null ? ws.averageHiddenRatio : 0;
    }

    public long getTotalBlocks(String worldName) {
        WorldStat ws = worlds.get(worldName);
        return ws != null ? ws.totalBlocks : 0;
    }

    public boolean hasData(String worldName) {
        WorldStat ws = worlds.get(worldName);
        return ws != null && ws.totalBlocks > 100;
    }

    public void updateAverages() {
        for (WorldStat ws : worlds.values()) ws.recalculate();
    }

    private static class WorldStat {
        long totalBlocks;
        long totalOres;
        long hiddenOres;
        long sessionStartTime;
        double averageBlocksPerMinute;
        double averageOreRate;
        double averageHiddenRatio;

        WorldStat(String worldName) {
            this.sessionStartTime = System.currentTimeMillis();
        }

        void recordBlock(long timestamp) { totalBlocks++; }
        void recordOre(boolean hidden, long timestamp) {
            totalOres++;
            if (hidden) hiddenOres++;
        }

        void recalculate() {
            double minutes = Math.max(1, (System.currentTimeMillis() - sessionStartTime) / 60_000.0);
            double currentBpm = totalBlocks / minutes;
            double currentOreRate = totalBlocks > 0 ? (double) totalOres / totalBlocks : 0;
            double currentHiddenRatio = totalOres > 0 ? (double) hiddenOres / totalOres : 0;

            averageBlocksPerMinute = averageBlocksPerMinute == 0
                    ? currentBpm
                    : averageBlocksPerMinute * (1 - ALPHA) + currentBpm * ALPHA;
            averageOreRate = averageOreRate == 0
                    ? currentOreRate
                    : averageOreRate * (1 - ALPHA) + currentOreRate * ALPHA;
            averageHiddenRatio = averageHiddenRatio == 0
                    ? currentHiddenRatio
                    : averageHiddenRatio * (1 - ALPHA) + currentHiddenRatio * ALPHA;
        }
    }
}
