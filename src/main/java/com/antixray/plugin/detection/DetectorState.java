package com.antixray.plugin.detection;

import java.util.UUID;

public class DetectorState {
    private final UUID playerId;
    private final String detectorName;
    private double accumulatedSuspicion;
    private int triggerCount;
    private double peakScore;
    private long firstTriggerTime;
    private long lastTriggerTime;
    private int windowCount;

    public DetectorState(UUID playerId, String detectorName) {
        this.playerId = playerId;
        this.detectorName = detectorName;
        this.firstTriggerTime = System.currentTimeMillis();
        this.lastTriggerTime = this.firstTriggerTime;
    }

    public void recordTrigger(double score, long now) {
        long elapsed = now - lastTriggerTime;
        if (elapsed > 30_000) {
            accumulatedSuspicion = 0;
            windowCount = 0;
        }
        accumulatedSuspicion += score;
        triggerCount++;
        windowCount++;
        peakScore = Math.max(peakScore, score);
        lastTriggerTime = now;
    }

    public double getAccumulatedSuspicion() { return Math.min(100, accumulatedSuspicion); }
    public int getTriggerCount() { return triggerCount; }
    public int getWindowCount() { return windowCount; }
    public double getPeakScore() { return peakScore; }
    public String getDetectorName() { return detectorName; }
    public UUID getPlayerId() { return playerId; }

    public boolean isActive(long now) {
        return (now - lastTriggerTime) < 30_000;
    }

    public void reset() {
        accumulatedSuspicion = 0;
        triggerCount = 0;
        windowCount = 0;
        peakScore = 0;
    }
}
