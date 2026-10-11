package com.antixray.plugin.drift;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.learning.LearningModule;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DriftModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<String, DriftDetector> detectors = new ConcurrentHashMap<>();
    private LearningModule learningModule;
    private BukkitTask taskId;

    public DriftModule(AntiXrayPlugin plugin) {
        super("drift");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.learningModule = plugin.getModuleManager().getModule(LearningModule.class);
        plugin.getServiceRegistry().register(DriftModule.class, this);

        detectors.put("oreRate", new DriftDetector(50, 2.0));
        detectors.put("blocksPerMin", new DriftDetector(50, 2.0));
        detectors.put("hiddenRatio", new DriftDetector(50, 2.5));

        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkDrift, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (taskId != null) taskId.cancel();
        detectors.clear();
    }

    private void checkDrift() {
        if (learningModule == null) return;
        var profile = learningModule.getServerProfile();
        if (profile.getTotalBlocksTracked() < 2000) return;

        detectors.get("oreRate").addValue(profile.getAverageOreRate());
        detectors.get("blocksPerMin").addValue(profile.getAverageBlocksPerMinute());
        detectors.get("hiddenRatio").addValue(profile.getAverageHiddenOreRatio());

        boolean anyDrift = detectors.values().stream().anyMatch(DriftDetector::hasDrifted);
        if (anyDrift) {
            plugin.getLogger().info("[Drift] Server baseline shifted — recalibrating learning engine.");
            profile.resetAverages();
            detectors.values().forEach(DriftDetector::reset);
        }
    }
}
