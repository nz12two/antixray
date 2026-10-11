package com.antixray.plugin.learning;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.api.events.PlayerFlaggedEvent;
import com.antixray.plugin.engine.EngineModule;
import com.antixray.plugin.engine.EngineModule.EngineData;
import com.antixray.plugin.learning.Fingerprint.Label;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import com.antixray.plugin.learning.SimilarityEngine.SimilarityResult;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HistoricalLearningModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<UUID, Fingerprint> sessionFingerprints = new ConcurrentHashMap<>();
    private final RiskHistory riskHistory = new RiskHistory();
    private final DetectorConsensus detectorConsensus = new DetectorConsensus();
    private final DetectorReputation detectorReputation = new DetectorReputation();
    private final MiningStyle.Classifier styleClassifier = new MiningStyle.Classifier();
    private final FingerprintStability stability = new FingerprintStability();

    private PlayerDataManager dataManager;
    private EngineModule engineModule;
    private FingerprintEngine fingerprintEngine;
    private ThreeTierStore store;
    private BukkitTask taskId;

    public HistoricalLearningModule(AntiXrayPlugin plugin) {
        super("historicallearning");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        this.engineModule = plugin.getModuleManager().getModule(EngineModule.class);
        this.fingerprintEngine = new FingerprintEngine();
        this.store = new ThreeTierStore();

        plugin.getServiceRegistry().register(HistoricalLearningModule.class, this);
        plugin.getServiceRegistry().register(ThreeTierStore.class, store);
        plugin.getServiceRegistry().register(RiskHistory.class, riskHistory);
        plugin.getServiceRegistry().register(DetectorConsensus.class, detectorConsensus);
        plugin.getServiceRegistry().register(DetectorReputation.class, detectorReputation);

        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        eventBus.register(PlayerFlaggedEvent.class, this::onFlagged);

        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 12000L, 12000L);
    }

    @Override
    public void onDisable() {
        if (taskId != null) taskId.cancel();
        sessionFingerprints.clear();
    }

    public Fingerprint recordSession(UUID playerId) {
        PlayerData data = dataManager.get(playerId);
        if (data == null || data.getRecentEvents().size() < 20) return null;

        EngineData ed = engineModule != null ? engineModule.get(playerId) : null;
        UUID sessionId = UUID.randomUUID();
        Fingerprint fp = fingerprintEngine.generate(data, ed, sessionId);
        sessionFingerprints.put(sessionId, fp);

        double risk = ed != null ? ed.getCombinedAnomalyScore() : 50;
        riskHistory.record(playerId, risk, fp.detectorScores());

        MiningStyle.StyleResult style = styleClassifier.classify(fp);
        if (style.isLegitimate() && style.similarity() > 0.6) {
            return fp;
        }

        ThreeTierStore.AdmitResult result = store.admit(fp, risk, riskHistory,
                detectorConsensus, detectorReputation);

        if (result == ThreeTierStore.AdmitResult.ACCEPTED_CANDIDATE) {
            plugin.getLogger().fine("[Fingerprint] Candidate saved for " + playerId);
        }

        return fp;
    }

    private void onFlagged(PlayerFlaggedEvent event) {
        Fingerprint fp = recordSession(event.getPlayerId());
        if (fp == null) return;

        for (var entry : fp.detectorScores().entrySet()) {
            if (entry.getValue() >= 50) {
                detectorReputation.recordFlag(entry.getKey());
            }
        }
    }

    public double getAdjustedRisk(double baseRisk, UUID playerId) {
        Fingerprint fp = recordSession(playerId);
        if (fp == null) return baseRisk;

        MiningStyle.StyleResult style = styleClassifier.classify(fp);
        double styleReduction = style.isLegitimate() ? style.riskReduction() * baseRisk : 0;

        double posSimilarity = store.bestPositiveSimilarity(fp);
        double negSimilarity = store.bestNegativeSimilarity(fp);

        double posBoost = posSimilarity * 40;
        double negPenalty = negSimilarity * 30;

        double risk = baseRisk + posBoost - negPenalty - styleReduction;
        return Math.max(0, Math.min(100, risk));
    }

    public void labelPositive(UUID playerId) {
        store.labelPlayerFingerprints(playerId, Label.POSITIVE);

        for (Fingerprint fp : sessionFingerprints.values()) {
            if (fp.playerId().equals(playerId)) {
                for (var entry : fp.detectorScores().entrySet()) {
                    if (entry.getValue() >= 50) {
                        detectorReputation.recordConfirmation(entry.getKey());
                    }
                }
            }
        }

        plugin.getLogger().info("[Historical] Labeled player " + playerId + " as POSITIVE");
    }

    public void labelNegative(UUID playerId) {
        store.labelPlayerFingerprints(playerId, Label.NEGATIVE);
        plugin.getLogger().info("[Historical] Labeled player " + playerId + " as NEGATIVE");
    }

    public void recordNegative(Fingerprint fp) {
        store.admit(fp.withLabel(Label.NEGATIVE), 95, riskHistory,
                detectorConsensus, detectorReputation);
    }

    public SimilarityResult lastComparison(UUID playerId) {
        Fingerprint fp = sessionFingerprints.values().stream()
                .filter(f -> f.playerId().equals(playerId))
                .reduce((first, second) -> second).orElse(null);
        if (fp == null) return null;

        double posSim = store.bestPositiveSimilarity(fp);
        double negSim = store.bestNegativeSimilarity(fp);

        MiningStyle.StyleResult style = styleClassifier.classify(fp);

        List<SimilarityEngine.FeatureContribution> contribs = new ArrayList<>();
        contribs.add(new SimilarityEngine.FeatureContribution("HistoryMatch", posSim));
        contribs.add(new SimilarityEngine.FeatureContribution("Style:" + style.style().displayName(), style.similarity()));

        return new SimilarityResult(posSim, posSim, negSim, style.similarity(), contribs);
    }

    public String explainFull(UUID playerId) {
        Fingerprint fp = sessionFingerprints.values().stream()
                .filter(f -> f.playerId().equals(playerId))
                .reduce((first, second) -> second).orElse(null);
        if (fp == null) return "§7No fingerprint data";

        StringBuilder sb = new StringBuilder();
        sb.append("§8=== §6Fingerprint Analysis §8===\n");

        MiningStyle.StyleResult style = styleClassifier.classify(fp);
        sb.append(style.explain()).append("\n");

        FingerprintStability.StabilityResult stable = stability.evaluate(
                sessionFingerprints.values().stream()
                        .filter(f -> f.playerId().equals(playerId))
                        .toList());
        sb.append(stable.explain()).append("\n");

        double posSim = store.bestPositiveSimilarity(fp);
        double negSim = store.bestNegativeSimilarity(fp);
        sb.append(String.format("§8PosMatch: §a%.0f%%§8 | NegMatch: §c%.0f%%\n", posSim * 100, negSim * 100));

        DetectorConsensus.ConsensusResult cr = detectorConsensus.evaluate(fp.detectorScores(), 3);
        sb.append(String.format("§8Detectors: §e%d§8 | Categories: §b%d§8 | Diversity: %s\n",
                cr.detectorCount(), cr.categoryCount(),
                cr.hasDiversity() ? "§a✔" : "§c✘"));

        int samples = riskHistory.sampleCount(playerId);
        double sustained = riskHistory.isSustainedAbove(playerId, 70, 600_000) ? 1 : 0;
        sb.append(String.format("§8Samples: §e%d§8 | Sustained70: %s\n",
                samples, sustained > 0 ? "§a✔" : "§7✘"));

        sb.append("§8StyleReduction: ").append(String.format("§a-%.0f%%", style.riskReduction() * 100)).append("\n");
        sb.append("§8" + store.storageReport());

        return sb.toString();
    }

    private void cleanup() {
        store.processQuarantine(riskHistory);

        long cutoff = System.currentTimeMillis() - 7_200_000;
        sessionFingerprints.entrySet().removeIf(e -> e.getValue().sessionEnd() < cutoff);

        plugin.getLogger().info("[Historical] " + store.storageReport());
    }

    public int getPositiveCount() { return store.verifiedCount(); }
    public int getNegativeCount() { return store.referenceCount(); }
    public int getSessionCount() { return sessionFingerprints.size(); }
    public ThreeTierStore getStore() { return store; }
    public RiskHistory getRiskHistory() { return riskHistory; }
    public DetectorReputation getDetectorReputation() { return detectorReputation; }
    public MiningStyle.Classifier getStyleClassifier() { return styleClassifier; }
}
