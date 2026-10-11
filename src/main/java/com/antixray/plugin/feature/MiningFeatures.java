package com.antixray.plugin.feature;

import java.util.Map;

public class MiningFeatures {
    public static final int SCHEMA_VERSION = 2;
    private final double averageTurnAngle;
    private final double hiddenOreRatio;
    private final double tunnelStraightness;
    private final double caveExplorationRatio;
    private final double oreDensity;
    private final double movementEntropy;
    private final double sessionDurationMinutes;
    private final double blocksPerMinute;
    private final double oresPerMinute;
    private final double averageYawChange;
    private final double averagePitchChange;
    private final double branchMiningRatio;
    private final double wallFollowRatio;
    private final double directionalConsistency;
    private final double rareOreRatio;
    private final double clusterDensity;
    private final double explorationEfficiency;
    private final double distanceTraveled;
    private final double averageTimeBetweenOres;
    private final int uniqueChunksVisited;
    private final int totalEvents;
    private final Map<String, Double> depthDistribution;
    private final Map<String, Double> biomeDistribution;
    private final String primaryDimension;

    public MiningFeatures(Builder builder) {
        this.averageTurnAngle = builder.averageTurnAngle;
        this.hiddenOreRatio = builder.hiddenOreRatio;
        this.tunnelStraightness = builder.tunnelStraightness;
        this.caveExplorationRatio = builder.caveExplorationRatio;
        this.oreDensity = builder.oreDensity;
        this.movementEntropy = builder.movementEntropy;
        this.sessionDurationMinutes = builder.sessionDurationMinutes;
        this.blocksPerMinute = builder.blocksPerMinute;
        this.oresPerMinute = builder.oresPerMinute;
        this.averageYawChange = builder.averageYawChange;
        this.averagePitchChange = builder.averagePitchChange;
        this.branchMiningRatio = builder.branchMiningRatio;
        this.wallFollowRatio = builder.wallFollowRatio;
        this.directionalConsistency = builder.directionalConsistency;
        this.rareOreRatio = builder.rareOreRatio;
        this.clusterDensity = builder.clusterDensity;
        this.explorationEfficiency = builder.explorationEfficiency;
        this.distanceTraveled = builder.distanceTraveled;
        this.averageTimeBetweenOres = builder.averageTimeBetweenOres;
        this.uniqueChunksVisited = builder.uniqueChunksVisited;
        this.totalEvents = builder.totalEvents;
        this.depthDistribution = builder.depthDistribution;
        this.biomeDistribution = builder.biomeDistribution;
        this.primaryDimension = builder.primaryDimension;
    }

    public double getAverageTurnAngle() { return averageTurnAngle; }
    public double getHiddenOreRatio() { return hiddenOreRatio; }
    public double getTunnelStraightness() { return tunnelStraightness; }
    public double getCaveExplorationRatio() { return caveExplorationRatio; }
    public double getOreDensity() { return oreDensity; }
    public double getMovementEntropy() { return movementEntropy; }
    public double getSessionDurationMinutes() { return sessionDurationMinutes; }
    public double getBlocksPerMinute() { return blocksPerMinute; }
    public double getOresPerMinute() { return oresPerMinute; }
    public double getAverageYawChange() { return averageYawChange; }
    public double getAveragePitchChange() { return averagePitchChange; }
    public double getBranchMiningRatio() { return branchMiningRatio; }
    public double getWallFollowRatio() { return wallFollowRatio; }
    public double getDirectionalConsistency() { return directionalConsistency; }
    public double getRareOreRatio() { return rareOreRatio; }
    public double getClusterDensity() { return clusterDensity; }
    public double getExplorationEfficiency() { return explorationEfficiency; }
    public double getDistanceTraveled() { return distanceTraveled; }
    public double getAverageTimeBetweenOres() { return averageTimeBetweenOres; }
    public int getUniqueChunksVisited() { return uniqueChunksVisited; }
    public int getTotalEvents() { return totalEvents; }
    public Map<String, Double> getDepthDistribution() { return depthDistribution; }
    public Map<String, Double> getBiomeDistribution() { return biomeDistribution; }
    public String getPrimaryDimension() { return primaryDimension; }

    public boolean hasMinData() { return totalEvents >= 10; }
    public int getSchemaVersion() { return SCHEMA_VERSION; }
    public static int getCurrentSchemaVersion() { return SCHEMA_VERSION; }

    public static class Builder {
        private double averageTurnAngle;
        private double hiddenOreRatio;
        private double tunnelStraightness;
        private double caveExplorationRatio;
        private double oreDensity;
        private double movementEntropy;
        private double sessionDurationMinutes;
        private double blocksPerMinute;
        private double oresPerMinute;
        private double averageYawChange;
        private double averagePitchChange;
        private double branchMiningRatio;
        private double wallFollowRatio;
        private double directionalConsistency;
        private double rareOreRatio;
        private double clusterDensity;
        private double explorationEfficiency;
        private double distanceTraveled;
        private double averageTimeBetweenOres;
        private int uniqueChunksVisited;
        private int totalEvents;
        private Map<String, Double> depthDistribution = Map.of();
        private Map<String, Double> biomeDistribution = Map.of();
        private String primaryDimension = "NORMAL";

        public Builder averageTurnAngle(double v) { this.averageTurnAngle = v; return this; }
        public Builder hiddenOreRatio(double v) { this.hiddenOreRatio = v; return this; }
        public Builder tunnelStraightness(double v) { this.tunnelStraightness = v; return this; }
        public Builder caveExplorationRatio(double v) { this.caveExplorationRatio = v; return this; }
        public Builder oreDensity(double v) { this.oreDensity = v; return this; }
        public Builder movementEntropy(double v) { this.movementEntropy = v; return this; }
        public Builder sessionDurationMinutes(double v) { this.sessionDurationMinutes = v; return this; }
        public Builder blocksPerMinute(double v) { this.blocksPerMinute = v; return this; }
        public Builder oresPerMinute(double v) { this.oresPerMinute = v; return this; }
        public Builder averageYawChange(double v) { this.averageYawChange = v; return this; }
        public Builder averagePitchChange(double v) { this.averagePitchChange = v; return this; }
        public Builder branchMiningRatio(double v) { this.branchMiningRatio = v; return this; }
        public Builder wallFollowRatio(double v) { this.wallFollowRatio = v; return this; }
        public Builder directionalConsistency(double v) { this.directionalConsistency = v; return this; }
        public Builder rareOreRatio(double v) { this.rareOreRatio = v; return this; }
        public Builder clusterDensity(double v) { this.clusterDensity = v; return this; }
        public Builder explorationEfficiency(double v) { this.explorationEfficiency = v; return this; }
        public Builder distanceTraveled(double v) { this.distanceTraveled = v; return this; }
        public Builder averageTimeBetweenOres(double v) { this.averageTimeBetweenOres = v; return this; }
        public Builder uniqueChunksVisited(int v) { this.uniqueChunksVisited = v; return this; }
        public Builder totalEvents(int v) { this.totalEvents = v; return this; }
        public Builder depthDistribution(Map<String, Double> v) { this.depthDistribution = v; return this; }
        public Builder biomeDistribution(Map<String, Double> v) { this.biomeDistribution = v; return this; }
        public Builder primaryDimension(String v) { this.primaryDimension = v; return this; }

        public MiningFeatures build() {
            return new MiningFeatures(this);
        }
    }
}
