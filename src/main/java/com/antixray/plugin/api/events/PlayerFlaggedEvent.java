package com.antixray.plugin.api.events;

import com.antixray.plugin.detection.result.DetectionResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

public class PlayerFlaggedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final String playerName;
    private final double riskScore;
    private final List<DetectionResult> results;

    public PlayerFlaggedEvent(UUID playerId, String playerName, double riskScore, List<DetectionResult> results) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.riskScore = riskScore;
        this.results = results;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public double getRiskScore() { return riskScore; }
    public List<DetectionResult> getResults() { return results; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
