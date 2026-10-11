package com.antixray.plugin.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

public class PlayerPunishmentEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final String playerName;
    private final double riskScore;
    private final List<String> actionsExecuted;

    public PlayerPunishmentEvent(UUID playerId, String playerName, double riskScore, List<String> actionsExecuted) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.riskScore = riskScore;
        this.actionsExecuted = actionsExecuted;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public double getRiskScore() { return riskScore; }
    public List<String> getActionsExecuted() { return actionsExecuted; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
