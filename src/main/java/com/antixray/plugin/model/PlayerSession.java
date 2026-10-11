package com.antixray.plugin.model;

import java.util.UUID;

public class PlayerSession {
    private final UUID playerId;
    private final long startTime;
    private final Object lock = new Object();
    private long lastActivityTime;
    private long totalPlayTime;
    private boolean active;
    private String worldName;
    private int blocksMined;
    private int oresFound;

    public PlayerSession(UUID playerId) {
        this.playerId = playerId;
        this.startTime = System.currentTimeMillis();
        this.lastActivityTime = this.startTime;
        this.active = true;
    }

    public UUID getPlayerId() { return playerId; }
    public long getStartTime() { return startTime; }
    public long getLastActivityTime() { return lastActivityTime; }
    public void setLastActivityTime(long v) { this.lastActivityTime = v; }
    public boolean isActive() { return active; }

    public long getTotalPlayTime() {
        synchronized (lock) {
            return totalPlayTime + (active ? System.currentTimeMillis() - lastActivityTime : 0);
        }
    }

    public void setActive(boolean active) {
        synchronized (lock) {
            if (this.active && !active) {
                totalPlayTime += System.currentTimeMillis() - lastActivityTime;
            }
            this.active = active;
        }
    }

    public String getWorldName() { return worldName; }
    public void setWorldName(String v) { this.worldName = v; }
    public int getBlocksMined() { return blocksMined; }
    public void incrementBlocksMined() { synchronized (lock) { this.blocksMined++; } }
    public int getOresFound() { return oresFound; }
    public void incrementOresFound() { synchronized (lock) { this.oresFound++; } }
}
