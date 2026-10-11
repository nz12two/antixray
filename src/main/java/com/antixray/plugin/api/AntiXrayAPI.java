package com.antixray.plugin.api;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.DetectionModule;
import com.antixray.plugin.evidence.Evidence;
import com.antixray.plugin.evidence.EvidenceModule;
import com.antixray.plugin.learning.LearningModule;
import com.antixray.plugin.learning.LearningModule.ServerProfile;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;

import java.util.List;
import java.util.UUID;

public class AntiXrayAPI {
    private final PlayerDataManager dataManager;
    private final DetectionModule detectionModule;
    private final EvidenceModule evidenceModule;
    private final LearningModule learningModule;

    public AntiXrayAPI(AntiXrayPlugin plugin) {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        this.detectionModule = plugin.getModuleManager().getModule(DetectionModule.class);
        this.evidenceModule = plugin.getModuleManager().getModule(EvidenceModule.class);
        this.learningModule = plugin.getModuleManager().getModule(LearningModule.class);
    }

    public double getPlayerRiskScore(UUID playerId) {
        PlayerData data = dataManager.get(playerId);
        if (data == null) return 0;
        return data.getRiskProfile().getCurrentRiskScore();
    }

    public double getPlayerPeakRiskScore(UUID playerId) {
        PlayerData data = dataManager.get(playerId);
        if (data == null) return 0;
        return data.getRiskProfile().getPeakRiskScore();
    }

    public int getPlayerTotalFlags(UUID playerId) {
        PlayerData data = dataManager.get(playerId);
        if (data == null) return 0;
        return data.getRiskProfile().getTotalFlags();
    }

    public List<Evidence> getPlayerEvidence(UUID playerId) {
        return evidenceModule.getPlayerEvidence(playerId);
    }

    public List<Detector> getDetectors() {
        return detectionModule.getDetectors();
    }

    public <T extends Detector> T getDetector(Class<T> type) {
        return detectionModule.getDetector(type);
    }

    public double getServerAverageBlocksPerMinute() {
        ServerProfile profile = learningModule.getServerProfile();
        return profile.getAverageBlocksPerMinute();
    }

    public double getServerAverageOreRate() {
        ServerProfile profile = learningModule.getServerProfile();
        return profile.getAverageOreRate();
    }
}
