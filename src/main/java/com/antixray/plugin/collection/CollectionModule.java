package com.antixray.plugin.collection;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.model.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class CollectionModule extends Module {
    private final AntiXrayPlugin plugin;
    private BlockBreakCollector blockBreakCollector;
    private MovementCollector movementCollector;
    private PlayerSessionCollector sessionCollector;
    private PlayerDataManager playerDataManager;
    private BukkitTask cleanupTask;

    public CollectionModule(AntiXrayPlugin plugin) {
        super("collection");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.playerDataManager = new PlayerDataManager();
        plugin.getServiceRegistry().register(PlayerDataManager.class, playerDataManager);

        this.blockBreakCollector = new BlockBreakCollector(plugin, playerDataManager);
        this.movementCollector = new MovementCollector(playerDataManager);
        this.sessionCollector = new PlayerSessionCollector(plugin, playerDataManager);

        Bukkit.getPluginManager().registerEvents(blockBreakCollector, plugin);
        Bukkit.getPluginManager().registerEvents(movementCollector, plugin);
        Bukkit.getPluginManager().registerEvents(sessionCollector, plugin);

        sessionCollector.startTracking();

        // Cleanup players offline a cada 5 minutos (6000 ticks = 5 min)
        this.cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                playerDataManager::removeOfflinePlayers, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (sessionCollector != null) {
            sessionCollector.stopTracking();
        }
        if (playerDataManager != null) {
            playerDataManager.cleanup();
        }
    }

    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
}
