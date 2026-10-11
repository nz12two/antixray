package com.antixray.plugin.api.events;

import com.antixray.plugin.evidence.Evidence;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class PlayerEvidenceCreateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final String playerName;
    private final Evidence evidence;

    public PlayerEvidenceCreateEvent(UUID playerId, String playerName, Evidence evidence) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.evidence = evidence;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public Evidence getEvidence() { return evidence; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
