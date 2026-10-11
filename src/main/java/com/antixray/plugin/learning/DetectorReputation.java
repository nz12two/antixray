package com.antixray.plugin.learning;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DetectorReputation {
    private final Map<String, DetectorStats> stats = new ConcurrentHashMap<>();

    private static class DetectorStats {
        final AtomicInteger totalFlags = new AtomicInteger();
        final AtomicInteger confirmedPositives = new AtomicInteger();

        double consistency() {
            int total = totalFlags.get();
            int confirmed = confirmedPositives.get();
            if (total == 0) return 0.5;
            return (double) confirmed / total;
        }
    }

    public void recordFlag(String detectorName) {
        stats.computeIfAbsent(detectorName, k -> new DetectorStats())
                .totalFlags.incrementAndGet();
    }

    public void recordConfirmation(String detectorName) {
        stats.computeIfAbsent(detectorName, k -> new DetectorStats())
                .confirmedPositives.incrementAndGet();
    }

    public double consistency(String detectorName) {
        DetectorStats s = stats.get(detectorName);
        return s != null ? s.consistency() : 0.5;
    }

    public double weightMultiplier(String detectorName) {
        double c = consistency(detectorName);
        if (c >= 0.8) return 1.2;
        if (c >= 0.6) return 1.0;
        if (c >= 0.4) return 0.7;
        return 0.4;
    }

    public boolean isReliable(String detectorName) {
        return consistency(detectorName) >= 0.6;
    }

    public int totalFlags(String detectorName) {
        DetectorStats s = stats.get(detectorName);
        return s != null ? s.totalFlags.get() : 0;
    }

    public Map<String, Double> allConsistencies() {
        Map<String, Double> result = new ConcurrentHashMap<>();
        for (var entry : stats.entrySet()) {
            result.put(entry.getKey(), entry.getValue().consistency());
        }
        return result;
    }
}
