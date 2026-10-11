package com.antixray.plugin.statistics.calculators;

import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.MiningStatistics;

import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class EfficiencyCalculator {

    public static double calculate(Deque<MiningEvent> events, MiningStatistics stats) {
        if (events.size() < 2) return 0;

        Set<String> uniqueLocations = new HashSet<>();
        int totalOre = 0;

        for (MiningEvent event : events) {
            String key = event.getLocation().getBlockX() + ","
                    + event.getLocation().getBlockY() + ","
                    + event.getLocation().getBlockZ();
            uniqueLocations.add(key);

            if (isOre(event.getBlockType())) {
                totalOre++;
            }
        }

        double efficiency = 0;
        if (uniqueLocations.size() > 0) {
            efficiency = (double) totalOre / uniqueLocations.size() * 100;
        }

        stats.setMiningEfficiency(efficiency);
        return efficiency;
    }

    private static boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }
}
