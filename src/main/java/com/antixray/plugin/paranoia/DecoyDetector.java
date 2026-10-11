package com.antixray.plugin.paranoia;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Detector que identifica jogadores que se desviam da rota para minerar iscas falsas.
 * <p>
 * Funcionamento:
 * <ul>
 *   <li>Quando um jogador minera uma isca, calcula o desvio da rota original</li>
 *   <li>Analisa o ângulo de rotação antes/depois de minerar a isca</li>
 *   <li>Verifica se havia minérios reais no caminho original</li>
 * </ul>
 * <p>
 * Este detector é acionado pelo DecoyManager — não realiza análise periódica.
 * Os resultados são armazenados em buffer e processados no método analyze().
 */
public class DecoyDetector extends Detector {

    private final DecoyManager decoyManager;
    private final Map<UUID, List<DetectionResult>> pendingResults;

    private static final int PATH_HISTORY_SIZE = 20;
    private static final double SIGNIFICANT_DEVIATION_THRESHOLD = 3.0;

    public DecoyDetector(DecoyManager decoyManager) {
        super("DecoyDetector", "Detects players who deviate path to mine fake decoy blocks");
        this.category = "behavioral";
        this.decoyManager = Objects.requireNonNull(decoyManager, "decoyManager must not be null");
        this.pendingResults = new ConcurrentHashMap<>();
    }

    // ──────────────────────────────────────────────
    // Analyze (called by DetectionModule periodically)
    // ──────────────────────────────────────────────

    /**
     * Processa resultados pendentes acumulados para este jogador.
     * Retorna o resultado com maior score, ou um resultado vazio se não houver nada pendente.
     */
    @Override
    public DetectionResult analyze(PlayerData data) {
        UUID playerId = data.getPlayerId();
        List<DetectionResult> results = pendingResults.remove(playerId);

        if (results == null || results.isEmpty()) {
            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(0)
                    .confidence(0)
                    .reason("No decoy interactions detected")
                    .build();
        }

        // Retorna o resultado com maior score
        return results.stream()
                .max(Comparator.comparingDouble(DetectionResult::getScore))
                .orElse(null);
    }

    // ──────────────────────────────────────────────
    // Report decoy mine (called by external code)
    // ──────────────────────────────────────────────

    /**
     * Reporta que um jogador minerou uma isca.
     * Cria um DetectionResult e armazena no buffer pendente.
     *
     * @param player      O jogador que minerou a isca
     * @param decoy       A isca que foi minerada
     * @param deviation   Desvio em blocos da rota original
     * @param pathBefore  Pontos do caminho do jogador antes de minerar
     */
    public void reportDecoyMine(Player player, DecoyManager.Decoy decoy,
                                double deviation, Location[] pathBefore) {
        if (player == null || decoy == null) return;

        UUID playerId = player.getUniqueId();
        Location decoyLocation = decoy.getLocation();

        // Calcula score baseado no desvio
        double score = calculateScore(deviation);

        // Calcula confiança baseada em quantas iscas o jogador já minerou
        double confidence = calculateConfidence(playerId, decoyLocation);

        // Monta o reason detalhado
        String reason = String.format(
                "Mined fake decoy ore at [%d, %d, %d] with %.1f blocks deviation from path",
                decoyLocation.getBlockX(),
                decoyLocation.getBlockY(),
                decoyLocation.getBlockZ(),
                deviation
        );

        // Verifica se havia minérios reais no caminho original (atenua suspeita)
        boolean hadRealOresOnPath = hasRealOresOnPath(pathBefore);

        // Analisa ângulo de rotação antes/depois
        double turnAngle = estimateTurnAngle(pathBefore, decoyLocation);

        DetectionResult result = new DetectionResult.Builder()
                .detectorName(getName())
                .score(score)
                .confidence(confidence)
                .reason(reason)
                .addEvidence("Decoy location: " + locationToString(decoyLocation))
                .addEvidence("Decoy material: " + decoy.getFakeMaterial().name())
                .addEvidence("Deviation: " + String.format("%.1f blocks", deviation))
                .addEvidence("Turn angle: " + String.format("%.1f degrees", turnAngle))
                .addEvidence("Real ores on path: " + (hadRealOresOnPath ? "yes" : "no"))
                .addMetadata("decoyX", (double) decoyLocation.getBlockX())
                .addMetadata("decoyY", (double) decoyLocation.getBlockY())
                .addMetadata("decoyZ", (double) decoyLocation.getBlockZ())
                .addMetadata("deviation", deviation)
                .addMetadata("turnAngle", turnAngle)
                .addMetadata("hadRealOresOnPath", hadRealOresOnPath ? 1.0 : 0.0)
                .addMetadata("fakeMaterial", decoy.getFakeMaterial().name())
                .build();

        // Armazena no buffer
        pendingResults.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(result);
    }

    // ──────────────────────────────────────────────
    // Pending results management
    // ──────────────────────────────────────────────

    /**
     * Retorna todos os resultados pendentes (sem removê-los).
     */
    public Map<UUID, List<DetectionResult>> getPendingResults() {
        Map<UUID, List<DetectionResult>> copy = new HashMap<>();
        for (Map.Entry<UUID, List<DetectionResult>> entry : pendingResults.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Limpa resultados pendentes de um jogador específico.
     */
    public void clearPending(UUID playerId) {
        pendingResults.remove(playerId);
    }

    /**
     * Limpa todos os resultados pendentes.
     */
    public void clearAllPending() {
        pendingResults.clear();
    }

    /**
     * Retorna o número de resultados pendentes para um jogador.
     */
    public int getPendingCount(UUID playerId) {
        List<DetectionResult> results = pendingResults.get(playerId);
        return results != null ? results.size() : 0;
    }

    // ──────────────────────────────────────────────
    // Score calculation
    // ──────────────────────────────────────────────

    /**
     * Calcula o score (0-100) baseado no desvio em blocos.
     * <ul>
     *   <li>3-5 blocos: 40-60 score</li>
     *   <li>5-10 blocos: 60-80 score</li>
     *   <li>&gt;10 blocos: 80-100 score</li>
     * </ul>
     */
    private double calculateScore(double deviation) {
        if (deviation <= SIGNIFICANT_DEVIATION_THRESHOLD) {
            return 0;
        }

        if (deviation <= 5.0) {
            // 3-5 blocos: 40-60 (mapeamento linear)
            double t = (deviation - SIGNIFICANT_DEVIATION_THRESHOLD) / (5.0 - SIGNIFICANT_DEVIATION_THRESHOLD);
            return 40.0 + t * 20.0;
        } else if (deviation <= 10.0) {
            // 5-10 blocos: 60-80
            double t = (deviation - 5.0) / (10.0 - 5.0);
            return 60.0 + t * 20.0;
        } else {
            // > 10 blocos: 80-100, limitado a 100
            double excess = Math.min(deviation - 10.0, 10.0);
            return Math.min(100.0, 80.0 + (excess / 10.0) * 20.0);
        }
    }

    /**
     * Calcula a confiança (0-100) baseada em quantas iscas o jogador já minerou.
     * <ul>
     *   <li>1 isca: 30%</li>
     *   <li>2-3 iscas: 50-70%</li>
     *   <li>4+ iscas: 80-95%</li>
     * </ul>
     */
    private double calculateConfidence(UUID playerId, Location lastDecoyLocation) {
        // Conta quantas vezes este jogador já apareceu nos resultados pendentes
        int decoyCount = getPendingCount(playerId);

        // Também conta iscas triggered no DecoyManager
        List<DecoyManager.Decoy> playerDecoys = decoyManager.getDecoys(playerId);
        long triggeredCount = playerDecoys.stream().filter(DecoyManager.Decoy::isTriggered).count();

        int totalDecoyMines = decoyCount + (int) triggeredCount;

        if (totalDecoyMines <= 0) {
            return 30.0; // primeira vez, confiança mínima
        } else if (totalDecoyMines == 1) {
            return 30.0;
        } else if (totalDecoyMines == 2) {
            return 50.0;
        } else if (totalDecoyMines == 3) {
            return 70.0;
        } else if (totalDecoyMines == 4) {
            return 80.0;
        } else {
            // 5+ iscas: 80-95%
            return Math.min(95.0, 80.0 + (totalDecoyMines - 4) * 3.0);
        }
    }

    /**
     * Verifica se havia minérios reais no caminho original do jogador.
     * Se sim, o desvio é menos suspeito (atenua score).
     */
    private boolean hasRealOresOnPath(Location[] path) {
        if (path == null || path.length == 0) return false;

        Set<Material> oreTypes = new HashSet<>(Arrays.asList(
                Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
                Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
                Material.ANCIENT_DEBRIS
        ));

        for (Location loc : path) {
            if (loc == null || loc.getWorld() == null) continue;
            Material type = loc.getBlock().getType();
            if (oreTypes.contains(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Estima o ângulo de rotação do jogador em direção à isca.
     * Compara a direção do último segmento do caminho com a direção até a isca.
     */
    private double estimateTurnAngle(Location[] path, Location decoyLocation) {
        if (path == null || path.length < 2) return 0.0;

        // Últimos dois pontos do caminho para determinar direção
        Location last = null;
        Location secondLast = null;
        for (int i = path.length - 1; i >= 0; i--) {
            if (path[i] != null) {
                if (last == null) {
                    last = path[i];
                } else if (secondLast == null) {
                    secondLast = path[i];
                    break;
                }
            }
        }

        if (last == null || secondLast == null) return 0.0;

        // Direção do movimento antes da isca
        org.bukkit.util.Vector movementDir = last.toVector().subtract(secondLast.toVector());
        if (movementDir.lengthSquared() < 0.01) return 0.0;

        // Direção do último ponto até a isca
        org.bukkit.util.Vector toDecoyDir = decoyLocation.toVector().subtract(last.toVector());
        if (toDecoyDir.lengthSquared() < 0.01) return 0.0;

        // Ângulo entre as direções
        double dot = movementDir.normalize().dot(toDecoyDir.normalize());
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    // ──────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────

    private String locationToString(Location loc) {
        return String.format("[%d, %d, %d]", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
