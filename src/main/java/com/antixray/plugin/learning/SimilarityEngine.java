package com.antixray.plugin.learning;

import java.util.*;

public class SimilarityEngine {

    public SimilarityResult compare(Fingerprint a, Fingerprint b) {
        double[] va = a.toVector();
        double[] vb = b.toVector();

        double cosine = cosineSimilarity(va, vb);
        double euclidean = 1.0 / (1.0 + euclideanDistance(va, vb));
        double featureOverlap = featureOverlap(a, b);

        double finalScore = 0.4 * cosine + 0.3 * euclidean + 0.3 * featureOverlap;

        List<FeatureContribution> top = topContributions(a, b, 5);

        return new SimilarityResult(finalScore, cosine, euclidean, featureOverlap, top);
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, nA = 0, nB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            nA += a[i] * a[i];
            nB += b[i] * b[i];
        }
        double denom = Math.sqrt(nA) * Math.sqrt(nB);
        return denom == 0 ? 0 : dot / denom;
    }

    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    private double featureOverlap(Fingerprint a, Fingerprint b) {
        var scoresA = a.detectorScores();
        var scoresB = b.detectorScores();
        if (scoresA.isEmpty() || scoresB.isEmpty()) return 0;

        int match = 0, total = 0;
        for (var key : scoresA.keySet()) {
            Double vb = scoresB.get(key);
            if (vb != null) {
                total++;
                double diff = Math.abs(scoresA.get(key) - vb);
                if (diff < 20) match++;
            }
        }
        return total > 0 ? (double) match / total : 0;
    }

    private List<FeatureContribution> topContributions(Fingerprint a, Fingerprint b, int n) {
        List<FeatureContribution> list = new ArrayList<>();

        double diffBlocks = Math.abs(a.blocksPerMinute() - b.blocksPerMinute());
        double diffOres = Math.abs(a.oresPerMinute() - b.oresPerMinute());
        double diffHidden = Math.abs(a.hiddenOreRatio() - b.hiddenOreRatio());
        double diffTunnel = Math.abs(a.tunnelStraightness() - b.tunnelStraightness());
        double diffCave = Math.abs(a.caveExplorationRatio() - b.caveExplorationRatio());
        double diffMovement = Math.abs(a.movementEntropy() - b.movementEntropy());
        double diffOreSpace = Math.abs(a.oreSpaceCorrelation() - b.oreSpaceCorrelation());
        double diffExploration = Math.abs(a.explorationEfficiency() - b.explorationEfficiency());

        double ref = Math.max(1, diffBlocks + diffOres + diffHidden + diffTunnel + diffCave + diffMovement + diffOreSpace + diffExploration);

        addIf(list, "BlocksPerMin", diffBlocks, ref);
        addIf(list, "OresPerMin", diffOres, ref);
        addIf(list, "HiddenOreRatio", diffHidden, ref);
        addIf(list, "TunnelStraightness", diffTunnel, ref);
        addIf(list, "CaveExploration", diffCave, ref);
        addIf(list, "MovementEntropy", diffMovement, ref);
        addIf(list, "OreSpaceCorrelation", diffOreSpace, ref);
        addIf(list, "ExplorationEfficiency", diffExploration, ref);

        var scoresA = a.detectorScores();
        var scoresB = b.detectorScores();
        for (var key : scoresA.keySet()) {
            Double vb = scoresB.get(key);
            if (vb != null) {
                double diff = Math.abs(scoresA.get(key) - vb);
                addIf(list, key, diff, ref);
            }
        }

        list.sort(Comparator.comparingDouble(FeatureContribution::contribution).reversed());
        if (list.size() > n) list = list.subList(0, n);
        return list;
    }

    private void addIf(List<FeatureContribution> list, String name, double diff, double ref) {
        double contribution = Math.min(1.0, diff / ref);
        list.add(new FeatureContribution(name, contribution));
    }

    public record FeatureContribution(String feature, double contribution) {}
    public record SimilarityResult(double finalScore, double cosine, double euclidean, double featureOverlap,
                                   List<FeatureContribution> topContributions) {

        public String explain() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("§8Similarity: §c%.0f%%§8 (cosine=%.0f euclidean=%.0f overlap=%.0f)",
                    finalScore * 100, cosine * 100, euclidean * 100, featureOverlap * 100));
            sb.append("\n§7Why:\n");
            for (FeatureContribution fc : topContributions) {
                String prefix = fc.contribution > 0.15 ? "§c+" : "§7";
                sb.append("  ").append(prefix).append(String.format("%.0f%%", fc.contribution * 100))
                        .append(" §7").append(fc.feature).append("\n");
            }
            return sb.toString();
        }
    }
}
