package com.antixray.plugin.statistics;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.MiningStatistics;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import com.antixray.plugin.statistics.calculators.*;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.*;

public class StatisticsModule extends Module {
    private final AntiXrayPlugin plugin;
    private PlayerDataManager dataManager;
    private final Map<UUID, List<Location>> tunnelCache = new HashMap<>();
    private final Map<UUID, Long> oreDiscoveryTimes = new HashMap<>();
    private final Map<UUID, Integer> dailyOreCount = new HashMap<>();
    private BukkitTask taskId;

    public StatisticsModule(AntiXrayPlugin plugin) {
        super("statistics");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);

        eventBus.register(MiningEvent.class, this::onMiningEvent);

        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::recalculateAll, 200L, 200L);
    }

    @Override
    public void onDisable() {
        if (taskId != null) taskId.cancel();
        tunnelCache.clear();
        oreDiscoveryTimes.clear();
        dailyOreCount.clear();
    }

    private void onMiningEvent(MiningEvent event) {
        PlayerData data = dataManager.get(event.getPlayerId());
        if (data == null) return;

        if (plugin.getConfigLoader().isOreRare(event.getBlockType().name())) {
            data.getCurrentSession().incrementOresFound();
            oreDiscoveryTimes.put(event.getPlayerId(), event.getTimestamp());
            dailyOreCount.merge(event.getPlayerId(), 1, Integer::sum);
        }
    }

    private void recalculateAll() {
        for (Map.Entry<UUID, PlayerData> entry : dataManager.getAll().entrySet()) {
            PlayerData data = entry.getValue();
            if (!data.isTracked()) continue;
            if (!data.getCurrentSession().isActive()) continue;
            try {
                recalculate(data);
            } catch (Exception ignored) {}
        }
    }

    public void recalculate(PlayerData data) {
        com.antixray.plugin.model.PlayerSession session = data.getCurrentSession();
        MiningStatistics stats = data.getStatistics();
        UUID playerId = data.getPlayerId();

        long sessionTimeMs = session.getTotalPlayTime();
        double sessionTimeMin = Math.max(1, sessionTimeMs / 60_000.0);

        stats.setBlocksPerMinute(session.getBlocksMined() / sessionTimeMin);
        stats.setOresPerMinute(session.getOresFound() / sessionTimeMin);

        if (sessionTimeMin >= 60) {
            stats.setDiamondsPerHour(dailyOreCount.getOrDefault(playerId, 0) / (sessionTimeMin / 60.0));
        }

        List<Location> path = data.getMiningPath();
        if (path.size() >= 2) {
            TunnelAnalyzer.analyze(path, stats);
        }

        RatioCalculator.calculate(data.getRecentEvents(), stats);
        DistributionAnalyzer.analyze(data.getRecentEvents(), stats);

        stats.setLastUpdated(System.currentTimeMillis());
    }
}
