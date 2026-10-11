package com.antixray.plugin.learning;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RiskHistory {
    private final Map<UUID, List<RiskSample>> history = new ConcurrentHashMap<>();
    private final long windowMs;
    private final int maxSamples;

    public record RiskSample(double risk, long timestamp, Map<String, Double> detectorScores) {}

    public RiskHistory() {
        this(3_600_000, 360);
    }

    public RiskHistory(long windowMs, int maxSamples) {
        this.windowMs = windowMs;
        this.maxSamples = maxSamples;
    }

    public void record(UUID playerId, double risk, Map<String, Double> detectors) {
        List<RiskSample> samples = history.computeIfAbsent(playerId, k ->
                Collections.synchronizedList(new ArrayList<>()));
        samples.add(new RiskSample(risk, System.currentTimeMillis(), detectors));
        trim(playerId);
    }

    private void trim(UUID playerId) {
        List<RiskSample> samples = history.get(playerId);
        if (samples == null) return;
        long cutoff = System.currentTimeMillis() - windowMs;
        samples.removeIf(s -> s.timestamp() < cutoff);
        while (samples.size() > maxSamples) samples.remove(0);
    }

    public boolean isSustainedAbove(UUID playerId, double minRisk, long durationMs) {
        List<RiskSample> samples = getWindow(playerId, durationMs);
        if (samples.size() < 3) return false;

        long now = System.currentTimeMillis();
        long windowStart = now - durationMs;
        List<RiskSample> inWindow = samples.stream()
                .filter(s -> s.timestamp() >= windowStart)
                .toList();

        if (inWindow.isEmpty()) return false;

        long aboveCount = inWindow.stream().filter(s -> s.risk() >= minRisk).count();
        return (double) aboveCount / inWindow.size() >= 0.7;
    }

    public double stability(UUID playerId) {
        List<RiskSample> samples = getWindow(playerId, 3_600_000);
        if (samples.size() < 3) return 1.0;

        double mean = samples.stream().mapToDouble(RiskSample::risk).average().orElse(0);
        double variance = samples.stream()
                .mapToDouble(s -> Math.pow(s.risk() - mean, 2))
                .average().orElse(0);
        double stddev = Math.sqrt(variance);

        return Math.max(0, 1.0 - stddev / 100.0);
    }

    public double peakRatio(UUID playerId) {
        List<RiskSample> samples = getWindow(playerId, 600_000);
        if (samples.size() < 2) return 1.0;

        double max = samples.stream().mapToDouble(RiskSample::risk).max().orElse(0);
        double min = samples.stream().mapToDouble(RiskSample::risk).min().orElse(0);
        if (max == 0) return 1.0;
        return 1.0 - (max - min) / max;
    }

    public List<RiskSample> getLast(UUID playerId, int count) {
        List<RiskSample> samples = history.get(playerId);
        if (samples == null) return List.of();
        synchronized (samples) {
            return samples.stream()
                    .skip(Math.max(0, samples.size() - count))
                    .toList();
        }
    }

    private List<RiskSample> getWindow(UUID playerId, long durationMs) {
        List<RiskSample> samples = history.get(playerId);
        if (samples == null) return List.of();
        long cutoff = System.currentTimeMillis() - Math.max(durationMs, windowMs);
        synchronized (samples) {
            return samples.stream()
                    .filter(s -> s.timestamp() >= cutoff)
                    .toList();
        }
    }

    public boolean hasHighConsistency(UUID playerId, int lastN, double minSimilarity, SimilarityEngine engine) {
        List<RiskSample> samples = getLast(playerId, lastN);
        if (samples.size() < 2) return false;

        for (int i = 0; i < samples.size() - 1; i++) {
            Map<String, Double> a = samples.get(i).detectorScores();
            Map<String, Double> b = samples.get(i + 1).detectorScores();
            double sim = detectorProfileSimilarity(a, b);
            if (sim < minSimilarity) return false;
        }
        return true;
    }

    private double detectorProfileSimilarity(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int match = 0, total = 0;
        for (var entry : a.entrySet()) {
            Double vb = b.get(entry.getKey());
            if (vb != null) {
                total++;
                if (Math.abs(entry.getValue() - vb) < 15) match++;
            }
        }
        return total > 0 ? (double) match / total : 0;
    }

    public void clear(UUID playerId) {
        history.remove(playerId);
    }

    public int sampleCount(UUID playerId) {
        List<RiskSample> samples = history.get(playerId);
        return samples != null ? samples.size() : 0;
    }

    public Map<UUID, Integer> allSampleCounts() {
        return history.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
    }
}
