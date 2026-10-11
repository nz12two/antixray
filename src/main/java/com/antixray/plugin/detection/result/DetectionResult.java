package com.antixray.plugin.detection.result;

import java.util.*;

public class DetectionResult {
    private final String detectorName;
    private final double score;
    private final double confidence;
    private final String reason;
    private final List<String> evidence;
    private final Map<String, Object> metadata;
    private final ExplainVector explainVector;
    private final long timestamp;

    private DetectionResult(Builder builder) {
        this.detectorName = builder.detectorName;
        this.score = builder.score;
        this.confidence = builder.confidence;
        this.reason = builder.reason;
        this.evidence = Collections.unmodifiableList(new ArrayList<>(builder.evidence));
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.explainVector = builder.explainVector;
        this.timestamp = System.currentTimeMillis();
    }

    public String getDetectorName() { return detectorName; }
    public double getScore() { return score; }
    public double getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public List<String> getEvidence() { return evidence; }
    public Map<String, Object> getMetadata() { return metadata; }
    public ExplainVector getExplainVector() { return explainVector; }
    public long getTimestamp() { return timestamp; }

    public boolean isSignificant() {
        return score > 0 && confidence > 0;
    }

    public static class Builder {
        private String detectorName;
        private double score;
        private double confidence;
        private String reason;
        private ExplainVector explainVector;
        private final List<String> evidence = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder detectorName(String detectorName) { this.detectorName = detectorName; return this; }
        public Builder score(double score) { this.score = Math.max(0, Math.min(100, score)); return this; }
        public Builder confidence(double confidence) { this.confidence = Math.max(0, Math.min(100, confidence)); return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder explainVector(ExplainVector explainVector) { this.explainVector = explainVector; return this; }
        public Builder addEvidence(String evidence) { this.evidence.add(evidence); return this; }
        public Builder addMetadata(String key, Object value) { this.metadata.put(key, value); return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata.putAll(metadata); return this; }

        public DetectionResult build() {
            Objects.requireNonNull(detectorName, "detectorName must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            return new DetectionResult(this);
        }
    }
}
