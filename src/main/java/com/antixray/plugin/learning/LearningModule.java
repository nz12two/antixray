package com.antixray.plugin.learning;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.model.MiningEvent;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

public class LearningModule extends Module {
    private final AntiXrayPlugin plugin;
    private ServerProfile serverProfile;
    private WorldProfile worldProfile;
    private BukkitTask taskId;

    public LearningModule(AntiXrayPlugin plugin) {
        super("learning");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.serverProfile = new ServerProfile();
        this.worldProfile = new WorldProfile();
        plugin.getServiceRegistry().register(ServerProfile.class, serverProfile);
        plugin.getServiceRegistry().register(WorldProfile.class, worldProfile);

        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        eventBus.register(MiningEvent.class, this::onMiningEvent);

        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::recalibrate, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (taskId != null) taskId.cancel();
    }

    private void onMiningEvent(MiningEvent event) {
        if (isOre(event.getBlockType())) {
            serverProfile.recordOreDiscovery(event);
        }
        serverProfile.recordBlockBreak(event);

        if (event.getLocation().getWorld() != null) {
            String worldName = event.getLocation().getWorld().getName();
            worldProfile.recordBlockBreak(worldName, event.getTimestamp());
            if (isOre(event.getBlockType())) {
                worldProfile.recordOreDiscovery(worldName, event.isHiddenOre(), event.getTimestamp());
            }
        }
    }

    private void recalibrate() {
        serverProfile.updateAverages();
        worldProfile.updateAverages();

        long totalBlocks = serverProfile.getTotalBlocksTracked();
        if (totalBlocks < 1000) return;
    }

    public ServerProfile getServerProfile() { return serverProfile; }
    public WorldProfile getWorldProfile() { return worldProfile; }

    private boolean isOre(org.bukkit.Material material) {
        return material.name().endsWith("_ORE");
    }

    public static class ServerProfile {
        private long totalBlocksTracked;
        private long totalOresTracked;
        private long totalHiddenOre;
        private double averageBlocksPerMinute;
        private double averageOreRate;
        private double averageHiddenOreRatio;
        private double blocksPerMinuteAlpha = 0.05;
        private double oreRateAlpha = 0.05;
        private long sessionBlockCount;
        private long sessionOreCount;
        private long sessionStartTime;

        public ServerProfile() {
            this.sessionStartTime = System.currentTimeMillis();
        }

        public void recordBlockBreak(MiningEvent event) {
            totalBlocksTracked++;
            sessionBlockCount++;
        }

        public void recordOreDiscovery(MiningEvent event) {
            totalOresTracked++;
            sessionOreCount++;
            if (event.isHiddenOre()) {
                totalHiddenOre++;
            }
        }

        public void updateAverages() {
            long now = System.currentTimeMillis();
            double sessionMinutes = Math.max(1, (now - sessionStartTime) / 60_000.0);

            double currentBlocksPerMin = sessionBlockCount / sessionMinutes;
            double currentOreRate = totalOresTracked > 0
                    ? (double) sessionOreCount / sessionBlockCount : 0;
            double currentHiddenRatio = totalOresTracked > 0
                    ? (double) totalHiddenOre / totalOresTracked : 0;

            averageBlocksPerMinute = exponentialMovingAverage(averageBlocksPerMinute, currentBlocksPerMin, blocksPerMinuteAlpha);
            averageOreRate = exponentialMovingAverage(averageOreRate, currentOreRate, oreRateAlpha);
            averageHiddenOreRatio = exponentialMovingAverage(averageHiddenOreRatio, currentHiddenRatio, 0.1);
        }

        private double exponentialMovingAverage(double current, double newValue, double alpha) {
            if (current == 0) return newValue;
            return current * (1 - alpha) + newValue * alpha;
        }

        public void resetAverages() {
            averageBlocksPerMinute = 0;
            averageOreRate = 0;
            averageHiddenOreRatio = 0;
            sessionBlockCount = 0;
            sessionOreCount = 0;
            sessionStartTime = System.currentTimeMillis();
        }

        public long getTotalBlocksTracked() { return totalBlocksTracked; }
        public double getAverageBlocksPerMinute() { return averageBlocksPerMinute; }
        public double getAverageOreRate() { return averageOreRate; }
        public double getAverageHiddenOreRatio() { return averageHiddenOreRatio; }
    }
}
