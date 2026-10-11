package com.antixray.plugin.statistics.calculators;

import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.MiningStatistics;

import java.util.Deque;

public class RatioCalculator {

    public static void calculate(Deque<MiningEvent> events, MiningStatistics stats) {
        int totalBlocks = events.size();
        int oreBlocks = 0;
        int hiddenOre = 0;
        int exposedOre = 0;

        for (MiningEvent event : events) {
            if (isOre(event.getBlockType())) {
                oreBlocks++;
                if (event.isHiddenOre()) {
                    hiddenOre++;
                } else {
                    exposedOre++;
                }
            }
        }

        if (totalBlocks > 0) {
            stats.setOreToBlockRatio((double) oreBlocks / totalBlocks);
        }

        if (exposedOre > 0) {
            stats.setHiddenToExposedRatio((double) hiddenOre / exposedOre);
        }
    }

    private static boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }
}
