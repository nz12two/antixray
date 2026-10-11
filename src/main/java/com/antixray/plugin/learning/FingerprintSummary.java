package com.antixray.plugin.learning;

import java.util.*;

public class FingerprintSummary {
    private final String label;
    private final int originalCount;
    private final long minTimestamp;
    private final long maxTimestamp;
    private final double[] mean;
    private final double[] stddev;
    private final double[] p50;
    private final double[] p95;
    private final int featureCount;

    private FingerprintSummary(Builder b) {
        this.label = b.label;
        this.originalCount = b.originalCount;
        this.minTimestamp = b.minTimestamp;
        this.maxTimestamp = b.maxTimestamp;
        this.mean = b.mean;
        this.stddev = b.stddev;
        this.p50 = b.p50;
        this.p95 = b.p95;
        this.featureCount = b.mean.length;
    }

    public static FingerprintSummary fromFingerprints(String label, List<Fingerprint> fingerprints) {
        if (fingerprints.isEmpty()) throw new IllegalArgumentException("Empty list");
        int n = fingerprints.size();
        int fCount = fingerprints.get(0).toVector().length;

        double[][] values = new double[fCount][n];
        long minTs = Long.MAX_VALUE, maxTs = 0;

        for (int i = 0; i < n; i++) {
            double[] vec = fingerprints.get(i).toVector();
            for (int j = 0; j < fCount; j++) values[j][i] = vec[j];
            minTs = Math.min(minTs, fingerprints.get(i).sessionStart());
            maxTs = Math.max(maxTs, fingerprints.get(i).sessionEnd());
        }

        Builder b = new Builder()
                .label(label)
                .originalCount(n)
                .minTimestamp(minTs)
                .maxTimestamp(maxTs);

        for (int j = 0; j < fCount; j++) {
            double[] sorted = Arrays.copyOf(values[j], n);
            Arrays.sort(sorted);
            double m = Arrays.stream(values[j]).average().orElse(0);
            double sd = Math.sqrt(Arrays.stream(values[j]).map(v -> Math.pow(v - m, 2)).average().orElse(0));
            b.addFeature(m, sd,
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95));
        }

        return b.build();
    }

    private static double percentile(double[] sorted, double p) {
        int idx = (int) Math.round(p * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    public double similarityTo(Fingerprint fp) {
        double[] vec = fp.toVector();
        if (vec.length != featureCount) return 0;
        double score = 0;
        for (int i = 0; i < featureCount; i++) {
            double z = stddev[i] > 0 ? Math.abs(vec[i] - mean[i]) / stddev[i] : 0;
            if (z < 0.5) score += 1.0;
            else if (z < 1.0) score += 0.7;
            else if (z < 1.5) score += 0.3;
        }
        return score / featureCount;
    }

    public String label() { return label; }
    public int originalCount() { return originalCount; }
    public long minTimestamp() { return minTimestamp; }
    public long maxTimestamp() { return maxTimestamp; }
    public int featureCount() { return featureCount; }
    public double mean(int i) { return mean[i]; }
    public double stddev(int i) { return stddev[i]; }
    public double p50(int i) { return p50[i]; }
    public double p95(int i) { return p95[i]; }

    public long storageEstimate() {
        return featureCount * 9 * 8 + 64;
    }

    public static class Builder {
        private String label;
        private int originalCount;
        private long minTimestamp;
        private long maxTimestamp;
        private final List<Double> meanList = new ArrayList<>();
        private final List<Double> stddevList = new ArrayList<>();
        private final List<Double> p50List = new ArrayList<>();
        private final List<Double> p95List = new ArrayList<>();

        public Builder label(String v) { this.label = v; return this; }
        public Builder originalCount(int v) { this.originalCount = v; return this; }
        public Builder minTimestamp(long v) { this.minTimestamp = v; return this; }
        public Builder maxTimestamp(long v) { this.maxTimestamp = v; return this; }

        public Builder addFeature(double mean, double stddev, double p50, double p95) {
            meanList.add(mean);
            stddevList.add(stddev);
            p50List.add(p50);
            p95List.add(p95);
            return this;
        }

        public FingerprintSummary build() {
            this.mean = meanList.stream().mapToDouble(Double::doubleValue).toArray();
            this.stddev = stddevList.stream().mapToDouble(Double::doubleValue).toArray();
            this.p50 = p50List.stream().mapToDouble(Double::doubleValue).toArray();
            this.p95 = p95List.stream().mapToDouble(Double::doubleValue).toArray();
            return new FingerprintSummary(this);
        }

        private double[] mean, stddev, p50, p95;
    }
}
