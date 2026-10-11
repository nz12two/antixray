package com.antixray.plugin.obfuscation;

import com.antixray.plugin.AntiXrayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia quais blocos estão <b>revelados</b> (mostrando material real)
 * ou <b>ocultos</b> (mostrando material falso) para cada jogador.
 * <p>
 * Cada bloco é identificado por uma chave long compactada (22 bits x, 10 bits y, 22 bits z).
 * <p>
 * <b>Envio de pacotes:</b> usa a API {@link Player#sendBlockChange(Location, BlockData)}
 * do Paper, que funciona <b>sem ProtocolLib</b>. Quando múltiplos blocos
 * precisam ser atualizados simultaneamente, utiliza
 * {@link Player#sendMultiBlockChange(Map)} com fallback para versões antigas.
 * <p>
 * Thread-safe: todos os mapas são {@link ConcurrentHashMap}.
 */
public class BlockRevealManager {

    private final AntiXrayPlugin plugin;

    // playerId -> Set<Long> de blocos atualmente revelados (mostram material real)
    private final Map<UUID, Set<Long>> revealedBlocks = new ConcurrentHashMap<>();

    // playerId -> Long -> material real (para restore rápido em revealAll)
    private final Map<UUID, Map<Long, Material>> revealedMaterials = new ConcurrentHashMap<>();

    // playerId -> Set<Long> de blocos atualmente ocultos (mostram material falso)
    private final Map<UUID, Set<Long>> hiddenBlocks = new ConcurrentHashMap<>();

    // playerId -> Long -> material falso (para restore rápido)
    private final Map<UUID, Map<Long, Material>> hiddenMaterials = new ConcurrentHashMap<>();

    /**
     * @param plugin Instância do plugin AntiXray.
     */
    public BlockRevealManager(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------------------
    // Codificação/decodificação de chave long
    // --------------------------------------------------------------

    /**
     * Gera a chave long compactada para um bloco.
     * 22 bits para x, 10 bits para y, 22 bits para z.
     */
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0x3FF) << 32) | (long) (z & 0x3FFFFF);
    }

    /**
     * Decodifica a coordenada X de uma chave long.
     */
    public static int decodeX(long key) {
        return (int) (key >> 42);
    }

    /**
     * Decodifica a coordenada Y de uma chave long.
     */
    public static int decodeY(long key) {
        return (int) ((key >> 32) & 0x3FF);
    }

    /**
     * Decodifica a coordenada Z de uma chave long.
     */
    public static int decodeZ(long key) {
        return (int) (key << 42 >> 42);
    }

    // --------------------------------------------------------------
    // Consulta
    // --------------------------------------------------------------

    /**
     * Verifica se um bloco está <b>revelado</b> (mostrando material real)
     * para o jogador.
     *
     * @param playerId UUID do jogador.
     * @param x        Coordenada X do bloco.
     * @param y        Coordenada Y do bloco.
     * @param z        Coordenada Z do bloco.
     * @return {@code true} se o bloco estiver revelado.
     */
    public boolean isRevealed(UUID playerId, int x, int y, int z) {
        Set<Long> playerSet = revealedBlocks.get(playerId);
        return playerSet != null && playerSet.contains(key(x, y, z));
    }

    /**
     * Verifica se um bloco está <b>oculto</b> (mostrando material falso)
     * para o jogador.
     *
     * @param playerId UUID do jogador.
     * @param x        Coordenada X do bloco.
     * @param y        Coordenada Y do bloco.
     * @param z        Coordenada Z do bloco.
     * @return {@code true} se o bloco estiver oculto.
     */
    public boolean isHidden(UUID playerId, int x, int y, int z) {
        Set<Long> playerSet = hiddenBlocks.get(playerId);
        return playerSet != null && playerSet.contains(key(x, y, z));
    }

    // --------------------------------------------------------------
    // Ações: revelar / ocultar
    // --------------------------------------------------------------

    /**
     * Revela um bloco para o jogador, enviando o material real via packet.
     * <p>
     * Remove o bloco da lista de ocultos (se existir) e o adiciona
     * à lista de revelados.
     *
     * @param playerId     UUID do jogador.
     * @param x            Coordenada X.
     * @param y            Coordenada Y.
     * @param z            Coordenada Z.
     * @param realMaterial Material real do bloco a ser mostrado.
     */
    public void revealBlock(UUID playerId, int x, int y, int z, Material realMaterial) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        long blockKey = key(x, y, z);

        // Atualiza mapas de tracking
        revealedBlocks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(blockKey);
        revealedMaterials.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(blockKey, realMaterial);

        Set<Long> hiddenSet = hiddenBlocks.get(playerId);
        if (hiddenSet != null) {
            hiddenSet.remove(blockKey);
        }
        Map<Long, Material> hiddenMatMap = hiddenMaterials.get(playerId);
        if (hiddenMatMap != null) {
            hiddenMatMap.remove(blockKey);
        }

        // Envia material real para o jogador (sem ProtocolLib!)
        Location loc = new Location(player.getWorld(), x, y, z);
        player.sendBlockChange(loc, realMaterial.createBlockData());
    }

    /**
     * Oculta um bloco para o jogador, enviando um material falso via packet.
     * <p>
     * Remove o bloco da lista de revelados (se existir) e o adiciona
     * à lista de ocultos.
     *
     * @param playerId    UUID do jogador.
     * @param x           Coordenada X.
     * @param y           Coordenada Y.
     * @param z           Coordenada Z.
     * @param fakeMaterial Material falso a ser mostrado (ex: STONE, NETHERRACK).
     */
    public void hideBlock(UUID playerId, int x, int y, int z, Material fakeMaterial) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        long blockKey = key(x, y, z);

        // Atualiza mapas de tracking
        hiddenBlocks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(blockKey);
        hiddenMaterials.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(blockKey, fakeMaterial);

        Set<Long> revealedSet = revealedBlocks.get(playerId);
        if (revealedSet != null) {
            revealedSet.remove(blockKey);
        }
        Map<Long, Material> revealedMatMap = revealedMaterials.get(playerId);
        if (revealedMatMap != null) {
            revealedMatMap.remove(blockKey);
        }

        // Envia material falso para o jogador
        Location loc = new Location(player.getWorld(), x, y, z);
        player.sendBlockChange(loc, fakeMaterial.createBlockData());
    }

    /**
     * Adiciona um bloco ao tracking de <b>ocultos</b> <u>sem</u> enviar packet.
     * <p>
     * Usado pelo {@link ChunkPacketInterceptor} quando já modificou o dado
     * diretamente no pacote e precisa apenas manter o tracking.
     *
     * @param playerId    UUID do jogador.
     * @param x           Coordenada X.
     * @param y           Coordenada Y.
     * @param z           Coordenada Z.
     * @param fakeMaterial Material falso exibido ao jogador.
     */
    public void trackHiddenBlock(UUID playerId, int x, int y, int z, Material fakeMaterial) {
        long blockKey = key(x, y, z);
        hiddenBlocks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(blockKey);
        hiddenMaterials.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(blockKey, fakeMaterial);

        // Garante que não está na lista de revelados
        Set<Long> revealedSet = revealedBlocks.get(playerId);
        if (revealedSet != null) {
            revealedSet.remove(blockKey);
        }
        Map<Long, Material> revealedMatMap = revealedMaterials.get(playerId);
        if (revealedMatMap != null) {
            revealedMatMap.remove(blockKey);
        }
    }

    // --------------------------------------------------------------
    // Ações em massa
    // --------------------------------------------------------------

    /**
     * Revela <b>todos</b> os blocos ocultos para o jogador.
     * <p>
     * Utiliza {@link Player#sendMultiBlockChange(Map)} quando disponível
     * (Paper 1.16.5+) para enviar múltiplas alterações num único packet.
     * Faz fallback para {@link Player#sendBlockChange(Location, BlockData)}
     * individual em versões antigas.
     *
     * @param playerId UUID do jogador.
     */
    public void revealAll(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            // Jogador offline: apenas limpa dados
            clearPlayer(playerId);
            return;
        }

        Map<Long, Material> hiddenMatMap = hiddenMaterials.get(playerId);
        if (hiddenMatMap == null || hiddenMatMap.isEmpty()) return;

        World world = player.getWorld();
        Map<Location, BlockData> multiChange = new HashMap<>();

        for (Map.Entry<Long, Material> entry : hiddenMatMap.entrySet()) {
            long blockKey = entry.getKey();
            int bx = decodeX(blockKey);
            int by = decodeY(blockKey);
            int bz = decodeZ(blockKey);

            // Material real do mundo
            Material realMaterial = world.getBlockAt(bx, by, bz).getType();
            Location loc = new Location(world, bx, by, bz);
            multiChange.put(loc, realMaterial.createBlockData());

            // Atualiza tracking
            revealedBlocks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(blockKey);
            revealedMaterials.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(blockKey, realMaterial);
        }

        // Envia multi-block-change quando possível
        if (!multiChange.isEmpty()) {
            try {
                // Paper 1.16.5+ — método sendMultiBlockChange
                player.sendMultiBlockChange(multiChange);
            } catch (NoSuchMethodError e) {
                // Fallback para versões antigas
                for (Map.Entry<Location, BlockData> change : multiChange.entrySet()) {
                    player.sendBlockChange(change.getKey(), change.getValue());
                }
            }
        }

        // Limpa tracking de ocultos (tudo foi revelado)
        hiddenBlocks.remove(playerId);
        hiddenMaterials.remove(playerId);
    }

    /**
     * Remove <b>todo</b> o tracking de um jogador (revelados e ocultos).
     *
     * @param playerId UUID do jogador.
     */
    public void clearPlayer(UUID playerId) {
        revealedBlocks.remove(playerId);
        revealedMaterials.remove(playerId);
        hiddenBlocks.remove(playerId);
        hiddenMaterials.remove(playerId);
    }

    /**
     * Remove dados de todos os jogadores que estão <b>offline</b>.
     * Chamado em {@code onDisable()} para limpeza geral.
     */
    public void cleanup() {
        Iterator<UUID> it = revealedBlocks.keySet().iterator();
        while (it.hasNext()) {
            UUID playerId = it.next();
            if (Bukkit.getPlayer(playerId) == null) {
                it.remove();
                revealedMaterials.remove(playerId);
                hiddenBlocks.remove(playerId);
                hiddenMaterials.remove(playerId);
            }
        }
    }

    // --------------------------------------------------------------
    // Acesso a dados internos (para ChunkPacketInterceptor e ObfuscationModule)
    // --------------------------------------------------------------

    /**
     * Retorna os blocos atualmente revelados para o jogador.
     *
     * @param playerId UUID do jogador.
     * @return Set de chaves long ou conjunto vazio se nenhum.
     */
    public Set<Long> getRevealedBlockKeys(UUID playerId) {
        return revealedBlocks.getOrDefault(playerId, Collections.emptySet());
    }

    /**
     * Retorna os blocos atualmente ocultos para o jogador.
     *
     * @param playerId UUID do jogador.
     * @return Set de chaves long ou conjunto vazio se nenhum.
     */
    public Set<Long> getHiddenBlockKeys(UUID playerId) {
        return hiddenBlocks.getOrDefault(playerId, Collections.emptySet());
    }

    /**
     * Retorna o material real de um bloco revelado para o jogador.
     *
     * @param playerId UUID do jogador.
     * @param x        Coordenada X.
     * @param y        Coordenada Y.
     * @param z        Coordenada Z.
     * @return Material real, ou {@code null} se não estiver revelado.
     */
    public Material getRevealedMaterial(UUID playerId, int x, int y, int z) {
        Map<Long, Material> matMap = revealedMaterials.get(playerId);
        return matMap != null ? matMap.get(key(x, y, z)) : null;
    }

    /**
     * Retorna o material falso de um bloco oculto para o jogador.
     *
     * @param playerId UUID do jogador.
     * @param x        Coordenada X.
     * @param y        Coordenada Y.
     * @param z        Coordenada Z.
     * @return Material falso, ou {@code null} se não estiver oculto.
     */
    public Material getHiddenMaterial(UUID playerId, int x, int y, int z) {
        Map<Long, Material> matMap = hiddenMaterials.get(playerId);
        return matMap != null ? matMap.get(key(x, y, z)) : null;
    }
}
