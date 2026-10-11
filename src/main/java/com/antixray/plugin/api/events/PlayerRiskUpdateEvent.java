package com.antixray.plugin.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;
import java.util.UUID;

public class PlayerRiskUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final String playerName;
    private final double riskScore;
    private final Map<String, Double> detectorScores;

    public PlayerRiskUpdateEvent(UUID playerId, String playerName, double riskScore, Map<String, Double> detectorScores) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.riskScore = riskScore;
        this.detectorScores = detectorScores;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public double getRiskScore() { return riskScore; }
    public Map<String, Double> getDetectorScores() { return detectorScores; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
