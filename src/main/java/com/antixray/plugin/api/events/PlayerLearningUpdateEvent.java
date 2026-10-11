package com.antixray.plugin.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class PlayerLearningUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final String playerName;
    private final double serverBlocksPerMin;
    private final double serverOreRate;
    private final double playerDeviation;

    public PlayerLearningUpdateEvent(UUID playerId, String playerName, double serverBlocksPerMin, double serverOreRate, double playerDeviation) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.serverBlocksPerMin = serverBlocksPerMin;
        this.serverOreRate = serverOreRate;
        this.playerDeviation = playerDeviation;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public double getServerBlocksPerMin() { return serverBlocksPerMin; }
    public double getServerOreRate() { return serverOreRate; }
    public double getPlayerDeviation() { return playerDeviation; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
