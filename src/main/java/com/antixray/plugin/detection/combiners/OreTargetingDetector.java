package com.antixray.plugin.detection.combiners;

import com.antixray.plugin.detection.DetectionContext;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.detection.result.ExplainVector;
import com.antixray.plugin.model.PlayerData;

import java.util.List;

public class OreTargetingDetector extends MetaDetector {

    public OreTargetingDetector() {
        super("OreTargetingDetector", "Combines WallTracking + HiddenOre to detect ore-directed behavior",
                List.of("WallTrackingDetector", "HiddenOreDetector"));
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        return null;
    }

    @Override
    public DetectionResult combine(List<DetectionResult> inputs, DetectionContext ctx) {
        double wallScore = 0, hiddenScore = 0;
        double wallConf = 0, hiddenConf = 0;

        for (DetectionResult r : inputs) {
            switch (r.getDetectorName()) {
                case "WallTrackingDetector" -> { wallScore = r.getScore(); wallConf = r.getConfidence(); }
                case "HiddenOreDetector" -> { hiddenScore = r.getScore(); hiddenConf = r.getConfidence(); }
            }
        }

        double combinedScore = wallScore * 0.5 + hiddenScore * 0.5;
        double combinedConf = wallConf * 0.4 + hiddenConf * 0.6;
        double synergy = wallScore * hiddenScore / 2500.0;
        double finalScore = Math.min(100, combinedScore + synergy * 30);

        ExplainVector vec = new ExplainVector();
        vec.addContribution("WallTrackingDetector", wallScore * 0.5);
        vec.addContribution("HiddenOreDetector", hiddenScore * 0.5);
        vec.addCategory("spatial", wallScore * 0.5 + hiddenScore * 0.3);

        return new DetectionResult.Builder()
                .detectorName(getName())
                .score(finalScore)
                .confidence(combinedConf)
                .reason("Ore targeting behavior: WallTracking=" + String.format("%.0f", wallScore)
                        + " HiddenOre=" + String.format("%.0f", hiddenScore))
                .addEvidence("OreTargeting: synergy=" + String.format("%.2f", synergy))
                .build();
    }
}
