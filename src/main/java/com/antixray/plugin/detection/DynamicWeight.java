package com.antixray.plugin.detection;

import com.antixray.plugin.detection.result.DetectionBatch;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.profiler.DetectorProfiler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicWeight {
    private final Map<String, DetectorWeight> weights = new ConcurrentHashMap<>();
    private final DetectorProfiler profiler;
    private static final double RELIABILITY_ALPHA = 0.1;
    private static final double MIN_EFFECTIVE = 0.1;

    public DynamicWeight(DetectorProfiler profiler) {
        this.profiler = profiler;
    }

    public void recordDetection(String detectorName, DetectionBatch batch) {
        DetectorWeight dw = weights.computeIfAbsent(detectorName, DetectorWeight::new);
        dw.totalDetections++;

        boolean coFired = false;
        int otherTriggers = 0;
        for (DetectionResult r : batch.getResults()) {
            if (!r.getDetectorName().equals(detectorName)) {
                otherTriggers++;
                if (r.getScore() > 0) coFired = true;
            }
        }

        if (otherTriggers > 0) {
            double newReliability = coFired
                    ? dw.reliability + RELIABILITY_ALPHA * (1.0 - dw.reliability)
                    : dw.reliability * (1.0 - RELIABILITY_ALPHA);
            dw.reliability = Math.max(0.1, Math.min(1.0, newReliability));
        }

        dw.accuracy = 1.0 - (dw.falsePositives / Math.max(1, dw.totalDetections));
    }

    public void recordFalsePositive(String detectorName) {
        DetectorWeight dw = weights.computeIfAbsent(detectorName, DetectorWeight::new);
        dw.falsePositives++;
        dw.accuracy = 1.0 - (dw.falsePositives / Math.max(1, dw.totalDetections));

        var snap = profiler.getSnapshot(detectorName);
        if (snap != null && detectorName != null) {
            profiler.recordFalsePositive(detectorName);
        }
    }

    public double getEffectiveWeight(String detectorName, double configWeight) {
        DetectorWeight dw = weights.get(detectorName);
        if (dw == null) return configWeight;
        return Math.max(MIN_EFFECTIVE, configWeight * dw.reliability * dw.accuracy);
    }

    public DetectorWeight getStats(String detectorName) {
        return weights.get(detectorName);
    }

    public Map<String, DetectorWeight> getAll() {
        return Collections.unmodifiableMap(weights);
    }

    public void reset() { weights.clear(); }

    public static class DetectorWeight {
        public final String detectorName;
        public double reliability = 1.0;
        public double accuracy = 1.0;
        public double falsePositives;
        public double totalDetections;

        public DetectorWeight(String name) { this.detectorName = name; }
    }
}
