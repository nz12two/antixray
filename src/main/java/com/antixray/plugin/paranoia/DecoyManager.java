package com.antixray.plugin.paranoia;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Gerencia iscas falsas (decoys) — envia pacotes de bloco falso
 * para a aparência de minérios próximos ao jogador, confundindo x-rayers.
 * <p>
 * As iscas são apenas visuais (sendBlockChange) e não existem no mundo real.
 */
public class DecoyManager {

    private final Plugin plugin;
    private final Map<UUID, List<Decoy>> activeDecoys;
    private static final long DECOY_TTL_MS = 30_000L; // 30 segundos
    private static final int MIN_DISTANCE = 3;
    private static final int MAX_DISTANCE = 8;

    public DecoyManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.activeDecoys = new ConcurrentHashMap<>();
    }

    // ──────────────────────────────────────────────
    // Decoy inner class
    // ──────────────────────────────────────────────

    public static class Decoy {
        private final Location location;
        private final Material fakeMaterial;
        private final long createdAt;
        private volatile boolean triggered;

        public Decoy(Location location, Material fakeMaterial) {
            this.location = location.clone();
            this.fakeMaterial = fakeMaterial;
            this.createdAt = System.currentTimeMillis();
            this.triggered = false;
        }

        public Location getLocation() { return location.clone(); }
        public Material getFakeMaterial() { return fakeMaterial; }
        public long getCreatedAt() { return createdAt; }
        public boolean isTriggered() { return triggered; }
        public void setTriggered(boolean triggered) { this.triggered = triggered; }
        public boolean isExpired() { return System.currentTimeMillis() - createdAt > DECOY_TTL_MS; }
    }

    // ──────────────────────────────────────────────
    // Spawn decoys
    // ──────────────────────────────────────────────

    /**
     * Cria uma isca falsa próxima ao jogador.
     * Escolhe um local aleatório 3-8 blocos de distância, mas não na linha de visão direta.
     * Envia um pacote de block change para simular o minério.
     */
    public void spawnDecoy(Player player, Location nearLocation, Material fakeOre) {
        if (player == null || nearLocation == null || fakeOre == null || !fakeOre.isBlock()) {
            return;
        }

        Location decoyLocation = findDecoyLocation(nearLocation, player);
        if (decoyLocation == null) return;

        // Envia o bloco falso apenas para este jogador
        player.sendBlockChange(decoyLocation, fakeOre.createBlockData());

        // Registra a isca
        Decoy decoy = new Decoy(decoyLocation, fakeOre);
        activeDecoys.computeIfAbsent(player.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(decoy);

        // Agenda remoção automática após 30 segundos
        scheduleDecoyRemoval(player, decoy);
    }

    /**
     * Gera múltiplas iscas ao redor de um centro.
     */
    public void spawnDecoyCluster(Player player, Location center, Material fakeOre, int count) {
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            spawnDecoy(player, center, fakeOre);
        }
    }

    // ──────────────────────────────────────────────
    // Query
    // ──────────────────────────────────────────────

    /**
     * Verifica se uma localização corresponde a uma isca ativa.
     */
    public boolean isDecoy(Location location) {
        return getDecoy(location) != null;
    }

    /**
     * Retorna a isca na localização, ou null se não existir.
     */
    public Decoy getDecoy(Location location) {
        for (List<Decoy> decoys : activeDecoys.values()) {
            synchronized (decoys) {
                for (Decoy decoy : decoys) {
                    if (isSameLocation(decoy.getLocation(), location)) {
                        return decoy;
                    }
                }
            }
        }
        return null;
    }

    public List<Decoy> getDecoys(UUID playerId) {
        List<Decoy> decoys = activeDecoys.get(playerId);
        if (decoys == null) return Collections.emptyList();
        synchronized (decoys) {
            return new ArrayList<>(decoys);
        }
    }

    // ──────────────────────────────────────────────
    // Interaction
    // ──────────────────────────────────────────────

    /**
     * Chamado quando um jogador minera um bloco.
     * Se o local era uma isca, marca como triggered e retorna a isca.
     *
     * @param player   Jogador que minerou
     * @param location Localização do bloco minerado
     * @return A isca se o local era uma isca, null caso contrário
     */
    public Decoy onPlayerMinedBlock(Player player, Location location) {
        UUID playerId = player.getUniqueId();
        List<Decoy> decoys = activeDecoys.get(playerId);
        if (decoys == null) return null;

        synchronized (decoys) {
            Iterator<Decoy> iterator = decoys.iterator();
            while (iterator.hasNext()) {
                Decoy decoy = iterator.next();
                if (isSameLocation(decoy.getLocation(), location)) {
                    decoy.setTriggered(true);
                    iterator.remove();
                    return decoy;
                }
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────

    /**
     * Remove iscas expiradas (mais de 30s).
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<Decoy>>> entryIterator = activeDecoys.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<UUID, List<Decoy>> entry = entryIterator.next();
            List<Decoy> decoys = entry.getValue();
            synchronized (decoys) {
                decoys.removeIf(decoy -> now - decoy.getCreatedAt() > DECOY_TTL_MS);
                if (decoys.isEmpty()) {
                    entryIterator.remove();
                }
            }
        }
    }

    /**
     * Remove todas as iscas de um jogador.
     */
    public void clearPlayer(UUID playerId) {
        List<Decoy> decoys = activeDecoys.remove(playerId);
        if (decoys != null) {
            synchronized (decoys) {
                decoys.clear();
            }
        }
    }

    /**
     * Remove todas as iscas de todos os jogadores.
     */
    public void clearAll() {
        activeDecoys.clear();
    }

    // ──────────────────────────────────────────────
    // Counts
    // ──────────────────────────────────────────────

    public int getActiveDecoyCount(UUID playerId) {
        List<Decoy> decoys = activeDecoys.get(playerId);
        if (decoys == null) return 0;
        synchronized (decoys) {
            return decoys.size();
        }
    }

    public int getTotalActiveDecoys() {
        int total = 0;
        for (List<Decoy> decoys : activeDecoys.values()) {
            synchronized (decoys) {
                total += decoys.size();
            }
        }
        return total;
    }

    public Set<UUID> getTrackedPlayers() {
        return Collections.unmodifiableSet(new HashSet<>(activeDecoys.keySet()));
    }

    // ──────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────

    /**
     * Encontra um local aleatório próximo que não esteja na linha de visão direta do jogador.
     */
    private Location findDecoyLocation(Location near, Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location playerLoc = player.getLocation();

        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = rng.nextInt(-MAX_DISTANCE, MAX_DISTANCE + 1);
            int dy = rng.nextInt(-3, 4);
            int dz = rng.nextInt(-MAX_DISTANCE, MAX_DISTANCE + 1);

            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < MIN_DISTANCE || dist > MAX_DISTANCE) continue;

            Location candidate = near.clone().add(dx, dy, dz);
            Block block = candidate.getBlock();

            // Só coloca isca em blocos substituíveis (ar, água, vegetação)
            if (block.getType().isAir() || block.isLiquid() || !block.getType().isOccluding()) {
                // Verifica se o bloco NÃO está na linha de visão direta do jogador
                if (!isInLineOfSight(playerLoc, candidate)) {
                    return candidate.toBlockLocation().add(0.5, 0.5, 0.5);
                }
            }
        }
        return null;
    }

    /**
     * Verifica se um local alvo está na linha de visão direta do jogador.
     * Usa um raycast simples.
     */
    private boolean isInLineOfSight(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 1.0) return true;

        direction.normalize();
        int steps = (int) Math.ceil(distance);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location point = from.clone().add(direction.clone().multiply(t * distance));
            if (!point.getBlock().getType().isAir()) {
                return false; // obstruído
            }
        }
        return true; // linha desobstruída
    }

    /**
     * Agenda a remoção automática de uma isca após TTL.
     */
    private void scheduleDecoyRemoval(Player player, Decoy decoy) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            UUID playerId = player.getUniqueId();
            List<Decoy> decoys = activeDecoys.get(playerId);
            if (decoys == null) return;

            synchronized (decoys) {
                decoys.removeIf(d -> isSameLocation(d.getLocation(), decoy.getLocation()));
                if (decoys.isEmpty()) {
                    activeDecoys.remove(playerId);
                }
            }

            // Restaura o bloco real (opcional — o client eventualmente atualiza)
            player.sendBlockChange(decoy.getLocation(), decoy.getLocation().getBlock().getBlockData());
        }, DECOY_TTL_MS / 50L); // ticks = ms / 50
    }

    /**
     * Compara duas localizações (apenas x, y, z inteiros, ignorando mundo).
     */
    private boolean isSameLocation(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
