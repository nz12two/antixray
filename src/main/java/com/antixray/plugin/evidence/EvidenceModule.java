package com.antixray.plugin.evidence;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.api.events.PlayerFlaggedEvent;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EvidenceModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<String, Evidence> evidenceStore = new ConcurrentHashMap<>();
    private PlayerDataManager dataManager;
    private EventBus eventBus;

    public EvidenceModule(AntiXrayPlugin plugin) {
        super("evidence");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        this.eventBus = plugin.getServiceRegistry().resolve(EventBus.class);

        eventBus.register(PlayerFlaggedEvent.class, this::onPlayerFlagged);
    }

    @Override
    public void onDisable() {
        evidenceStore.clear();
    }

    private void onPlayerFlagged(PlayerFlaggedEvent event) {
        PlayerData data = dataManager.get(event.getPlayerId());
        if (data == null) return;

        List<Location> path = new ArrayList<>(data.getMiningPath());
        List<Location> recentPath = path.subList(Math.max(0, path.size() - 50), path.size());

        Evidence.Builder builder = new Evidence.Builder()
                .playerId(event.getPlayerId())
                .playerName(event.getPlayerName())
                .timestamp(System.currentTimeMillis())
                .totalRiskScore(event.getRiskScore())
                .worldName(data.getCurrentSession().getWorldName())
                .sessionId(UUID.randomUUID().toString().substring(0, 8))
                .detailedReason(buildReason(event));

        for (DetectionResult result : event.getResults()) {
            builder.addDetectorScore(result.getDetectorName(), result.getScore());
            builder.addTriggeredDetector(result.getDetectorName());
        }

        for (Location loc : recentPath) {
            builder.addPathLocation(loc);
        }

        builder.addMetadata("total_blocks_mined", data.getTotalBlocksMined());
        builder.addMetadata("total_ores_found", data.getTotalOresFound());
        builder.addMetadata("session_time_ms", data.getCurrentSession().getTotalPlayTime());

        Evidence evidence = builder.build();
        evidenceStore.put(evidence.getId(), evidence);

        com.antixray.plugin.api.events.PlayerEvidenceCreateEvent evidenceEvent =
                new com.antixray.plugin.api.events.PlayerEvidenceCreateEvent(
                        event.getPlayerId(), event.getPlayerName(), evidence);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getPluginManager().callEvent(evidenceEvent);
        });
    }

    private String buildReason(PlayerFlaggedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Risk Score: ").append(String.format("%.0f", event.getRiskScore())).append("\n");
        sb.append("Triggered Detectors:\n");
        for (DetectionResult result : event.getResults()) {
            sb.append("  - ").append(result.getDetectorName())
                    .append(" [Score: ").append(String.format("%.0f", result.getScore()))
                    .append(", Confidence: ").append(String.format("%.0f", result.getConfidence()))
                    .append("]\n");
            sb.append("    Reason: ").append(result.getReason()).append("\n");
        }
        return sb.toString();
    }

    public Evidence getEvidence(String id) { return evidenceStore.get(id); }
    public List<Evidence> getPlayerEvidence(UUID playerId) {
        List<Evidence> result = new ArrayList<>();
        for (Evidence evidence : evidenceStore.values()) {
            if (evidence.getPlayerId().equals(playerId)) {
                result.add(evidence);
            }
        }
        return result;
    }
    public Collection<Evidence> getAllEvidence() { return evidenceStore.values(); }
}
