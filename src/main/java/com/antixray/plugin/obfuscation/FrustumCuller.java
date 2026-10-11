package com.antixray.plugin.obfuscation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementa view frustum culling — determina se blocos estão dentro do cone de visão
 * (frustum) do jogador, evitando processar blocos que o jogador não pode ver.
 * <p>
 * O frustum é definido por um campo de visão (FOV) configurável, padrao 90 graus.
 */
public class FrustumCuller {

    private final double fov;

    /** Cache de direção do jogador por tick. */
    private final Map<UUID, CachedDirection> directionCache = new ConcurrentHashMap<>();

    private static class CachedDirection {
        final Vector direction;
        final long tick;
        final Location eyeLocation;

        CachedDirection(Vector direction, long tick, Location eyeLocation) {
            this.direction = direction;
            this.tick = tick;
            this.eyeLocation = eyeLocation;
        }
    }

    /**
     * Construtor com FOV padrão de 90 graus.
     */
    public FrustumCuller() {
        this(90.0);
    }

    /**
     * Construtor com FOV personalizado.
     *
     * @param fov campo de visão em graus. Deve estar entre 1 e 360.
     * @throws IllegalArgumentException se FOV estiver fora do intervalo válido.
     */
    public FrustumCuller(double fov) {
        if (fov <= 0 || fov > 360) {
            throw new IllegalArgumentException("FOV must be between 1 and 360 degrees, got: " + fov);
        }
        this.fov = fov;
    }

    /**
     * Construtor para compatibilidade com ObfuscationModule.
     *
     * @param plugin Plugin (não utilizado, apenas para compatibilidade).
     */
    public FrustumCuller(Plugin plugin) {
        this(90.0);
    }

    /**
     * Retorna o FOV configurado.
     *
     * @return FOV em graus.
     */
    public double getFov() {
        return fov;
    }

    /**
     * Verifica se um bloco está dentro do frustum de visão do jogador.
     * <p>
     * Calcula o ângulo entre a direção do olhar do jogador e a direção do bloco.
     * Se o ângulo for maior que FOV/2, o bloco está fora do frustum (portanto não visível).
     *
     * @param player         jogador.
     * @param blockLocation  localização do bloco.
     * @return {@code true} se o bloco estiver dentro do frustum de visão.
     */
    public boolean isInViewFrustum(Player player, Location blockLocation) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(blockLocation, "Block location cannot be null");

        // Se estiver atrás, já descarta rápido
        if (isBehindPlayer(player, blockLocation)) {
            return false;
        }

        double angle = getAngleToPlayer(player, blockLocation);

        // Trata NaN (pode ocorrer se dot product for ligeiramente > 1 ou < -1)
        if (Double.isNaN(angle)) {
            return false;
        }

        return angle <= fov / 2.0;
    }

    /**
     * Filtra uma coleção de localizações, retornando apenas aquelas que estão
     * dentro do frustum de visão do jogador.
     *
     * @param player     jogador.
     * @param candidates coleção de localizações candidatas.
     * @return conjunto de localizações dentro do frustum.
     */
    public Set<Location> cullOutOfView(Player player, Collection<Location> candidates) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(candidates, "Candidates cannot be null");

        if (candidates.isEmpty()) {
            return Collections.emptySet();
        }

        return candidates.parallelStream()
                .filter(loc -> loc != null)
                .filter(loc -> isInViewFrustum(player, loc))
                .collect(Collectors.toSet());
    }

    /**
     * Calcula o ângulo em graus entre a direção do olhar do jogador e a direção
     * de um bloco alvo.
     * <p>
     * Retorna 0 se o jogador está olhando diretamente para o bloco.
     * Retorna valores próximos de 180 se o bloco está diretamente atrás.
     *
     * @param player         jogador.
     * @param blockLocation  localização do bloco.
     * @return ângulo em graus entre 0 e 180 (ou NaN se impossível calcular).
     */
    public double getAngleToPlayer(Player player, Location blockLocation) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(blockLocation, "Block location cannot be null");

        // Obtém direção do cache (evita recalcular no mesmo tick)
        long currentTick = Bukkit.getCurrentTick();
        CachedDirection cached = directionCache.get(player.getUniqueId());
        Vector lookDirection;
        Location eyeLocation;

        if (cached != null && cached.tick == currentTick) {
            lookDirection = cached.direction;
            eyeLocation = cached.eyeLocation;
        } else {
            eyeLocation = player.getEyeLocation();
            lookDirection = eyeLocation.getDirection().normalize();
            directionCache.put(player.getUniqueId(), new CachedDirection(lookDirection, currentTick, eyeLocation));
        }

        Vector toBlock = blockLocation.toVector().subtract(eyeLocation.toVector());

        // Se distância for quase zero, jogador está dentro do bloco — considera ângulo 0
        double distanceSquared = toBlock.lengthSquared();
        if (distanceSquared < 0.0001) {
            return 0.0;
        }

        toBlock.normalize();

        double dot = lookDirection.dot(toBlock);

        // Clamp para evitar NaN do acos quando dot está fora de [-1, 1]
        // por imprecisão de ponto flutuante
        dot = Math.max(-1.0, Math.min(1.0, dot));

        return Math.toDegrees(Math.acos(dot));
    }

    /**
     * Verifica se o jogador está olhando diretamente para um bloco, dentro de
     * um limiar (threshold) em graus.
     * <p>
     * Útil para o {@code DecoyDetector} — se o jogador estiver olhando fixamente
     * para um bloco suspeito, pode indicar uso de x-ray.
     *
     * @param player            jogador.
     * @param blockLocation     localização do bloco.
     * @param thresholdDegrees  limiar em graus (ex: 5 para olhar direto).
     * @return {@code true} se o ângulo for menor ou igual ao threshold.
     */
    public boolean isLookingAt(Player player, Location blockLocation, double thresholdDegrees) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(blockLocation, "Block location cannot be null");

        if (thresholdDegrees < 0) {
            throw new IllegalArgumentException("Threshold must be non-negative, got: " + thresholdDegrees);
        }

        double angle = getAngleToPlayer(player, blockLocation);

        if (Double.isNaN(angle)) {
            return false;
        }

        return angle <= thresholdDegrees;
    }

    /**
     * Verifica se um bloco está atrás do jogador (produto escalar negativo
     * entre a direção do olhar e a direção do bloco).
     *
     * @param player         jogador.
     * @param blockLocation  localização do bloco.
     * @return {@code true} se o bloco estiver atrás do jogador.
     */
    public boolean isBehindPlayer(Player player, Location blockLocation) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(blockLocation, "Block location cannot be null");

        // Usa o mesmo cache de direção do getAngleToPlayer
        long currentTick = Bukkit.getCurrentTick();
        CachedDirection cached = directionCache.get(player.getUniqueId());
        Vector lookDirection;
        Location eyeLocation;

        if (cached != null && cached.tick == currentTick) {
            lookDirection = cached.direction;
            eyeLocation = cached.eyeLocation;
        } else {
            eyeLocation = player.getEyeLocation();
            lookDirection = eyeLocation.getDirection().normalize();
            directionCache.put(player.getUniqueId(), new CachedDirection(lookDirection, currentTick, eyeLocation));
        }

        Vector toBlock = blockLocation.toVector().subtract(eyeLocation.toVector());

        double distanceSquared = toBlock.lengthSquared();
        if (distanceSquared < 0.0001) {
            return false; // Mesma posição — não está atrás
        }

        toBlock.normalize();

        double dot = lookDirection.dot(toBlock);

        // Se dot < 0, o bloco está atrás do jogador
        return dot < 0;
    }

    /**
     * Limpa o cache de direções dos jogadores.
     */
    public void clearDirectionCache() {
        directionCache.clear();
    }

    @Override
    public String toString() {
        return "FrustumCuller{" +
                "fov=" + fov +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FrustumCuller that = (FrustumCuller) o;
        return Double.compare(that.fov, fov) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fov);
    }
}
