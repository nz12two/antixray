package com.antixray.plugin.obfuscation;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Realiza ray tracing para verificar se um bloco está na linha de visão de um jogador.
 * <p>
 * AVISO: rayTraceBlocks é CPU intensivo. Use com moderação e respeite o cache.
 */
public class LineOfSightChecker {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST
    };

    private static final long CACHE_DURATION_MS = 2000L;

    private static final Set<Material> ORE_MATERIALS = EnumSet.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    private final Plugin plugin;

    // Cache: chave = "playerUUID:blockX:blockY:blockZ", valor = {resultado, timestamp}
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // Rate limiter: último ray trace por jogador
    private final Map<UUID, Long> lastRayTracePerPlayer = new ConcurrentHashMap<>();
    private static final long RAY_TRACE_COOLDOWN_MS = 500; // max 2 ray traces por segundo por jogador

    public LineOfSightChecker(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
    }

    /**
     * Verifica se um jogador tem linha de visão direta para um bloco alvo.
     * <p>
     * Usa {@link World#rayTraceBlocks(Location, Vector, double, FluidCollisionMode, boolean)} do Bukkit.
     *
     * @param player         jogador que está olhando.
     * @param blockLocation  localização do bloco alvo.
     * @return {@code true} se o ray trace atingir o bloco alvo ou se não houver blocos no caminho;
     *         {@code false} se for interrompido por outro bloco antes do alvo.
     */
    public boolean hasLineOfSight(Player player, Location blockLocation) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(blockLocation, "Block location cannot be null");

        String cacheKey = buildCacheKey(player, blockLocation);
        CacheEntry cached = getCached(cacheKey);
        if (cached != null) {
            return cached.result;
        }

        boolean result = computeLineOfSight(player, blockLocation);
        cache.put(cacheKey, new CacheEntry(result, System.currentTimeMillis()));
        return result;
    }

    /**
     * Verifica múltiplos blocos candidatos em paralelo, retornando apenas aqueles
     * que estão na linha de visão do jogador.
     *
     * @param player      jogador.
     * @param candidates  coleção de localizações candidatas.
     * @param maxDistance distância máxima para considerar um bloco (não utilizado no raio,
     *                    mas pode ser usado para pré-filtro).
     * @return conjunto de localizações visíveis.
     */
    public Set<Location> getVisibleBlocks(Player player, Collection<Location> candidates, double maxDistance) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(candidates, "Candidates cannot be null");

        if (candidates.isEmpty()) {
            return Collections.emptySet();
        }

        double maxDistanceSq = maxDistance * maxDistance;
        Location eyeLocation = player.getEyeLocation();

        return candidates.parallelStream()
                .filter(loc -> loc != null && loc.getWorld() != null && loc.getWorld().equals(player.getWorld()))
                .filter(loc -> loc.distanceSquared(eyeLocation) <= maxDistanceSq)
                .filter(loc -> hasLineOfSight(player, loc))
                .collect(Collectors.toSet());
    }

    /**
     * Escaneia blocos ao redor do jogador dentro de um raio e retorna apenas aqueles
     * que estão na linha de visão.
     *
     * @param player jogador.
     * @param radius raio de blocos para escanear (em todas as direções).
     * @return conjunto de localizações de blocos visíveis ao redor.
     */
    public Set<Location> getVisibleOresInRadius(Player player, int radius) {
        Objects.requireNonNull(player, "Player cannot be null");
        if (radius < 0) {
            throw new IllegalArgumentException("Radius cannot be negative");
        }

        Location center = player.getLocation();
        World world = player.getWorld();
        Set<Location> candidates = new HashSet<>();

        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();

        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), by - radius);
                 y <= Math.min(world.getMaxHeight() - 1, by + radius); y++) {
                for (int z = bz - radius; z <= bz + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isOre(block.getType())) {
                        candidates.add(block.getLocation());
                    }
                }
            }
        }

        return getVisibleBlocks(player, candidates, radius * Math.sqrt(3));
    }

    /**
     * Verifica se o bloco está exposto ao ar em pelo menos uma das 6 faces.
     *
     * @param location localização do bloco.
     * @return {@code true} se alguma face estiver adjacente a AR, CAVE_AIR ou VOID_AIR.
     */
    public boolean isExposedToAir(Location location) {
        Objects.requireNonNull(location, "Location cannot be null");

        Block block = location.getBlock();
        if (block.getType() == Material.AIR
                || block.getType() == Material.CAVE_AIR
                || block.getType() == Material.VOID_AIR) {
            return true;
        }

        for (BlockFace face : FACES) {
            Block relative = block.getRelative(face);
            Material type = relative.getType();
            if (type == Material.AIR
                    || type == Material.CAVE_AIR
                    || type == Material.VOID_AIR) {
                return true;
            }
        }

        return false;
    }

    /**
     * Calcula o fator de oclusão entre 0.0 (totalmente visível) e 1.0 (completamente oculto).
     * <p>
     * O algoritmo realiza ray tracing progressivo: dispara múltiplos raios em pequenos
     * offsets ao redor do centro do bloco alvo e calcula a proporção dos que são
     * interceptados antes de chegar ao destino.
     *
     * @param player jogador.
     * @param target localização do bloco alvo.
     * @return valor entre 0.0 e 1.0 representando o nível de oclusão.
     */
    public double getOcclusionFactor(Player player, Location target) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(target, "Target location cannot be null");

        Location eye = player.getEyeLocation();
        double distance = eye.distance(target);

        if (distance < 0.5) {
            return 0.0; // Muito próximo, considera visível
        }

        Vector direction = target.toVector().subtract(eye.toVector()).normalize();

        // Pequenos offsets para amostrar diferentes pontos do bloco
        double[][] offsets = {
                {0.0, 0.0, 0.0},   // centro
                {0.3, 0.3, 0.0},
                {-0.3, 0.3, 0.0},
                {0.3, -0.3, 0.0},
                {-0.3, -0.3, 0.0}
        };

        int hitCount = 0;
        World world = player.getWorld();

        for (double[] offset : offsets) {
            Vector offsetVec = new Vector(offset[0], offset[1], offset[2]);
            Location sampleTarget = target.clone().add(offsetVec);

            Vector sampleDir = sampleTarget.toVector().subtract(eye.toVector()).normalize();
            double sampleDistance = eye.distance(sampleTarget);

            RayTraceResult result = world.rayTraceBlocks(eye, sampleDir, sampleDistance,
                    FluidCollisionMode.NEVER, true);

            if (result != null && result.getHitBlock() != null) {
                Location hitLoc = result.getHitBlock().getLocation();
                if (!hitLoc.equals(target.getBlock().getLocation())) {
                    hitCount++;
                }
            }
        }

        return (double) hitCount / offsets.length;
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private boolean computeLineOfSight(Player player, Location blockLocation) {
        UUID playerId = player.getUniqueId();
        Long lastTrace = lastRayTracePerPlayer.get(playerId);
        long now = System.currentTimeMillis();
        if (lastTrace != null && now - lastTrace < RAY_TRACE_COOLDOWN_MS) {
            // Usa cache em vez de ray trace real
            CacheEntry cached = getCached(buildCacheKey(player, blockLocation));
            if (cached != null) return cached.result;
            return true; // default otimista se não tiver cache
        }
        lastRayTracePerPlayer.put(playerId, now);

        Location eyeLocation = player.getEyeLocation();
        Vector direction = blockLocation.toVector().subtract(eyeLocation.toVector());
        double distance = eyeLocation.distance(blockLocation);

        // Se o jogador está dentro do bloco alvo, considera visível
        if (distance < 0.1) {
            return true;
        }

        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eyeLocation,
                direction,
                distance,
                FluidCollisionMode.NEVER,
                true
        );

        // Sem colisão → linha de visão livre
        if (result == null) {
            return true;
        }

        // Colidiu com algúm — verifica se foi com o próprio bloco alvo
        Block hitBlock = result.getHitBlock();
        if (hitBlock == null) {
            return true;
        }

        return hitBlock.getLocation().equals(blockLocation.getBlock().getLocation());
    }

    private String buildCacheKey(Player player, Location location) {
        return player.getUniqueId() + ":"
                + location.getBlockX() + ":"
                + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    private synchronized CacheEntry getCached(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.timestamp > CACHE_DURATION_MS) {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    /**
     * Limpa manualmente entradas expiradas do cache.
     * Pode ser chamado periodicamente para evitar acumulo.
     */
    public synchronized void purgeCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now - e.getValue().timestamp > CACHE_DURATION_MS);
    }

    /**
     * Limpa todo o cache (útil em reloads ou troca de mundo).
     */
    public synchronized void clearCache() {
        cache.clear();
    }

    /**
     * Verifica se o material é um minério que vale a pena escanear.
     */
    private boolean isOre(Material material) {
        return ORE_MATERIALS.contains(material);
    }

    // ---------------------------------------------------------------
    // Internal class
    // ---------------------------------------------------------------

    private static class CacheEntry {
        final boolean result;
        final long timestamp;

        CacheEntry(boolean result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }
}
