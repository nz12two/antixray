package com.antixray.plugin.explanation;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.detection.result.ExplainVector;

import java.util.*;

public class ExplanationModule extends Module {
    private static final List<String> PENALTY_ORDER = List.of(
            "HiddenOreDetector", "WallTrackingDetector", "PathFindingDetector",
            "TunnelDetector", "LuckDetector", "BranchMiningDetector",
            "RareOreDetector", "DirectionDetector", "ExplorationDetector",
            "ClusterDetector", "OreRatioDetector", "SessionPatternDetector",
            "MiningEfficiencyDetector", "BehaviorDetector", "HistoricalBehaviorDetector",
            "RotationDetector", "SuspiciousTurnDetector", "MovementDetector",
            "CaveExplorationDetector",
            "OreTargetingDetector", "SystematicMiningDetector"
    );

    public ExplanationModule(AntiXrayPlugin plugin) {
        super("explanation");
    }

    @Override
    public void onEnable() {}
    @Override
    public void onDisable() {}

    public Explanation generate(UUID playerId, String playerName, double finalRisk,
                                List<DetectionResult> results, Map<String, Double> weights,
                                ExplainVector batchVector) {
        Explanation exp = new Explanation(playerId, playerName, finalRisk, batchVector);
        Set<String> accounted = new HashSet<>();

        for (String detector : PENALTY_ORDER) {
            for (DetectionResult r : results) {
                if (!r.getDetectorName().equals(detector)) continue;
                if (accounted.contains(detector)) continue;
                accounted.add(detector);
                double weight = weights.getOrDefault(detector.toLowerCase(), 10.0);
                double contribution = r.getScore() * (weight / 100.0) * (r.getConfidence() / 100.0);
                if (contribution > 1.0) {
                    exp.addContributor(detector, contribution, r.getReason());
                }
            }
        }

        exp.sortContributors();
        return exp;
    }

    public Explanation generate(UUID playerId, String playerName, double finalRisk,
                                List<DetectionResult> results, Map<String, Double> weights) {
        return generate(playerId, playerName, finalRisk, results, weights, null);
    }

    public static class Explanation {
        private final UUID playerId;
        private final String playerName;
        private final double finalRisk;
        private final ExplainVector explainVector;
        private final List<Contributor> contributors = new ArrayList<>();

        public Explanation(UUID playerId, String playerName, double finalRisk, ExplainVector explainVector) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.finalRisk = finalRisk;
            this.explainVector = explainVector;
        }

        public void addContributor(String detector, double contribution, String reason) {
            contributors.add(new Contributor(detector, contribution, reason));
        }

        public void sortContributors() {
            contributors.sort((a, b) -> Double.compare(b.contribution, a.contribution));
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public double getFinalRisk() { return finalRisk; }
        public ExplainVector getExplainVector() { return explainVector; }
        public List<Contributor> getContributors() { return contributors; }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("§8[§cAntiXray§8] §7Risk: §c").append(String.format("%.0f", finalRisk)).append("\n");
            sb.append("§7Why:\n");
            for (Contributor c : contributors) {
                String prefix = c.contribution > 0 ? "§c+" : "§a";
                sb.append("  ").append(prefix).append(String.format("%.0f", Math.abs(c.contribution)))
                        .append(" §7").append(c.detector.replace("Detector", ""))
                        .append(" §8- §7").append(c.reason).append("\n");
            }
            if (explainVector != null && !explainVector.getCategories().isEmpty()) {
                sb.append("§8Categories: ");
                explainVector.getCategories().forEach((cat, val) -> {
                    if (val > 5) sb.append(cat).append("=").append(String.format("%.0f", val)).append(" ");
                });
                sb.append("\n");
            }
            sb.append("§8Confidence requires score > 30 and confidence > 30 for punishment.");
            return sb.toString();
        }

        public String toJson() {
            return explainVector != null ? explainVector.toJson() : "{}";
        }

        public record Contributor(String detector, double contribution, String reason) {}
    }
}
