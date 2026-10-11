package com.antixray.plugin.model;

import com.antixray.plugin.profile.PlayerProfile;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe per-player data container.
 * <p>
 * Uses ReentrantReadWriteLock for the main data lock, allowing concurrent reads
 * while serializing writes. Contains mining event history (1000 max), mining path
 * (500 max), session tracking, risk profile, movement data, and player profile.
 * <p>
 * Inner classes RiskProfile and MovementData use their own synchronized locks for
 * fine-grained concurrency.
 */
public class PlayerData {
    private final UUID playerId;
    private volatile String playerName;
    private final Deque<MiningEvent> recentEvents;
    private final List<Location> miningPath;
    private volatile PlayerSession currentSession;
    private volatile MiningStatistics statistics;
    private volatile RiskProfile riskProfile;
    private volatile MovementData movement;
    private volatile PlayerProfile playerProfile;
    private volatile long lastMiningTime;
    private volatile int totalBlocksMined;
    private volatile int totalOresFound;
    private volatile long sessionStartTime;
    private volatile boolean tracked;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public PlayerData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.recentEvents = new ConcurrentLinkedDeque<>();
        this.miningPath = Collections.synchronizedList(new ArrayList<>());
        this.currentSession = new PlayerSession(playerId);
        this.statistics = new MiningStatistics(playerId);
        this.riskProfile = new RiskProfile(playerId);
        this.movement = new MovementData();
        this.playerProfile = new PlayerProfile(playerId);
        this.sessionStartTime = System.currentTimeMillis();
        this.tracked = true;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String v) { this.playerName = v; }
    public Deque<MiningEvent> getRecentEvents() { return recentEvents; }
    public List<Location> getMiningPath() { return miningPath; }

    public PlayerSession getCurrentSession() {
        readLock.lock();
        try { return currentSession; } finally { readLock.unlock(); }
    }
    public void setCurrentSession(PlayerSession v) {
        writeLock.lock();
        try { this.currentSession = v; } finally { writeLock.unlock(); }
    }
    public MiningStatistics getStatistics() {
        readLock.lock();
        try { return statistics; } finally { readLock.unlock(); }
    }
    public void setStatistics(MiningStatistics v) {
        writeLock.lock();
        try { this.statistics = v; } finally { writeLock.unlock(); }
    }
    public RiskProfile getRiskProfile() {
        readLock.lock();
        try { return riskProfile; } finally { readLock.unlock(); }
    }
    public void setRiskProfile(RiskProfile v) {
        writeLock.lock();
        try { this.riskProfile = v; } finally { writeLock.unlock(); }
    }
    public MovementData getMovement() {
        readLock.lock();
        try { return movement; } finally { readLock.unlock(); }
    }
    public PlayerProfile getPlayerProfile() {
        readLock.lock();
        try { return playerProfile; } finally { readLock.unlock(); }
    }
    public void setPlayerProfile(PlayerProfile v) {
        writeLock.lock();
        try { this.playerProfile = v; } finally { writeLock.unlock(); }
    }

    public long getLastMiningTime() { return lastMiningTime; }
    public void setLastMiningTime(long v) { this.lastMiningTime = v; }
    public int getTotalBlocksMined() { return totalBlocksMined; }
    public void setTotalBlocksMined(int v) { this.totalBlocksMined = v; }
    public int getTotalOresFound() { return totalOresFound; }
    public void setTotalOresFound(int v) { this.totalOresFound = v; }
    public long getSessionStartTime() { return sessionStartTime; }
    public boolean isTracked() { return tracked; }
    public void setTracked(boolean v) { this.tracked = v; }

    public void addEvent(MiningEvent event) {
        writeLock.lock();
        try {
            recentEvents.addLast(event);
            if (recentEvents.size() > 1000) recentEvents.removeFirst();
            this.lastMiningTime = event.getTimestamp();
            this.totalBlocksMined++;
        } finally {
            writeLock.unlock();
        }
    }

    public void addPathLocation(Location location) {
        writeLock.lock();
        try {
            miningPath.add(location.clone());
            if (miningPath.size() > 500) miningPath.remove(0);
        } finally {
            writeLock.unlock();
        }
    }

    public void clearData() {
        writeLock.lock();
        try {
            recentEvents.clear();
            miningPath.clear();
            totalBlocksMined = 0;
            totalOresFound = 0;
        } finally {
            writeLock.unlock();
        }
    }

    public static class RiskProfile {
        private final UUID playerId;
        private final Object lock = new Object();
        private double currentRiskScore;
        private double peakRiskScore;
        private int totalFlags;
        private long lastFlagTime;
        private long lastDecayTime;
        private final Map<String, Double> detectorScores;

        public RiskProfile(UUID playerId) {
            this.playerId = playerId;
            this.detectorScores = new HashMap<>();
            this.lastDecayTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public double getCurrentRiskScore() { synchronized (lock) { return currentRiskScore; } }
        public void setCurrentRiskScore(double v) { synchronized (lock) { this.currentRiskScore = v; } }
        public double getPeakRiskScore() { synchronized (lock) { return peakRiskScore; } }
        public void setPeakRiskScore(double v) { synchronized (lock) { this.peakRiskScore = v; } }
        public int getTotalFlags() { synchronized (lock) { return totalFlags; } }
        public void incrementFlags() { synchronized (lock) { this.totalFlags++; } }
        public long getLastFlagTime() { synchronized (lock) { return lastFlagTime; } }
        public void setLastFlagTime(long v) { synchronized (lock) { this.lastFlagTime = v; } }
        public Map<String, Double> getDetectorScores() { synchronized (lock) { return detectorScores; } }
        public long getLastDecayTime() { synchronized (lock) { return lastDecayTime; } }
        public void setLastDecayTime(long v) { synchronized (lock) { this.lastDecayTime = v; } }
    }

    public static class MovementData {
        private final Object lock = new Object();
        private double totalDistance;
        private double yaw;
        private double pitch;
        private double speed;
        private Vector lastPosition;
        private long lastMoveTime;
        private double yawChangeRate;
        private double pitchChangeRate;
        private int suspiciousTurns;

        public double getTotalDistance() { synchronized (lock) { return totalDistance; } }
        public void setTotalDistance(double v) { synchronized (lock) { this.totalDistance = v; } }
        public double getYaw() { synchronized (lock) { return yaw; } }
        public void setYaw(double v) { synchronized (lock) { this.yaw = v; } }
        public double getPitch() { synchronized (lock) { return pitch; } }
        public void setPitch(double v) { synchronized (lock) { this.pitch = v; } }
        public double getSpeed() { synchronized (lock) { return speed; } }
        public void setSpeed(double v) { synchronized (lock) { this.speed = v; } }
        public Vector getLastPosition() { synchronized (lock) { return lastPosition; } }
        public void setLastPosition(Vector v) { synchronized (lock) { this.lastPosition = v; } }
        public long getLastMoveTime() { synchronized (lock) { return lastMoveTime; } }
        public void setLastMoveTime(long v) { synchronized (lock) { this.lastMoveTime = v; } }
        public double getYawChangeRate() { synchronized (lock) { return yawChangeRate; } }
        public void setYawChangeRate(double v) { synchronized (lock) { this.yawChangeRate = v; } }
        public double getPitchChangeRate() { synchronized (lock) { return pitchChangeRate; } }
        public void setPitchChangeRate(double v) { synchronized (lock) { this.pitchChangeRate = v; } }
        public int getSuspiciousTurns() { synchronized (lock) { return suspiciousTurns; } }
        public void setSuspiciousTurns(int v) { synchronized (lock) { this.suspiciousTurns = v; } }
    }
}
