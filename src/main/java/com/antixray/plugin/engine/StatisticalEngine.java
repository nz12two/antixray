package com.antixray.plugin.engine;

import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.learning.LearningModule;
import com.antixray.plugin.learning.LearningModule.ServerProfile;

public class StatisticalEngine {
    private final LearningModule learningModule;

    public StatisticalEngine(LearningModule learningModule) {
        this.learningModule = learningModule;
    }

    public StatisticalSnapshot analyze(PlayerData data) {
        StatisticalSnapshot snap = new StatisticalSnapshot();
        ServerProfile server = learningModule != null ? learningModule.getServerProfile() : null;
        if (server == null) return snap;

        snap.serverBlocksPerMin = server.getAverageBlocksPerMinute();
        snap.serverOreRate = server.getAverageOreRate();
        snap.serverHiddenRatio = server.getAverageHiddenOreRatio();

        double playerBlocksPerMin = data.getStatistics().getBlocksPerMinute();
        double playerOresPerMin = data.getStatistics().getOresPerMinute();

        snap.blockRateZScore = computeZScore(playerBlocksPerMin, snap.serverBlocksPerMin,
                snap.serverBlocksPerMin * 0.3);
        snap.oreRateZScore = computeZScore(playerOresPerMin, snap.serverOreRate,
                snap.serverOreRate * 0.4);

        String worldName = data.getCurrentSession().getWorldName();
        if (worldName != null) {
            snap.worldOreRate = learningModule.getWorldProfile().getAverageOreRate(worldName);
            snap.worldBlocksPerMin = learningModule.getWorldProfile().getAverageBlocksPerMinute(worldName);
            snap.worldHiddenRatio = learningModule.getWorldProfile().getAverageHiddenRatio(worldName);
        }

        return snap;
    }

    private double computeZScore(double value, double mean, double std) {
        if (std <= 0) return 0;
        return (value - mean) / std;
    }

    public static class StatisticalSnapshot {
        public double serverBlocksPerMin;
        public double serverOreRate;
        public double serverHiddenRatio;
        public double blockRateZScore;
        public double oreRateZScore;
        public double worldOreRate;
        public double worldBlocksPerMin;
        public double worldHiddenRatio;

        public double getAnomalyScore() {
            double score = 0;
            if (oreRateZScore > 1.5) score += Math.min(100, oreRateZScore * 20);
            if (blockRateZScore > 2.0) score += Math.min(100, blockRateZScore * 15);
            return Math.min(100, score);
        }
    }
}
