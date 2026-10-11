package com.antixray.plugin.statistics.calculators;

import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.MiningStatistics;

import java.util.Deque;

public class DistributionAnalyzer {

    public static void analyze(Deque<MiningEvent> events, MiningStatistics stats) {
        stats.getDepthDistribution().clear();
        stats.getBiomeDistribution().clear();
        stats.getDimensionDistribution().clear();

        for (MiningEvent event : events) {
            int y = event.getBlockY();
            String depthRange = getDepthRange(y);
            stats.getDepthDistribution().merge(depthRange, 1, Integer::sum);

            String biomeName = event.getBiome().getKey().toString();
            stats.getBiomeDistribution().merge(biomeName, 1, Integer::sum);

            String dimName = event.getDimension().name();
            stats.getDimensionDistribution().merge(dimName, 1, Integer::sum);
        }
    }

    private static String getDepthRange(int y) {
        if (y < 0) return "deepslate";
        if (y < 16) return "deep";
        if (y < 32) return "low";
        if (y < 64) return "mid";
        return "surface";
    }
}
