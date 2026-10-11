package com.antixray.plugin.learning;

import java.util.*;

public class Fingerprint {
    private final UUID sessionId;
    private final UUID playerId;
    private final String playerName;
    private final long sessionStart;
    private final long sessionEnd;
    private final Map<String, Double> detectorScores;
    private final double blocksPerMinute;
    private final double oresPerMinute;
    private final double hiddenOreRatio;
    private final double tunnelStraightness;
    private final double caveExplorationRatio;
    private final double movementEntropy;
    private final double oreSpaceCorrelation;
    private final double explorationEfficiency;
    private final double averageYLevel;
    private final Label label;

    public enum Label { UNLABELED, POSITIVE, NEGATIVE }

    private Fingerprint(Builder b) {
        this.sessionId = b.sessionId;
        this.playerId = b.playerId;
        this.playerName = b.playerName;
        this.sessionStart = b.sessionStart;
        this.sessionEnd = b.sessionEnd;
        this.detectorScores = Collections.unmodifiableMap(new LinkedHashMap<>(b.detectorScores));
        this.blocksPerMinute = b.blocksPerMinute;
        this.oresPerMinute = b.oresPerMinute;
        this.hiddenOreRatio = b.hiddenOreRatio;
        this.tunnelStraightness = b.tunnelStraightness;
        this.caveExplorationRatio = b.caveExplorationRatio;
        this.movementEntropy = b.movementEntropy;
        this.oreSpaceCorrelation = b.oreSpaceCorrelation;
        this.explorationEfficiency = b.explorationEfficiency;
        this.averageYLevel = b.averageYLevel;
        this.label = b.label;
    }

    public double[] toVector() {
        double[] v = new double[12 + detectorScores.size()];
        int i = 0;
        v[i++] = blocksPerMinute / 200.0;
        v[i++] = oresPerMinute / 10.0;
        v[i++] = hiddenOreRatio;
        v[i++] = tunnelStraightness;
        v[i++] = caveExplorationRatio;
        v[i++] = movementEntropy;
        v[i++] = oreSpaceCorrelation;
        v[i++] = explorationEfficiency;
        v[i++] = averageYLevel / 64.0;
        v[i++] = sessionDurationMinutes() / 120.0;
        for (double d : detectorScores.values()) v[i++] = d / 100.0;
        return v;
    }

    public double sessionDurationMinutes() {
        return (sessionEnd - sessionStart) / 60_000.0;
    }

    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public long sessionStart() { return sessionStart; }
    public long sessionEnd() { return sessionEnd; }
    public Map<String, Double> detectorScores() { return detectorScores; }
    public double blocksPerMinute() { return blocksPerMinute; }
    public double oresPerMinute() { return oresPerMinute; }
    public double hiddenOreRatio() { return hiddenOreRatio; }
    public double tunnelStraightness() { return tunnelStraightness; }
    public double caveExplorationRatio() { return caveExplorationRatio; }
    public double movementEntropy() { return movementEntropy; }
    public double oreSpaceCorrelation() { return oreSpaceCorrelation; }
    public double explorationEfficiency() { return explorationEfficiency; }
    public double averageYLevel() { return averageYLevel; }
    public Label label() { return label; }

    public Fingerprint withLabel(Label label) {
        return new Builder().from(this).label(label).build();
    }

    public static class Builder {
        private UUID sessionId;
        private UUID playerId;
        private String playerName;
        private long sessionStart;
        private long sessionEnd;
        private Map<String, Double> detectorScores = new LinkedHashMap<>();
        private double blocksPerMinute;
        private double oresPerMinute;
        private double hiddenOreRatio;
        private double tunnelStraightness;
        private double caveExplorationRatio;
        private double movementEntropy;
        private double oreSpaceCorrelation;
        private double explorationEfficiency;
        private double averageYLevel;
        private Label label = Label.UNLABELED;

        public Builder sessionId(UUID v) { this.sessionId = v; return this; }
        public Builder playerId(UUID v) { this.playerId = v; return this; }
        public Builder playerName(String v) { this.playerName = v; return this; }
        public Builder sessionStart(long v) { this.sessionStart = v; return this; }
        public Builder sessionEnd(long v) { this.sessionEnd = v; return this; }
        public Builder detectorScore(String name, double v) { this.detectorScores.put(name, v); return this; }
        public Builder detectorScores(Map<String, Double> v) { this.detectorScores.putAll(v); return this; }
        public Builder blocksPerMinute(double v) { this.blocksPerMinute = v; return this; }
        public Builder oresPerMinute(double v) { this.oresPerMinute = v; return this; }
        public Builder hiddenOreRatio(double v) { this.hiddenOreRatio = v; return this; }
        public Builder tunnelStraightness(double v) { this.tunnelStraightness = v; return this; }
        public Builder caveExplorationRatio(double v) { this.caveExplorationRatio = v; return this; }
        public Builder movementEntropy(double v) { this.movementEntropy = v; return this; }
        public Builder oreSpaceCorrelation(double v) { this.oreSpaceCorrelation = v; return this; }
        public Builder explorationEfficiency(double v) { this.explorationEfficiency = v; return this; }
        public Builder averageYLevel(double v) { this.averageYLevel = v; return this; }
        public Builder label(Label v) { this.label = v; return this; }

        public Fingerprint build() {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(playerId, "playerId");
            return new Fingerprint(this);
        }

        public Builder from(Fingerprint f) {
            this.sessionId = f.sessionId;
            this.playerId = f.playerId;
            this.playerName = f.playerName;
            this.sessionStart = f.sessionStart;
            this.sessionEnd = f.sessionEnd;
            this.detectorScores = new LinkedHashMap<>(f.detectorScores);
            this.blocksPerMinute = f.blocksPerMinute;
            this.oresPerMinute = f.oresPerMinute;
            this.hiddenOreRatio = f.hiddenOreRatio;
            this.tunnelStraightness = f.tunnelStraightness;
            this.caveExplorationRatio = f.caveExplorationRatio;
            this.movementEntropy = f.movementEntropy;
            this.oreSpaceCorrelation = f.oreSpaceCorrelation;
            this.explorationEfficiency = f.explorationEfficiency;
            this.averageYLevel = f.averageYLevel;
            this.label = f.label;
            return this;
        }
    }
}
