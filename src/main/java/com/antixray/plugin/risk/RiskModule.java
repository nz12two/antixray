package com.antixray.plugin.risk;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.config.ConfigLoader.DetectorConfig;
import com.antixray.plugin.detection.result.DetectionBatch;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import com.antixray.plugin.pipeline.DetectorPipeline;
import com.antixray.plugin.profile.PlayerProfileModule;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class RiskModule extends Module {
    private final AntiXrayPlugin plugin;
    private PlayerDataManager dataManager;
    private EventBus eventBus;
    private PlayerProfileModule profileModule;
    private TemporalRiskEngine temporalEngine;
    private DetectorPipeline pipeline;

    public RiskModule(AntiXrayPlugin plugin) {
        super("risk");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        this.eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        this.profileModule = plugin.getModuleManager().getModule(PlayerProfileModule.class);

        FileConfiguration trConfig = plugin.getConfig();
        double decayFactor = trConfig.getDouble("temporal-risk.decay-factor", 0.9);
        double histW = trConfig.getDouble("temporal-risk.historical-weight", 0.2);
        double prevW = trConfig.getDouble("temporal-risk.previous-weight", 0.3);
        double currW = trConfig.getDouble("temporal-risk.current-weight", 0.5);
        long decayInterval = trConfig.getLong("decay.interval-minutes", 3) * 60 * 1000L;
        this.temporalEngine = new TemporalRiskEngine(decayFactor, histW, prevW, currW, decayInterval);

        this.pipeline = plugin.getDetectionModule().getPipeline();

        eventBus.register(DetectionBatch.class, this::onDetectionBatch);
    }

    @Override
    public void onDisable() {}

    private void onDetectionBatch(DetectionBatch batch) {
        PlayerData data = dataManager.get(batch.getPlayerId());
        if (data == null) return;

        Map<String, Double> weights = new HashMap<>();
        for (DetectionResult r : batch.getResults()) {
            DetectorConfig config = plugin.getConfigLoader().getDetectorConfig(r.getDetectorName());
            weights.put(r.getDetectorName().toLowerCase(), config.weight);
        }

        // Probabilistic score: 1 - Π(1 - Pi * wi)
        double currentProbScore = computeProbabilisticScore(batch.getResults(), weights);

        double temporalRisk = temporalEngine.compute(
                data.getPlayerId(), data, batch.getResults(), currentProbScore, weights);

        data.getRiskProfile().setCurrentRiskScore(temporalRisk);
        data.getRiskProfile().setPeakRiskScore(
                Math.max(data.getRiskProfile().getPeakRiskScore(), temporalRisk));
        data.getRiskProfile().setLastFlagTime(System.currentTimeMillis());

        pipeline.updateRiskCache(data.getPlayerId(), temporalRisk);

        double alertThreshold = plugin.getConfigLoader().getAlertThreshold();
        double flagThreshold = plugin.getConfigLoader().getFlagThreshold();
        double punishThreshold = plugin.getConfigLoader().getPunishThreshold();

        if (temporalRisk >= punishThreshold) {
            data.getRiskProfile().incrementFlags();
            if (profileModule != null) profileModule.adjustForFlags(data.getPlayerId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(
                        new com.antixray.plugin.api.events.PlayerRiskUpdateEvent(
                                data.getPlayerId(), data.getPlayerName(), temporalRisk, getScoreMap(batch)));
                Bukkit.getPluginManager().callEvent(
                        new com.antixray.plugin.api.events.PlayerFlaggedEvent(
                                data.getPlayerId(), data.getPlayerName(), temporalRisk, batch.getResults()));
            });
        } else if (temporalRisk >= flagThreshold) {
            data.getRiskProfile().incrementFlags();
            if (profileModule != null) profileModule.adjustForFlags(data.getPlayerId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(
                        new com.antixray.plugin.api.events.PlayerRiskUpdateEvent(
                                data.getPlayerId(), data.getPlayerName(), temporalRisk, getScoreMap(batch)));
            });
        } else if (temporalRisk >= alertThreshold) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(
                        new com.antixray.plugin.api.events.PlayerRiskUpdateEvent(
                                data.getPlayerId(), data.getPlayerName(), temporalRisk, getScoreMap(batch)));
            });
        }
    }

    private double computeProbabilisticScore(List<DetectionResult> results, Map<String, Double> weights) {
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight == 0) return 0;

        double product = 1.0;
        for (DetectionResult r : results) {
            double w = weights.getOrDefault(r.getDetectorName().toLowerCase(), 10.0);
            double normalizedWeight = w / totalWeight;
            double probability = (r.getScore() / 100.0) * (r.getConfidence() / 100.0);
            product *= (1.0 - probability * normalizedWeight);
        }

        if (product <= 0 || Double.isNaN(product) || Double.isInfinite(product)) {
            return 0.0;
        }

        return (1.0 - product) * 100;
    }

    private Map<String, Double> getScoreMap(DetectionBatch batch) {
        Map<String, Double> map = new HashMap<>();
        for (DetectionResult r : batch.getResults()) {
            map.put(r.getDetectorName(), r.getScore());
        }
        return map;
    }
}
