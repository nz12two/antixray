package com.antixray.plugin.detection.combiners;

import com.antixray.plugin.detection.DetectionContext;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.detection.result.ExplainVector;
import com.antixray.plugin.model.PlayerData;

import java.util.List;

public class SystematicMiningDetector extends MetaDetector {

    public SystematicMiningDetector() {
        super("SystematicMiningDetector", "Combines Tunnel + BranchMining + Direction to detect systematic mining",
                List.of("TunnelDetector", "BranchMiningDetector", "DirectionDetector"));
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        return null;
    }

    @Override
    public DetectionResult combine(List<DetectionResult> inputs, DetectionContext ctx) {
        double tunnel = 0, branch = 0, direction = 0;
        double tunnelC = 0, branchC = 0, directionC = 0;

        for (DetectionResult r : inputs) {
            switch (r.getDetectorName()) {
                case "TunnelDetector" -> { tunnel = r.getScore(); tunnelC = r.getConfidence(); }
                case "BranchMiningDetector" -> { branch = r.getScore(); branchC = r.getConfidence(); }
                case "DirectionDetector" -> { direction = r.getScore(); directionC = r.getConfidence(); }
            }
        }

        double avgScore = (tunnel + branch + direction) / 3.0;
        double avgConf = (tunnelC + branchC + directionC) / 3.0;
        double consistency = tunnel > 0 && branch > 0 && direction > 0 ? 1.2 : 1.0;
        double finalScore = Math.min(100, avgScore * consistency);

        ExplainVector vec = new ExplainVector();
        vec.addContribution("TunnelDetector", tunnel);
        vec.addContribution("BranchMiningDetector", branch);
        vec.addContribution("DirectionDetector", direction);
        vec.addCategory("behavioral", tunnel + branch + direction);

        return new DetectionResult.Builder()
                .detectorName(getName())
                .score(finalScore)
                .confidence(avgConf)
                .reason("Systematic mining (Tunnel=" + String.format("%.0f", tunnel)
                        + " Branch=" + String.format("%.0f", branch)
                        + " Direction=" + String.format("%.0f", direction) + ")")
                .addEvidence("Systematic: consistency=" + String.format("%.2f", consistency))
                .build();
    }
}
