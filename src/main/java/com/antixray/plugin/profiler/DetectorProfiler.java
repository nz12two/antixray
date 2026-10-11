package com.antixray.plugin.profiler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DetectorProfiler {
    private final Map<String, ProfileEntry> profiles = new ConcurrentHashMap<>();

    public void record(String detectorName, long executionTimeNanos) {
        profiles.compute(detectorName, (k, v) -> {
            if (v == null) v = new ProfileEntry();
            v.totalTimeNanos += executionTimeNanos;
            v.executionCount.incrementAndGet();
            v.maxTimeNanos = Math.max(v.maxTimeNanos, executionTimeNanos);
            v.minTimeNanos = v.executionCount.get() == 1 ? executionTimeNanos : Math.min(v.minTimeNanos, executionTimeNanos);
            return v;
        });
    }

    public void recordMemory(String detectorName, long memoryBytes) {
        ProfileEntry entry = profiles.get(detectorName);
        if (entry != null) {
            entry.maxMemoryBytes = Math.max(entry.maxMemoryBytes, memoryBytes);
            entry.totalMemoryBytes.addAndGet(memoryBytes);
        }
    }

    public void recordFlag(String detectorName) {
        ProfileEntry entry = profiles.computeIfAbsent(detectorName, k -> new ProfileEntry());
        entry.flagsGenerated.incrementAndGet();
        entry.totalScoreAccumulator.addAndGet(100);
    }

    public void recordFalsePositive(String detectorName) {
        ProfileEntry entry = profiles.computeIfAbsent(detectorName, k -> new ProfileEntry());
        entry.falsePositives.incrementAndGet();
    }

    public void recordScore(String detectorName, double score) {
        ProfileEntry entry = profiles.computeIfAbsent(detectorName, k -> new ProfileEntry());
        entry.totalScoreAccumulator.addAndGet((int) Math.round(score));
    }

    public ProfileSnapshot getSnapshot(String detectorName) {
        ProfileEntry e = profiles.get(detectorName);
        if (e == null) return null;
        return buildSnapshot(detectorName, e);
    }

    public List<ProfileSnapshot> getAllSnapshots() {
        List<ProfileSnapshot> list = new ArrayList<>();
        for (Map.Entry<String, ProfileEntry> e : profiles.entrySet()) {
            list.add(buildSnapshot(e.getKey(), e.getValue()));
        }
        list.sort(Comparator.comparingLong(ProfileSnapshot::averageTimeNanos).reversed());
        return list;
    }

    public void reset() { profiles.clear(); }

    public void reset(String detectorName) { profiles.remove(detectorName); }

    private ProfileSnapshot buildSnapshot(String name, ProfileEntry e) {
        int execCount = e.executionCount.get();
        double fpRate = execCount > 0 ? (double) e.falsePositives.get() / execCount : 0;
        return new ProfileSnapshot(
                name,
                execCount > 0 ? e.totalTimeNanos / execCount : 0,
                e.maxTimeNanos,
                e.minTimeNanos,
                execCount,
                e.maxMemoryBytes,
                e.flagsGenerated.get(),
                e.falsePositives.get(),
                fpRate,
                e.totalMemoryBytes.get()
        );
    }

    private static class ProfileEntry {
        final AtomicInteger executionCount = new AtomicInteger();
        final AtomicInteger flagsGenerated = new AtomicInteger();
        final AtomicInteger falsePositives = new AtomicInteger();
        final AtomicLong totalScoreAccumulator = new AtomicLong();
        final AtomicLong totalMemoryBytes = new AtomicLong();
        long totalTimeNanos;
        long maxTimeNanos;
        long minTimeNanos = Long.MAX_VALUE;
        long maxMemoryBytes;
    }

    public record ProfileSnapshot(
            String detectorName,
            long averageTimeNanos,
            long maxTimeNanos,
            long minTimeNanos,
            int executionCount,
            long maxMemoryBytes,
            int flagsGenerated,
            int falsePositives,
            double falsePositiveRate,
            long totalMemoryBytes
    ) {
        public double averageTimeMs() { return averageTimeNanos / 1_000_000.0; }
        public double maxTimeMs() { return maxTimeNanos / 1_000_000.0; }
        public double minTimeMs() { return minTimeNanos == Long.MAX_VALUE ? 0 : minTimeNanos / 1_000_000.0; }
        public double averageMemoryKb() { return totalMemoryBytes / 1024.0; }

        public String formatted() {
            return String.format("%-22s | avg=%.2fms max=%.2fms count=%d flags=%d fp=%.1f%% mem=%dKB",
                    detectorName, averageTimeMs(), maxTimeMs(), executionCount,
                    flagsGenerated, falsePositiveRate * 100, maxMemoryBytes / 1024);
        }
    }
}
