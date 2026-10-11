package com.antixray.plugin.feature;

import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerData.MovementData;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;

public class FeatureExtractor {

    public MiningFeatures extract(PlayerData data) {
        Deque<MiningEvent> events = data.getRecentEvents();
        List<Location> path = data.getMiningPath();
        MovementData movement = data.getMovement();

        MiningFeatures.Builder builder = new MiningFeatures.Builder();

        builder.totalEvents(events.size());
        builder.sessionDurationMinutes(data.getCurrentSession().getTotalPlayTime() / 60_000.0);
        buildBlockRates(data, builder);
        buildTurnAngles(path, builder);
        buildHiddenOreRatio(events, builder);
        buildStraightness(path, builder);
        buildCaveRatio(events, builder);
        buildOreDensity(events, builder);
        buildMovementFeatures(movement, builder);
        buildBranchRatio(path, builder);
        buildWallFollowRatio(events, path, builder);
        buildDirectionalConsistency(path, builder);
        buildRareOreRatio(events, builder);
        buildClusterDensity(events, builder);
        buildExplorationEfficiency(events, path, builder);
        buildTimeBetweenOres(events, builder);
        buildDistributions(events, builder);

        return builder.build();
    }

    private void buildBlockRates(PlayerData data, MiningFeatures.Builder b) {
        double minutes = Math.max(0.1, data.getCurrentSession().getTotalPlayTime() / 60_000.0);
        b.blocksPerMinute(data.getTotalBlocksMined() / minutes);
        b.oresPerMinute(data.getTotalOresFound() / minutes);
    }

    private void buildTurnAngles(List<Location> path, MiningFeatures.Builder b) {
        if (path.size() < 3) return;
        double totalAngle = 0;
        int count = 0;
        Vector lastDir = null;
        Location last = null;
        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            Vector dir = loc.toVector().subtract(last.toVector());
            double len = dir.length();
            if (len < 0.3) continue;
            dir.normalize();
            if (lastDir != null) {
                double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, lastDir.dot(dir)))));
                totalAngle += angle;
                count++;
            }
            lastDir = dir;
            last = loc;
        }
        if (count > 0) b.averageTurnAngle(totalAngle / count);
    }

    private void buildHiddenOreRatio(Deque<MiningEvent> events, MiningFeatures.Builder b) {
        int totalOre = 0, hidden = 0;
        for (MiningEvent e : events) {
            if (!isOre(e.getBlockType())) continue;
            totalOre++;
            if (e.isHiddenOre()) hidden++;
        }
        if (totalOre > 0) b.hiddenOreRatio((double) hidden / totalOre);
    }

    private void buildStraightness(List<Location> path, MiningFeatures.Builder b) {
        if (path.size() < 5) return;
        int straight = 0, total = 0;
        Vector lastDir = null;
        Location last = null;
        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            Vector dir = loc.toVector().subtract(last.toVector());
            if (dir.length() < 0.3) continue;
            total++;
            dir.normalize();
            if (lastDir != null && lastDir.dot(dir) > 0.98) straight++;
            lastDir = dir;
            last = loc;
        }
        if (total > 0) b.tunnelStraightness((double) straight / total);
    }

    private void buildCaveRatio(Deque<MiningEvent> events, MiningFeatures.Builder b) {
        int cave = 0, total = 0;
        for (MiningEvent e : events) {
            total++;
            if (e.getConditions().contains(MiningEvent.MiningCondition.CAVE)) cave++;
        }
        if (total > 0) b.caveExplorationRatio((double) cave / total);
    }

    private void buildOreDensity(Deque<MiningEvent> events, MiningFeatures.Builder b) {
        int ore = 0, total = 0;
        for (MiningEvent e : events) {
            total++;
            if (isOre(e.getBlockType())) ore++;
        }
        if (total > 0) b.oreDensity((double) ore / total);
    }

    private void buildMovementFeatures(MovementData movement, MiningFeatures.Builder b) {
        b.movementEntropy(1.0 / (1.0 + Math.abs(movement.getYawChangeRate() - 15)));
        b.averageYawChange(movement.getYawChangeRate());
        b.averagePitchChange(movement.getPitchChangeRate());
        b.distanceTraveled(movement.getTotalDistance());
    }

    private void buildBranchRatio(List<Location> path, MiningFeatures.Builder b) {
        if (path.size() < 5) return;
        int branches = 0, total = 0;
        Vector lastDir = null;
        Location last = null;
        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            Vector dir = loc.toVector().subtract(last.toVector());
            if (dir.length() < 0.3) continue;
            total++;
            dir.normalize();
            if (lastDir != null) {
                double dot = lastDir.dot(dir);
                double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
                if (angle > 45 && angle < 135) branches++;
            }
            lastDir = dir;
            last = loc;
        }
        if (total > 0) b.branchMiningRatio((double) branches / total);
    }

    private void buildWallFollowRatio(Deque<MiningEvent> events, List<Location> path, MiningFeatures.Builder b) {
        if (path.size() < 5) return;
        int follows = 0;
        Set<String> oreLocations = new HashSet<>();
        for (MiningEvent e : events) {
            if (isOre(e.getBlockType())) {
                Location l = e.getLocation();
                oreLocations.add(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
            }
        }
        for (int i = 1; i < path.size(); i++) {
            Location cur = path.get(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String key = (cur.getBlockX() + dx) + "," + cur.getBlockY() + "," + (cur.getBlockZ() + dz);
                    if (oreLocations.contains(key)) { follows++; break; }
                }
            }
        }
        b.wallFollowRatio(path.size() > 0 ? (double) follows / path.size() : 0);
    }

    private void buildDirectionalConsistency(List<Location> path, MiningFeatures.Builder b) {
        if (path.size() < 5) return;
        Vector mainDir = null;
        int aligned = 0, total = 0;
        Location last = null;
        for (Location loc : path) {
            if (last == null) { last = loc; continue; }
            Vector dir = loc.toVector().subtract(last.toVector());
            if (dir.length() < 0.3) continue;
            total++;
            dir.normalize();
            if (mainDir == null) mainDir = dir;
            if (mainDir.dot(dir) > 0.7) aligned++;
            last = loc;
        }
        if (total > 0) b.directionalConsistency((double) aligned / total);
    }

    private void buildRareOreRatio(Deque<MiningEvent> events, MiningFeatures.Builder b) {
        int total = 0, rare = 0;
        for (MiningEvent e : events) {
            if (!isOre(e.getBlockType())) continue;
            total++;
            String n = e.getBlockType().name();
            if (n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("ANCIENT_DEBRIS")) rare++;
        }
        if (total > 0) b.rareOreRatio((double) rare / total);
    }

    private void buildClusterDensity(Deque<MiningEvent> events, MiningFeatures.Builder b) {
        Map<String, List<Location>> byType = new HashMap<>();
        for (MiningEvent e : events) {
            if (!isTracked(e.getBlockType())) continue;
            byType.computeIfAbsent(e.getBlockType().name(), k -> new ArrayList<>()).add(e.getLocation());
        }
        int closePairs = 0, totalPairs = 0;
        for (List<Location> locs : byType.values()) {
            for (int i = 0; i < locs.size(); i++) {
                for (int j = i + 1; j < locs.size(); j++) {
                    totalPairs++;
                    if (locs.get(i).distance(locs.get(j)) < 8) closePairs++;
                }
            }
        }
        b.clusterDensity(totalPairs > 0 ? (double) closePairs / totalPairs : 0);
    }

    private void buildExplorationEfficiency(Deque<MiningEvent> events, List<Location> path, MiningFeatures.Builder b) {
        Set<String> chunks = new HashSet<>();
        for (Location loc : path) chunks.add((loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4));
        int chunksWithOre = 0;
        Set<String> oreChunks = new HashSet<>();
        for (MiningEvent e : events) {
            if (!isTracked(e.getBlockType())) continue;
            Location l = e.getLocation();
            String ck = (l.getBlockX() >> 4) + "," + (l.getBlockZ() >> 4);
            oreChunks.add(ck);
        }
        chunksWithOre = oreChunks.size();
        b.uniqueChunksVisited(chunks.size());
        b.explorationEfficiency(chunks.size() > 0 ? (double) chunksWithOre / chunks.size() : 0);
    }

    private void buildTimeBetweenOres(Deque<MiningEvent> events, MiningFeatures.Builder b) {
        List<Long> oreTimes = new ArrayList<>();
        for (MiningEvent e : events) {
            if (isTracked(e.getBlockType())) oreTimes.add(e.getTimestamp());
        }
        if (oreTimes.size() >= 2) {
            double totalGap = 0;
            for (int i = 1; i < oreTimes.size(); i++) {
                totalGap += (oreTimes.get(i) - oreTimes.get(i - 1)) / 1000.0;
            }
            b.averageTimeBetweenOres(totalGap / (oreTimes.size() - 1));
        }
    }

    private void buildDistributions(Deque<MiningEvent> events, MiningFeatures.Builder b) {
        Map<String, Double> depths = new HashMap<>();
        Map<String, Double> biomes = new HashMap<>();
        int total = 0;
        for (MiningEvent e : events) {
            total++;
            int y = e.getBlockY();
            String depth = y < 0 ? "deepslate" : y < 16 ? "deep" : y < 32 ? "low" : y < 64 ? "mid" : "surface";
            depths.merge(depth, 1.0, Double::sum);
            String biome = e.getBiome().getKey().toString();
            biomes.merge(biome, 1.0, Double::sum);
        }
        if (total > 0) {
            double t = total;
            depths.replaceAll((k, v) -> v / t);
            biomes.replaceAll((k, v) -> v / t);
        }
        b.depthDistribution(depths);
        b.biomeDistribution(biomes);
        if (!events.isEmpty()) b.primaryDimension(events.getLast().getDimension().name());
    }

    private boolean isOre(org.bukkit.Material m) { return m.name().endsWith("_ORE"); }
    private boolean isTracked(org.bukkit.Material m) {
        if (!m.name().endsWith("_ORE")) return false;
        String n = m.name();
        return n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("ANCIENT_DEBRIS");
    }
}
