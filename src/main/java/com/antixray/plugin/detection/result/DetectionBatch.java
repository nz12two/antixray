package com.antixray.plugin.detection.result;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DetectionBatch {
    private final UUID playerId;
    private final String playerName;
    private final List<DetectionResult> results;
    private final ExplainVector explainVector;

    public DetectionBatch(UUID playerId, String playerName, List<DetectionResult> results) {
        this(playerId, playerName, results, null);
    }

    public DetectionBatch(UUID playerId, String playerName, List<DetectionResult> results, ExplainVector explainVector) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.results = Collections.unmodifiableList(results);
        this.explainVector = explainVector;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public List<DetectionResult> getResults() { return results; }
    public ExplainVector getExplainVector() { return explainVector; }
}
