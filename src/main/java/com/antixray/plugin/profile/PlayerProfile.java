package com.antixray.plugin.profile;

import java.util.UUID;

public class PlayerProfile {
    private final UUID playerId;
    private double playtimeHours;
    private double skillLevelEstimate;
    private MiningStyle miningStyle;
    private double trustScore;
    private double pingStability;
    private double deviceStability;
    private long firstSeen;
    private long lastUpdated;

    public enum MiningStyle {
        UNKNOWN, BRANCH, CAVE, HYBRID, EXPLORER
    }

    public PlayerProfile(UUID playerId) {
        this.playerId = playerId;
        this.firstSeen = System.currentTimeMillis();
        this.lastUpdated = this.firstSeen;
        this.miningStyle = MiningStyle.UNKNOWN;
        this.trustScore = 0.5;
        this.skillLevelEstimate = 0.3;
        this.pingStability = 1.0;
        this.deviceStability = 1.0;
    }

    public UUID getPlayerId() { return playerId; }
    public double getPlaytimeHours() { return playtimeHours; }
    public void setPlaytimeHours(double v) { this.playtimeHours = v; }
    public double getSkillLevelEstimate() { return skillLevelEstimate; }
    public void setSkillLevelEstimate(double v) { this.skillLevelEstimate = Math.max(0, Math.min(1, v)); }
    public MiningStyle getMiningStyle() { return miningStyle; }
    public void setMiningStyle(MiningStyle v) { this.miningStyle = v; }
    public double getTrustScore() { return trustScore; }
    public void setTrustScore(double v) { this.trustScore = Math.max(0, Math.min(1, v)); }
    public double getPingStability() { return pingStability; }
    public void setPingStability(double v) { this.pingStability = Math.max(0, Math.min(1, v)); }
    public double getDeviceStability() { return deviceStability; }
    public void setDeviceStability(double v) { this.deviceStability = Math.max(0, Math.min(1, v)); }
    public long getFirstSeen() { return firstSeen; }
    public long getLastUpdated() { return lastUpdated; }

    public double getAdjustedThreshold(double baseThreshold) {
        double discount = trustScore * 0.3;
        double skillFactor = skillLevelEstimate * 0.1;
        return baseThreshold * (1.0 - discount + skillFactor);
    }
}
