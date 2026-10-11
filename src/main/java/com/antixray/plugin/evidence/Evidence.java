package com.antixray.plugin.evidence;

import org.bukkit.Location;

import java.util.*;

public class Evidence {
    private final String id;
    private final UUID playerId;
    private final String playerName;
    private final long timestamp;
    private final double totalRiskScore;
    private final Map<String, Double> detectorScores;
    private final List<String> triggeredDetectors;
    private final List<Location> miningPath;
    private final String worldName;
    private final String sessionId;
    private final Map<String, Object> metadata;
    private final String detailedReason;

    private Evidence(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.playerId = builder.playerId;
        this.playerName = builder.playerName;
        this.timestamp = builder.timestamp;
        this.totalRiskScore = builder.totalRiskScore;
        this.detectorScores = Collections.unmodifiableMap(new HashMap<>(builder.detectorScores));
        this.triggeredDetectors = Collections.unmodifiableList(new ArrayList<>(builder.triggeredDetectors));
        this.miningPath = Collections.unmodifiableList(new ArrayList<>(builder.miningPath));
        this.worldName = builder.worldName;
        this.sessionId = builder.sessionId;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.detailedReason = builder.detailedReason;
    }

    public String getId() { return id; }
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public long getTimestamp() { return timestamp; }
    public double getTotalRiskScore() { return totalRiskScore; }
    public Map<String, Double> getDetectorScores() { return detectorScores; }
    public List<String> getTriggeredDetectors() { return triggeredDetectors; }
    public List<Location> getMiningPath() { return miningPath; }
    public String getWorldName() { return worldName; }
    public String getSessionId() { return sessionId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getDetailedReason() { return detailedReason; }

    public static class Builder {
        private String id;
        private UUID playerId;
        private String playerName;
        private long timestamp;
        private double totalRiskScore;
        private final Map<String, Double> detectorScores = new HashMap<>();
        private final List<String> triggeredDetectors = new ArrayList<>();
        private final List<Location> miningPath = new ArrayList<>();
        private String worldName;
        private String sessionId;
        private final Map<String, Object> metadata = new HashMap<>();
        private String detailedReason;

        public Builder id(String id) { this.id = id; return this; }
        public Builder playerId(UUID playerId) { this.playerId = playerId; return this; }
        public Builder playerName(String playerName) { this.playerName = playerName; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder totalRiskScore(double totalRiskScore) { this.totalRiskScore = totalRiskScore; return this; }
        public Builder addDetectorScore(String detector, double score) { this.detectorScores.put(detector, score); return this; }
        public Builder addTriggeredDetector(String detector) { this.triggeredDetectors.add(detector); return this; }
        public Builder addPathLocation(Location location) { this.miningPath.add(location.clone()); return this; }
        public Builder worldName(String worldName) { this.worldName = worldName; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder addMetadata(String key, Object value) { this.metadata.put(key, value); return this; }
        public Builder detailedReason(String detailedReason) { this.detailedReason = detailedReason; return this; }

        public Evidence build() {
            Objects.requireNonNull(playerId, "playerId must not be null");
            if (timestamp == 0) this.timestamp = System.currentTimeMillis();
            if (sessionId == null) this.sessionId = "session-" + timestamp;
            return new Evidence(this);
        }
    }
}
