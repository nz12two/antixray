package com.antixray.plugin.drift;

import java.util.ArrayDeque;
import java.util.Deque;

public class DriftDetector {
    private final Deque<Double> window;
    private final int maxWindowSize;
    private final double threshold;
    private double mean;
    private double cumulativeSum;
    private boolean drifted;

    public DriftDetector(int maxWindowSize, double threshold) {
        this.window = new ArrayDeque<>();
        this.maxWindowSize = maxWindowSize;
        this.threshold = threshold;
        this.mean = 0;
        this.cumulativeSum = 0;
        this.drifted = false;
    }

    public void addValue(double value) {
        window.addLast(value);
        if (window.size() > maxWindowSize) {
            window.removeFirst();
        }
        recomputeMean();
        checkDrift(value);
    }

    private void recomputeMean() {
        if (window.isEmpty()) return;
        mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private void checkDrift(double value) {
        double delta = value - mean;
        cumulativeSum += delta;

        double max = threshold * mean;
        if (cumulativeSum > max || cumulativeSum < -max) {
            drifted = true;
            cumulativeSum = 0;
            window.clear();
        }
    }

    public boolean hasDrifted() {
        if (drifted) {
            drifted = false;
            return true;
        }
        return false;
    }

    public double getMean() { return mean; }
    public void reset() {
        window.clear();
        cumulativeSum = 0;
        drifted = false;
        mean = 0;
    }
}
