package com.antixray.plugin.autovalidation;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.detection.DetectionModule;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.model.PlayerDataManager;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoValidationModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<String, DetectorStats> detectorStats = new ConcurrentHashMap<>();
    private BukkitTask taskId;
    private static final double WARNING_THRESHOLD = 0.90;

    public AutoValidationModule(AntiXrayPlugin plugin) {
        super("autovalidation");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getServiceRegistry().register(AutoValidationModule.class, this);
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::validate, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (taskId != null) taskId.cancel();
        detectorStats.clear();
    }

    public void recordDetection(String detectorName, UUID playerId) {
        detectorStats.computeIfAbsent(detectorName, DetectorStats::new)
                .uniquePlayers.add(playerId);
    }

    private void validate() {
        DetectionModule dm = plugin.getModuleManager().getModule(DetectionModule.class);
        if (dm == null) return;

        PlayerDataManager pdm = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        int totalTracked = Math.max(1, pdm.size());

        for (Detector detector : dm.getDetectors()) {
            DetectorStats stats = detectorStats.get(detector.getName());
            if (stats == null || stats.uniquePlayers.size() < 5) continue;

            double coverage = (double) stats.uniquePlayers.size() / totalTracked;

            if (coverage >= WARNING_THRESHOLD) {
                plugin.getLogger().warning("[AutoValidation] " + detector.getName()
                        + " flagged " + String.format("%.0f%%", coverage * 100)
                        + " of players — possible false positive spiral!");
            }

            if (stats.uniquePlayers.size() > 10) {
                stats.uniquePlayers.clear();
            }
        }
    }

    public Map<String, DetectorStats> getAllStats() {
        return Collections.unmodifiableMap(detectorStats);
    }

    public static class DetectorStats {
        public final String detectorName;
        public final Set<UUID> uniquePlayers = ConcurrentHashMap.newKeySet();

        public DetectorStats(String name) { this.detectorName = name; }
    }
}
