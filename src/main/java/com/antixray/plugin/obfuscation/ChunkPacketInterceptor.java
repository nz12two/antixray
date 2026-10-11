package com.antixray.plugin.obfuscation;

import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;

import java.util.*;

/**
 * Intercepta chunks enviados aos jogadores e substitui minérios não-expostos
 * por materiais genéricos (pedra, netherrack, end_stone).
 * <p>
 * Usa {@link io.papermc.paper.event.packet.PlayerChunkLoadEvent} para escanear
 * chunks recebidos e aplicar substituições via {@code sendMultiBlockChange()}.
 * Processa 1 chunk por tick via scheduler para evitar picos no main thread.
 * <p>
 * Mantém cache LRU de chunks processados para evitar re-escaneamento.
 */
public class ChunkPacketInterceptor implements Listener {

    private final Plugin plugin;
    private final BlockRevealManager revealManager;
    // ProtocolLib removed — BLOCK_CHANGE handler removed in favor of PlayerChunkLoadEvent

    /** Cache LRU de chunks processados. */
    private final Map<Long, CachedChunkData> chunkCache;
    private final int maxCacheSize;

    /** Inner class para dados cacheados do chunk. */
    private static class CachedChunkData {
        final long timestamp;
        final Map<String, Material> oreReplacements; // "x,y,z" → fake material
        final Set<String> orePositions; // "x,y,z" positions that are ores (for fast lookup)

        CachedChunkData(Map<String, Material> oreReplacements, Set<String> orePositions) {
            this.timestamp = System.currentTimeMillis();
            this.oreReplacements = oreReplacements;
            this.orePositions = orePositions != null ? orePositions : Collections.emptySet();
        }
    }

    /** Conjunto com todos os materiais que contêm "_ORE" no nome. */
    private final Set<Material> oreMaterials = EnumSet.noneOf(Material.class);

    // PacketAdapter removed — no longer intercepting packets via ProtocolLib

    // --------------------------------------------------------------
    // Queue system for async chunk processing
    // --------------------------------------------------------------

    /** Fila de chunks aguardando processamento (evita picos no main thread). */
    private final Queue<ChunkLoadTask> pendingChunks = new ConcurrentLinkedQueue<>();
    /** ID da task do scheduler responsável por processar a fila (-1 = inativo). */
    private int processingTaskId = -1;

    private static class ChunkLoadTask {
        final Player player;
        final Chunk chunk;
        final World world;
        final int chunkX;
        final int chunkZ;
        final UUID playerId;

        ChunkLoadTask(Player player, Chunk chunk) {
            this.player = player;
            this.chunk = chunk;
            this.world = chunk.getWorld();
            this.chunkX = chunk.getX();
            this.chunkZ = chunk.getZ();
            this.playerId = player.getUniqueId();
        }
    }

    // --------------------------------------------------------------
    // Construtores
    // --------------------------------------------------------------

    /**
     * @param plugin        Plugin pai.
     * @param revealManager Gerenciador de blocos revelados/ocultos.
     * @param maxCacheSize  Tamanho máximo do cache LRU (mínimo 100).
     */
    public ChunkPacketInterceptor(Plugin plugin, BlockRevealManager revealManager, int maxCacheSize) {
        this.plugin = plugin;
        this.revealManager = revealManager;
        this.maxCacheSize = Math.max(100, maxCacheSize);
        this.chunkCache = Collections.synchronizedMap(new LinkedHashMap<Long, CachedChunkData>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CachedChunkData> eldest) {
                return size() > ChunkPacketInterceptor.this.maxCacheSize;
            }
        });
        initOreMaterials();
    }

    /**
     * @param plugin        Plugin pai.
     * @param revealManager Gerenciador de blocos revelados/ocultos.
     */
    public ChunkPacketInterceptor(Plugin plugin, BlockRevealManager revealManager) {
        this(plugin, revealManager, 500);
    }

    /**
     * Popula {@link #oreMaterials} com todos os materiais que têm sufixo {@code _ORE}.
     */
    private void initOreMaterials() {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_ORE")) {
                oreMaterials.add(material);
            }
        }
        // Inclui ANCIENT_DEBRIS (não termina em _ORE mas é minério valioso)
        oreMaterials.add(Material.ANCIENT_DEBRIS);
    }

    // --------------------------------------------------------------
    // Registro / Remoção
    // --------------------------------------------------------------

    @EventHandler
    public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        // Apenas enfileira — o processamento ocorre 1 chunk/tick via scheduler
        pendingChunks.add(new ChunkLoadTask(event.getPlayer(), event.getChunk()));
        ensureProcessing();
    }

    /**
     * Garante que o scheduler de processamento da fila está ativo.
     * Processa 1 chunk por tick para não travar o main thread.
     */
    private void ensureProcessing() {
        if (processingTaskId != -1) return;
        processingTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ChunkLoadTask task = pendingChunks.poll();
            if (task == null) {
                // Fila vazia — cancela o scheduler
                Bukkit.getScheduler().cancelTask(processingTaskId);
                processingTaskId = -1;
                return;
            }
            processChunk(task);
        }, 1L, 1L).getTaskId();
    }

    /**
     * Remove os listeners do ProtocolManager, para o scheduler e limpa o cache.
     */
    public void unregister() {
        // Cancela scheduler de processamento
        if (processingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(processingTaskId);
            processingTaskId = -1;
        }
        pendingChunks.clear();
        chunkCache.clear();
    }

    // --------------------------------------------------------------
    // MAP_CHUNK
    // --------------------------------------------------------------

    /**
     * Processa um {@code MAP_CHUNK} packet.
     * <p>
     * Escaneia os blocos do chunk no mundo, identifica minérios não-expostos
     * ao ar e não-revelados, e modifica os dados do chunk no pacote para
     * mostrar materiais falsos.
     * <p>
     * Utiliza cache de chunks para evitar re-processamento.
     */


    /**
     * Processa um chunk da fila: usa ChunkSnapshot para leitura rápida,
     * escaneia apenas Y onde minérios geram, e aplica substituições.
     */
    private void processChunk(ChunkLoadTask task) {
        long chunkKey = getChunkKey(task.chunkX, task.chunkZ);
        Player player = task.player;

        // Double-check jogador ainda online
        if (!player.isOnline()) return;

        // Verifica cache primeiro
        CachedChunkData cached = chunkCache.get(chunkKey);
        if (cached != null) {
            if (!cached.oreReplacements.isEmpty()) {
                applyCachedReplacements(player, task.world, task.chunkX, task.chunkZ, cached);
            }
            return;
        }

        // ChunkSnapshot é MUITO mais rápido que chunk.getBlock().getType()
        ChunkSnapshot snapshot = task.chunk.getChunkSnapshot();

        int baseX = task.chunkX << 4;
        int baseZ = task.chunkZ << 4;

        // Apenas escaneia Y onde minérios geram:
        // Overworld: Y=-64 a 256
        // Nether: Y=8 a 120
        // End: sem minérios, pula
        int minY, maxY;
        switch (task.world.getEnvironment()) {
            case NETHER:
                minY = 8;
                maxY = 120;
                break;
            case THE_END:
                return; // Sem minérios no End
            default:
                minY = Math.max(task.world.getMinHeight(), -64);
                maxY = Math.min(task.world.getMaxHeight(), 256);
                break;
        }

        // Early check: se minY >= maxY, nada a escanear
        if (minY >= maxY) {
            chunkCache.put(chunkKey, new CachedChunkData(Collections.emptyMap(), Collections.emptySet()));
            return;
        }

        Map<String, Material> replacements = new HashMap<>();
        Set<String> orePosSet = new HashSet<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    int bx = baseX + x;
                    int bz = baseZ + z;

                    // Leitura rápida do snapshot (~10x mais rápido)
                    Material type = snapshot.getBlockType(x, y, z);

                    // Só verifica condições caras se for minério
                    if (!isOre(type)) continue;

                    if (revealManager.isRevealed(task.playerId, bx, y, bz)) continue;

                    if (isExposedToAir(task.world, bx, y, bz)) continue;

                    Material fake = getFakeForDimension(task.world, y);
                    replacements.put(x + "," + y + "," + z, fake);
                    orePosSet.add(x + "," + y + "," + z);
                }
            }
        }

        chunkCache.put(chunkKey, new CachedChunkData(new HashMap<>(replacements), orePosSet));

        if (!replacements.isEmpty()) {
            applyReplacements(player, task.world, task.chunkX, task.chunkZ, replacements);
        }
    }

    /**
     * Re-aplica substituições de um chunk previamente cacheado.
     */
    private void applyCachedReplacements(Player player, World world, int chunkX, int chunkZ, CachedChunkData cached) {
        if (cached.oreReplacements.isEmpty()) return;
        UUID playerId = player.getUniqueId();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        Map<Location, BlockData> changes = new HashMap<>();
        for (Map.Entry<String, Material> entry : cached.oreReplacements.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            int bx = baseX + x;
            int bz = baseZ + z;

            if (revealManager.isRevealed(playerId, bx, y, bz)) continue;
            // Verifica se o bloco REAL ainda é minério (não mudou)
            Material realType = world.getBlockAt(bx, y, bz).getType();
            if (!isOre(realType)) continue;

            Location loc = new Location(world, bx, y, bz);
            changes.put(loc, entry.getValue().createBlockData());
            revealManager.trackHiddenBlock(playerId, bx, y, bz, entry.getValue());
        }

        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    /**
     * Aplica substituições de minério via sendMultiBlockChange.
     */
    private void applyReplacements(Player player, World world, int chunkX, int chunkZ, Map<String, Material> replacements) {
        if (replacements.isEmpty()) return;
        UUID playerId = player.getUniqueId();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        Map<Location, BlockData> changes = new HashMap<>();
        for (Map.Entry<String, Material> entry : replacements.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            int bx = baseX + x;
            int bz = baseZ + z;

            Location loc = new Location(world, bx, y, bz);
            changes.put(loc, entry.getValue().createBlockData());
            revealManager.trackHiddenBlock(playerId, bx, y, bz, entry.getValue());
        }

        player.sendMultiBlockChange(changes);
    }


    // --------------------------------------------------------------
    // Utilitários
    // --------------------------------------------------------------

    /**
     * Verifica se um material é um minério que deve ser ocultado.
     */
    private boolean isOre(Material material) {
        return oreMaterials.contains(material);
    }

    /**
     * Verifica se um bloco está exposto ao ar (adjacente a ar em qualquer
     * direção).
     */
    private boolean isExposedToAir(World world, int x, int y, int z) {
        // Usa getType() diretamente (é mais rápido que getBlockAt().getType())
        return world.getBlockAt(x + 1, y, z).isEmpty()
                || world.getBlockAt(x - 1, y, z).isEmpty()
                || world.getBlockAt(x, y + 1, z).isEmpty()
                || world.getBlockAt(x, y - 1, z).isEmpty()
                || world.getBlockAt(x, y, z + 1).isEmpty()
                || world.getBlockAt(x, y, z - 1).isEmpty();
    }

    /**
     * Retorna o material falso apropriado para a dimensão e altura.
     */
    private Material getFakeForDimension(World world, int y) {
        if (world == null) return Material.STONE;
        switch (world.getEnvironment()) {
            case NETHER:
                return Material.NETHERRACK;
            case THE_END:
                return Material.END_STONE;
            default:
                // Overworld: DEEPSLATE para Y < 0, STONE para Y ≥ 0
                return y < 0 ? Material.DEEPSLATE : Material.STONE;
        }
    }

    /**
     * Gera chave única para o cache de chunks.
     */
    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // --------------------------------------------------------------
    // Controle de cache
    // --------------------------------------------------------------

    /**
     * Invalida o cache de um chunk específico.
     * Chamado quando blocos no chunk são alterados.
     *
     * @param chunkX Coordenada X do chunk.
     * @param chunkZ Coordenada Z do chunk.
     */
    public void invalidateCache(int chunkX, int chunkZ) {
        chunkCache.remove(getChunkKey(chunkX, chunkZ));
    }

    /**
     * Limpa todo o cache de chunks.
     */
    public void clearCache() {
        chunkCache.clear();
    }
}
