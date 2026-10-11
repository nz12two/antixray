package com.antixray.plugin.model;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private static final long OFFLINE_CLEANUP_MS = 30 * 60 * 1000L; // 30 minutos

    public PlayerData getOrCreate(Player player) {
        PlayerData data = playerDataMap.computeIfAbsent(player.getUniqueId(),
                id -> new PlayerData(id, player.getName()));
        data.setTracked(true);
        return data;
    }

    public PlayerData getOrCreate(UUID playerId, String playerName) {
        PlayerData data = playerDataMap.computeIfAbsent(playerId,
                id -> new PlayerData(id, playerName));
        data.setTracked(true);
        return data;
    }

    public PlayerData get(UUID playerId) {
        return playerDataMap.get(playerId);
    }

    public void remove(UUID playerId) {
        PlayerData removed = playerDataMap.remove(playerId);
    }

    /** Remove jogadores offline há mais de 30 minutos para evitar vazamento de memória. */
    public void removeOfflinePlayers() {
        long cutoff = System.currentTimeMillis() - OFFLINE_CLEANUP_MS;
        playerDataMap.entrySet().removeIf(entry -> {
            PlayerData data = entry.getValue();
            if (data == null) return true;
            // Se está marcado como não tracked, remove
            if (!data.isTracked()) return true;
            // Se o player está online, não remove
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) return false;
            // Se passou do cutoff desde o último evento, remove
            if (data.getLastMiningTime() > 0 && data.getLastMiningTime() < cutoff) return true;
            return false;
        });
    }

    public void cleanup() {
        playerDataMap.clear();
    }

    public int size() { return playerDataMap.size(); }
    public Map<UUID, PlayerData> getAll() { return playerDataMap; }
}
