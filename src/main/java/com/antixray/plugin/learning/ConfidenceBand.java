package com.antixray.plugin.learning;

public enum ConfidenceBand {
    DISCARD(0, 40),
    STATS_ONLY(40, 70),
    TEMPORARY(70, 90),
    PERMANENT(90, 100);

    private final double minScore;
    private final double maxScore;

    ConfidenceBand(double minScore, double maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public static ConfidenceBand forScore(double risk) {
        if (risk < 40) return DISCARD;
        if (risk < 70) return STATS_ONLY;
        if (risk < 90) return TEMPORARY;
        return PERMANENT;
    }

    public boolean accumulates() {
        return this != DISCARD;
    }

    public boolean persists() {
        return this == PERMANENT || this == TEMPORARY;
    }

    public boolean isPermanent() {
        return this == PERMANENT;
    }

    public boolean isTemporary() {
        return this == TEMPORARY;
    }

    public double minScore() { return minScore; }
    public double maxScore() { return maxScore; }
}
