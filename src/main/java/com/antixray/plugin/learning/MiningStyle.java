package com.antixray.plugin.learning;

public enum MiningStyle {
    CAVE_EXPLORER("Cave Explorer",
            0.3,  0.2,  0.1,  0.3,  0.8,  0.7,  0.2,  0.8,  0.3),
    BRANCH_MINER("Branch Miner",
            0.8,  0.3,  0.6,  0.9,  0.2,  0.3,  0.5,  0.3,  0.2),
    SPEEDRUNNER("Speedrunner",
            0.9,  0.7,  0.3,  0.5,  0.1,  0.2,  0.3,  0.2,  0.1),
    CASUAL("Casual Miner",
            0.2,  0.1,  0.1,  0.2,  0.4,  0.4,  0.2,  0.4,  0.2),
    OPTIMIZED("Optimized Miner",
            0.7,  0.5,  0.4,  0.6,  0.3,  0.4,  0.6,  0.4,  0.5);

    private final String displayName;
    private final double[] prototype;

    MiningStyle(String displayName,
                double blocksPerMin, double oresPerMin, double hiddenOreRatio,
                double tunnelStraightness, double caveExploration, double movementEntropy,
                double oreSpaceCorrelation, double explorationEfficiency, double avgYLevel) {
        this.displayName = displayName;
        this.prototype = new double[]{
                blocksPerMin / 200.0, oresPerMin / 10.0, hiddenOreRatio,
                tunnelStraightness, caveExploration, movementEntropy,
                oreSpaceCorrelation, explorationEfficiency, avgYLevel / 64.0
        };
    }

    public String displayName() { return displayName; }

    public double similarity(double[] vector) {
        double[] v = new double[Math.min(prototype.length, vector.length)];
        System.arraycopy(vector, 0, v, 0, v.length);

        double dot = 0, nA = 0, nB = 0;
        for (int i = 0; i < v.length; i++) {
            dot += prototype[i] * v[i];
            nA += prototype[i] * prototype[i];
            nB += v[i] * v[i];
        }
        double denom = Math.sqrt(nA) * Math.sqrt(nB);
        return denom == 0 ? 0 : dot / denom;
    }

    public static class Classifier {
        public StyleResult classify(Fingerprint fp) {
            double[] vec = fp.toVector();
            MiningStyle best = CASUAL;
            double bestSim = 0;

            for (MiningStyle style : values()) {
                double sim = style.similarity(vec);
                if (sim > bestSim) {
                    bestSim = sim;
                    best = style;
                }
            }

            return new StyleResult(best, bestSim);
        }

        public StyleResult classify(double[] vector) {
            MiningStyle best = CASUAL;
            double bestSim = 0;

            for (MiningStyle style : values()) {
                double sim = style.similarity(vector);
                if (sim > bestSim) {
                    bestSim = sim;
                    best = style;
                }
            }

            return new StyleResult(best, bestSim);
        }
    }

    public record StyleResult(MiningStyle style, double similarity) {
        public boolean isLegitimate() {
            return style != MiningStyle.SPEEDRUNNER;
        }

        public double riskReduction() {
            if (style == MiningStyle.CAVE_EXPLORER) return 0.3;
            if (style == MiningStyle.CASUAL) return 0.4;
            if (style == MiningStyle.OPTIMIZED) return 0.15;
            if (style == MiningStyle.BRANCH_MINER) return 0.1;
            return 0.0;
        }

        public String explain() {
            String reduction = String.format("§a-%.0f%%", riskReduction() * 100);
            return String.format("§8Style: §e%s §8(§b%.0f%%§8 match) %s",
                    style.displayName(), similarity * 100,
                    isLegitimate() ? reduction : "§c(suspicious)");
        }
    }
}
