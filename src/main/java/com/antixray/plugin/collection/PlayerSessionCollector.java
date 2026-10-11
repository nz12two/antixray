package com.antixray.plugin.collection;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class PlayerSessionCollector implements Listener {
    private final AntiXrayPlugin plugin;
    private final PlayerDataManager dataManager;
    private BukkitTask taskId;

    public PlayerSessionCollector(AntiXrayPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void startTracking() {
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, PlayerData> entry : dataManager.getAll().entrySet()) {
                PlayerData data = entry.getValue();

                if (now - data.getLastMiningTime() > 300_000) {
                    data.getCurrentSession().setActive(false);
                }
            }
        }, 1200L, 1200L);
    }

    public void stopTracking() {
        if (taskId != null) {
            taskId.cancel();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataManager.getOrCreate(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = dataManager.get(player.getUniqueId());
        if (data != null) {
            data.getCurrentSession().setActive(false);
        }
    }
}
