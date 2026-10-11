package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.learning.LearningModule.ServerProfile;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerSession;

import java.util.Deque;

public class SessionPatternDetector extends Detector {
    private static final long SESSION_MIN_MS = 120_000;
    private static final int MIN_EVENTS = 50;

    public SessionPatternDetector() {
        super("SessionPatternDetector", "Analyzes session-wide mining patterns for consistency with X-Ray usage");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        PlayerSession session = data.getCurrentSession();
        Deque<MiningEvent> events = data.getRecentEvents();
        if (events.size() < MIN_EVENTS) return null;
        if (session.getTotalPlayTime() < SESSION_MIN_MS) return null;

        long sessionTime = session.getTotalPlayTime();
        int blocksMined = data.getTotalBlocksMined();
        int oresFound = data.getTotalOresFound();

        double blocksPerMin = (double) blocksMined / Math.max(1, sessionTime / 60_000.0);
        double oresPerMin = (double) oresFound / Math.max(1, sessionTime / 60_000.0);

        ServerProfile profile = AntiXrayPlugin.getServerProfile();
        double expectedBlocksPerMin = profile != null ? profile.getAverageBlocksPerMinute() : 60;
        double expectedOresPerMin = profile != null ? profile.getAverageOreRate() * expectedBlocksPerMin : 2;
        if (expectedBlocksPerMin <= 0) expectedBlocksPerMin = 60;
        if (expectedOresPerMin <= 0) expectedOresPerMin = 2;

        double blockEfficiency = blocksPerMin / expectedBlocksPerMin;
        double oreEfficiency = oresPerMin / expectedOresPerMin;

        double score = 0;

        if (oreEfficiency > 5) {
            score += Math.min(60, oreEfficiency * 10);
        }
        if (blockEfficiency > 2) {
            score += Math.min(40, blockEfficiency * 15);
        }

        if (score > 30) {
            double confidence = Math.min(100, (double) events.size() / 2);

            return new DetectionResult.Builder()
                    .detectorName(getName())
                    .score(score)
                    .confidence(confidence)
                    .reason(String.format("Session pattern: %.1f blocks/min, %.1f ores/min (%.1fx expected rate)",
                            blocksPerMin, oresPerMin, oreEfficiency))
                    .addEvidence("Blocks/min: " + String.format("%.1f", blocksPerMin))
                    .addEvidence("Ores/min: " + String.format("%.1f", oresPerMin))
                    .addEvidence("Ore efficiency factor: " + String.format("%.1fx", oreEfficiency))
                    .build();
        }

        return null;
    }
}
