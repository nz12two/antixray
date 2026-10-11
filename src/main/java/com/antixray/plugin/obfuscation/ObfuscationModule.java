package com.antixray.plugin.obfuscation;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Módulo principal de obfuscação do AntiXray.
 * <p>
 * Gerencia o ciclo de vida de todos os subcomponentes de obfuscação:
 * <ul>
 *   <li>{@link BlockRevealManager} — controle de blocos revelados/ocultos por jogador</li>
 *   <li>{@link ChunkPacketInterceptor} — interceptação de pacotes (requer ProtocolLib)</li>
 *   <li>{@link LineOfSightChecker} — verificação de linha de visão</li>
 *   <li>{@link FrustumCuller} — culling de frustum para otimização</li>
 * </ul>
 * <p>
 * Se ProtocolLib não estiver disponível, opera em <b>modo degradado</b>:
 * apenas os detectores (LineOfSightChecker, FrustumCuller) funcionam,
 * sem obfuscação de pacotes.
 */
public class ObfuscationModule extends Module {

    // --------------------------------------------------------------
    // Plugin reference
    // --------------------------------------------------------------
    private final AntiXrayPlugin plugin;

    // --------------------------------------------------------------
    // Subcomponentes
    // --------------------------------------------------------------
    private BlockRevealManager blockRevealManager;
    private ChunkPacketInterceptor chunkPacketInterceptor;
    private LineOfSightChecker lineOfSightChecker;
    private FrustumCuller frustumCuller;
    private BukkitTask proximityTask;

    // --------------------------------------------------------------
    // Configurações (lidas da config.yml)
    // --------------------------------------------------------------
    private boolean obfuscationEnabled;
    private String engineMode;           // "packet" | "raytrace" | "hybrid"
    private boolean hideBlockEntities;
    private int revealRadius;
    private int maxUpdatesPerTick;
    private boolean protocolLibAvailable;

    // --------------------------------------------------------------
    // Adaptive scheduling
    // --------------------------------------------------------------
    private double lastTps = 20.0;
    private int adaptiveInterval = 20; // ticks
    private int adaptiveMaxUpdates = 100;
    private int lowTpsCount = 0;

    // --------------------------------------------------------------
    // Construtor
    // --------------------------------------------------------------

    /**
     * @param plugin Instância principal do plugin AntiXray.
     */
    public ObfuscationModule(AntiXrayPlugin plugin) {
        super("obfuscation");
        this.plugin = plugin;
    }

    // --------------------------------------------------------------
    // Ciclo de vida
    // --------------------------------------------------------------

    @Override
    public void onEnable() {
        loadConfig();

        if (!obfuscationEnabled) {
            plugin.getLogger().info("[Obfuscation] Module is disabled in config.yml");
            return;
        }

        // Detecta ProtocolLib
        this.protocolLibAvailable = isProtocolLibAvailable();

        // Inicializa gerenciadores independentes de packet interception
        this.blockRevealManager = new BlockRevealManager(plugin);
        this.lineOfSightChecker = new LineOfSightChecker(plugin);
        this.frustumCuller = new FrustumCuller(plugin);

        // Registra serviços
        plugin.getServiceRegistry().register(ObfuscationModule.class, this);
        plugin.getServiceRegistry().register(BlockRevealManager.class, blockRevealManager);

        // Inicializa interceptor de pacotes (só se ProtocolLib existir)
        if (protocolLibAvailable) {
            try {
                this.chunkPacketInterceptor = new ChunkPacketInterceptor(plugin, blockRevealManager);
                // register() removed — ChunkPacketInterceptor no longer uses ProtocolLib
                plugin.getLogger().info("[Obfuscation] ProtocolLib detected — packet interception enabled");
            } catch (Exception e) {
                plugin.getLogger().warning("[Obfuscation] Failed to initialize ChunkPacketInterceptor: " + e.getMessage());
                this.chunkPacketInterceptor = null;
            }
        } else {
            plugin.getLogger().warning("[Obfuscation] ProtocolLib not found — operating in DEGRADED mode (no packet obfuscation)");
        }

        // Register PlayerChunkLoadEvent listener (Paper API, no ProtocolLib needed)
        if (chunkPacketInterceptor != null) {
            Bukkit.getPluginManager().registerEvents(chunkPacketInterceptor, plugin);
            plugin.getLogger().info("[Obfuscation] PlayerChunkLoadEvent listener registered");
        }

        // Agenda task de atualização de proximidade (roda a cada tick para verificar TPS, mas pode pular execução)
        this.proximityTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::updateProximityAdaptive,
                20L,   // delay inicial
                1L     // período: 1 tick
        );

        plugin.getLogger().info("[Obfuscation] Module enabled | engine-mode=" + engineMode
                + " reveal-radius=" + revealRadius
                + " protocol-lib=" + protocolLibAvailable);
    }

    @Override
    public void onDisable() {
        // Cancela task agendada
        if (proximityTask != null && !proximityTask.isCancelled()) {
            proximityTask.cancel();
            proximityTask = null;
        }

        // Remove interceptor de pacotes
        if (chunkPacketInterceptor != null) {
            chunkPacketInterceptor.unregister();
            chunkPacketInterceptor = null;
        }

        // Limpa dados de jogadores
        if (blockRevealManager != null) {
            blockRevealManager.cleanup();
        }

        // Remove registros do ServiceRegistry
        plugin.getServiceRegistry().unregister(ObfuscationModule.class);
        plugin.getServiceRegistry().unregister(BlockRevealManager.class);

        plugin.getLogger().info("[Obfuscation] Module disabled");
    }

    // --------------------------------------------------------------
    // Configuração
    // --------------------------------------------------------------

    /**
     * Carrega valores da config.yml com defaults sensíveis.
     */
    private void loadConfig() {
        var config = plugin.getConfig();
        this.obfuscationEnabled = config.getBoolean("obfuscation.enabled", true);
        this.engineMode = config.getString("obfuscation.engine-mode", "packet");
        this.hideBlockEntities = config.getBoolean("obfuscation.hide-block-entities", true);
        this.revealRadius = config.getInt("obfuscation.reveal-radius", 24);
        this.maxUpdatesPerTick = config.getInt("obfuscation.max-updates-per-tick", 100);
    }

    // --------------------------------------------------------------
    // ProtocolLib detection
    // --------------------------------------------------------------

    /**
     * Verifica se o plugin ProtocolLib está presente no servidor.
     *
     * @return {@code true} se ProtocolLib estiver carregado.
     */
    private boolean isProtocolLibAvailable() {
        return Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    }

    // --------------------------------------------------------------
    // Adaptive proximity update
    // --------------------------------------------------------------

    /**
     * Task periódica adaptativa que verifica TPS e ajusta a frequência
     * de atualização de proximidade.
     */
    private void updateProximityAdaptive() {
        // Verifica TPS
        double currentTps = Bukkit.getTPS()[0];
        lastTps = currentTps;

        if (currentTps < 16.0) {
            lowTpsCount++;
        } else {
            lowTpsCount = Math.max(0, lowTpsCount - 1);
        }

        // Adaptive: se TPS < 16 por 3+ ticks consecutivos, reduz frequência
        if (lowTpsCount >= 3 && currentTps < 16.0) {
            // Pula esta execução (roda 1/5 da frequência)
            if (Bukkit.getCurrentTick() % 5 != 0) return;
            adaptiveMaxUpdates = Math.max(25, maxUpdatesPerTick / 2);
        } else if (currentTps < 18.0) {
            // TPS moderado: roda 1/2 da frequência
            if (Bukkit.getCurrentTick() % 2 != 0) return;
            adaptiveMaxUpdates = Math.max(50, maxUpdatesPerTick / 2);
        } else {
            adaptiveMaxUpdates = maxUpdatesPerTick;
        }

        updateProximity();
    }

    /**
     * Task periódica que revela minérios próximos aos jogadores e
     * re-oculta minérios que ficaram distantes.
     * <p>
     * Respeita o limite de {@link #adaptiveMaxUpdates} por execução.
     */
    private void updateProximity() {
        if (blockRevealManager == null) return;

        int updatesThisTick = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (updatesThisTick >= adaptiveMaxUpdates) break;

            UUID playerId = player.getUniqueId();
            Location playerLoc = player.getLocation();
            World world = player.getWorld();
            int pX = playerLoc.getBlockX();
            int pY = playerLoc.getBlockY();
            int pZ = playerLoc.getBlockZ();
            int radiusSq = revealRadius * revealRadius;

            // --- Revela blocos ocultos que estão dentro do raio ---
            Set<Long> hiddenKeys = blockRevealManager.getHiddenBlockKeys(playerId);
            if (hiddenKeys != null && !hiddenKeys.isEmpty()) {
                List<Long> toReveal = new ArrayList<>();

                for (long key : hiddenKeys) {
                    if (updatesThisTick >= adaptiveMaxUpdates) break;
                    int bx = BlockRevealManager.decodeX(key);
                    int by = BlockRevealManager.decodeY(key);
                    int bz = BlockRevealManager.decodeZ(key);

                    if (distanceSquared(pX, pY, pZ, bx, by, bz) <= radiusSq) {
                        toReveal.add(key);
                    }
                }

                for (long key : toReveal) {
                    if (updatesThisTick >= adaptiveMaxUpdates) break;
                    int bx = BlockRevealManager.decodeX(key);
                    int by = BlockRevealManager.decodeY(key);
                    int bz = BlockRevealManager.decodeZ(key);

                    // Material real do mundo (não foi alterado, só o packet)
                    Material realMaterial = world.getBlockAt(bx, by, bz).getType();
                    blockRevealManager.revealBlock(playerId, bx, by, bz, realMaterial);
                    updatesThisTick++;
                }
            }

            // --- Re-oculta blocos revelados que estão fora do raio ---
            Set<Long> revealedKeys = blockRevealManager.getRevealedBlockKeys(playerId);
            if (revealedKeys != null && !revealedKeys.isEmpty()) {
                List<Long> toHide = new ArrayList<>();

                for (long key : revealedKeys) {
                    if (updatesThisTick >= adaptiveMaxUpdates) break;
                    int bx = BlockRevealManager.decodeX(key);
                    int by = BlockRevealManager.decodeY(key);
                    int bz = BlockRevealManager.decodeZ(key);

                    if (distanceSquared(pX, pY, pZ, bx, by, bz) > radiusSq) {
                        toHide.add(key);
                    }
                }

                for (long key : toHide) {
                    if (updatesThisTick >= adaptiveMaxUpdates) break;
                    int bx = BlockRevealManager.decodeX(key);
                    int by = BlockRevealManager.decodeY(key);
                    int bz = BlockRevealManager.decodeZ(key);

                    Material fakeMaterial = getFakeMaterial(world);
                    blockRevealManager.hideBlock(playerId, bx, by, bz, fakeMaterial);
                    updatesThisTick++;
                }
            }
        }
    }

    // --------------------------------------------------------------
    // Decoy blocks
    // --------------------------------------------------------------

    /**
     * Envia um {@code BlockChangePacket} <b>falso</b> para o jogador,
     * fazendo com que ele veja um bloco aleatório (pedra, cascalho, etc.)
     * em vez do bloco real.
     * <p>
     * Útil para simular mineração ou confundir clientes maliciosos.
     *
     * @param player   Jogador alvo.
     * @param location Localização do bloco falso.
     */
    public void createDecoyBlock(Player player, Location location) {
        if (player == null || location == null || !location.isWorldLoaded()) return;

        Material[] decoys = {
                Material.STONE,
                Material.COBBLESTONE,
                Material.GRAVEL,
                Material.DIORITE,
                Material.ANDESITE,
                Material.GRANITE,
                Material.TUFF,
                Material.DEEPSLATE
        };
        Material decoy = decoys[new Random().nextInt(decoys.length)];
        player.sendBlockChange(location, decoy.createBlockData());
    }

    // --------------------------------------------------------------
    // Utilitários
    // --------------------------------------------------------------

    /**
     * Retorna o material "falso" apropriado para a dimensão do mundo.
     */
    public Material getFakeMaterial(World world) {
        if (world == null) return Material.STONE;
        switch (world.getEnvironment()) {
            case NETHER:
                return Material.NETHERRACK;
            case THE_END:
                return Material.END_STONE;
            default:
                // Overworld: STONE para y ≥ 0, DEEPSLATE para y < 0
                return Material.STONE;
        }
    }

    private static int distanceSquared(int x1, int y1, int z1, int x2, int y2, int z2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        int dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    // --------------------------------------------------------------
    // Getters
    // --------------------------------------------------------------

    public BlockRevealManager getBlockRevealManager() {
        return blockRevealManager;
    }

    public ChunkPacketInterceptor getChunkPacketInterceptor() {
        return chunkPacketInterceptor;
    }

    public LineOfSightChecker getLineOfSightChecker() {
        return lineOfSightChecker;
    }

    public FrustumCuller getFrustumCuller() {
        return frustumCuller;
    }

    public String getEngineMode() {
        return engineMode;
    }

    public boolean isHideBlockEntities() {
        return hideBlockEntities;
    }

    public int getRevealRadius() {
        return revealRadius;
    }

    public int getMaxUpdatesPerTick() {
        return maxUpdatesPerTick;
    }
}
