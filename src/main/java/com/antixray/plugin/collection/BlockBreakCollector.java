package com.antixray.plugin.collection;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.*;

public class BlockBreakCollector implements Listener {
    private final AntiXrayPlugin plugin;
    private final PlayerDataManager dataManager;
    private final EventBus eventBus;
    private final Map<UUID, Map<Location, Long>> placedBlocks = new HashMap<>();
    private final Map<Location, Long> explosionExposed = new HashMap<>();

    private static final Set<Material> AIR_TYPES = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR
    );
    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static final Set<Material> MINING_RELEVANT = Set.of(
        // Overworld stone types
        Material.STONE, Material.DEEPSLATE, Material.GRANITE,
        Material.DIORITE, Material.ANDESITE, Material.TUFF,
        Material.CALCITE, Material.DRIPSTONE_BLOCK,
        Material.SMOOTH_BASALT, Material.MOSSY_COBBLESTONE,
        // Nether
        Material.NETHERRACK, Material.BLACKSTONE, Material.BASALT,
        Material.SOUL_SAND, Material.SOUL_SOIL,
        // End
        Material.END_STONE,
        // Underground common
        Material.GRAVEL, Material.CLAY, Material.MOSS_BLOCK,
        Material.SANDSTONE, Material.RED_SANDSTONE,
        Material.OBSIDIAN, Material.MAGMA_BLOCK, Material.GLOWSTONE,
        Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
        Material.PACKED_ICE, Material.DIRT, Material.ROOTED_DIRT, Material.MUD,
        // Sculk
        Material.SCULK, Material.SCULK_VEIN,
        Material.SCULK_CATALYST, Material.SCULK_SENSOR, Material.SCULK_SHRIEKER,
        Material.REINFORCED_DEEPSLATE,
        // Infested
        Material.INFESTED_STONE, Material.INFESTED_DEEPSLATE,
        Material.INFESTED_COBBLESTONE, Material.INFESTED_STONE_BRICKS,
        Material.INFESTED_MOSSY_STONE_BRICKS,
        Material.INFESTED_CRACKED_STONE_BRICKS,
        Material.INFESTED_CHISELED_STONE_BRICKS
    );


    public BlockBreakCollector(AntiXrayPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Block block = event.getBlock();
        Location location = block.getLocation();
        Material type = block.getType();
        String worldName = location.getWorld().getName();

        if (!plugin.getConfigLoader().isWorldDetectionEnabled(worldName)) return;
        if (isPlayerPlaced(playerId, location)) return;
        if (isExplosionExposed(location)) return;
        if (!isMiningRelevant(type)) return;

        PlayerData data = dataManager.getOrCreate(player);
        data.addPathLocation(location);

        boolean exposed = isExposedToAir(location);
        boolean rareOre = plugin.getConfigLoader().isOreRare(type.name());

        MiningEvent miningEvent = new MiningEvent.Builder()
                .playerId(playerId)
                .playerName(player.getName())
                .blockType(type)
                .location(location)
                .timestamp(System.currentTimeMillis())
                .ping(player.getPing())
                .tps(getTPS())
                .tool(player.getInventory().getItemInMainHand())
                .exposedToAir(exposed)
                .hiddenOre(!exposed && rareOre)
                .build();

        sessionCollectorData(player, data);
        data.addEvent(miningEvent);
        eventBus.post(miningEvent);
    }

    private void sessionCollectorData(Player player, PlayerData data) {
        data.getCurrentSession().incrementBlocksMined();
        data.setLastMiningTime(System.currentTimeMillis());
    }

    public boolean isPlayerPlaced(UUID playerId, Location location) {
        Map<Location, Long> placed = placedBlocks.get(playerId);
        if (placed == null) return false;
        Long time = placed.get(location);
        if (time == null) return false;
        if (System.currentTimeMillis() - time > 30 * 60 * 1000L) {
            placed.remove(location);
            return false;
        }
        return true;
    }

    public void markPlayerPlaced(UUID playerId, Location location) {
        placedBlocks.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(location, System.currentTimeMillis());
    }

    /** Remove entries expirados para evitar vazamento de memória. */
    public void cleanupExpiredEntries() {
        long cutoff = System.currentTimeMillis() - 30 * 60 * 1000L;
        placedBlocks.values().forEach(map -> map.entrySet().removeIf(e -> e.getValue() < cutoff));
        explosionExposed.values().removeIf(time -> time < cutoff);
        // Remove jogadores sem entradas válidas
        placedBlocks.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private boolean isExplosionExposed(Location location) {
        Long expiry = explosionExposed.get(location);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            explosionExposed.remove(location);
            return false;
        }
        return true;
    }

    public void markExplosionExposed(Location location, long retentionMs) {
        explosionExposed.put(location, System.currentTimeMillis() + retentionMs);
    }

    private boolean isExposedToAir(Location location) {
        Block block = location.getBlock();
        for (BlockFace face : FACES) {
            if (AIR_TYPES.contains(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMiningRelevant(Material material) {
        if (material.name().endsWith("_ORE")) return true;
        if (material == Material.ANCIENT_DEBRIS) return true;
        return MINING_RELEVANT.contains(material);
    }

    private double getTPS() {
        try {
            return Bukkit.getTPS()[0];
        } catch (Exception e) {
            return 20.0;
        }
    }
}
