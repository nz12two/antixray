package com.antixray.plugin.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class MiningStatistics {
    private final UUID playerId;
    private volatile double blocksPerMinute;
    private volatile double oresPerMinute;
    private volatile double diamondsPerHour;
    private volatile double ancientDebrisPerHour;
    private volatile double miningEfficiency;
    private volatile double averageDistanceBetweenOres;
    private volatile double averageTunnelLength;
    private volatile double straightTunnelPercentage;
    private volatile double branchMiningPercentage;
    private volatile double oreToBlockRatio;
    private volatile double hiddenToExposedRatio;
    private volatile double averageTimeToFindOre;
    private final Map<String, Integer> depthDistribution = new ConcurrentHashMap<>();
    private final Map<String, Integer> biomeDistribution = new ConcurrentHashMap<>();
    private final Map<String, Integer> dimensionDistribution = new ConcurrentHashMap<>();
    private volatile long lastUpdated;

    public MiningStatistics(UUID playerId) {
        this.playerId = playerId;
        this.lastUpdated = System.currentTimeMillis();
    }

    public UUID getPlayerId() { return playerId; }
    public double getBlocksPerMinute() { return blocksPerMinute; }
    public void setBlocksPerMinute(double v) { this.blocksPerMinute = v; }
    public double getOresPerMinute() { return oresPerMinute; }
    public void setOresPerMinute(double v) { this.oresPerMinute = v; }
    public double getDiamondsPerHour() { return diamondsPerHour; }
    public void setDiamondsPerHour(double v) { this.diamondsPerHour = v; }
    public double getAncientDebrisPerHour() { return ancientDebrisPerHour; }
    public void setAncientDebrisPerHour(double v) { this.ancientDebrisPerHour = v; }
    public double getMiningEfficiency() { return miningEfficiency; }
    public void setMiningEfficiency(double v) { this.miningEfficiency = v; }
    public double getAverageDistanceBetweenOres() { return averageDistanceBetweenOres; }
    public void setAverageDistanceBetweenOres(double v) { this.averageDistanceBetweenOres = v; }
    public double getAverageTunnelLength() { return averageTunnelLength; }
    public void setAverageTunnelLength(double v) { this.averageTunnelLength = v; }
    public double getStraightTunnelPercentage() { return straightTunnelPercentage; }
    public void setStraightTunnelPercentage(double v) { this.straightTunnelPercentage = v; }
    public double getBranchMiningPercentage() { return branchMiningPercentage; }
    public void setBranchMiningPercentage(double v) { this.branchMiningPercentage = v; }
    public double getOreToBlockRatio() { return oreToBlockRatio; }
    public void setOreToBlockRatio(double v) { this.oreToBlockRatio = v; }
    public double getHiddenToExposedRatio() { return hiddenToExposedRatio; }
    public void setHiddenToExposedRatio(double v) { this.hiddenToExposedRatio = v; }
    public double getAverageTimeToFindOre() { return averageTimeToFindOre; }
    public void setAverageTimeToFindOre(double v) { this.averageTimeToFindOre = v; }
    public Map<String, Integer> getDepthDistribution() { return depthDistribution; }
    public Map<String, Integer> getBiomeDistribution() { return biomeDistribution; }
    public Map<String, Integer> getDimensionDistribution() { return dimensionDistribution; }
    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long v) { this.lastUpdated = v; }
}
