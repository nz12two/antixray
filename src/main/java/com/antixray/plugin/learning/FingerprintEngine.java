package com.antixray.plugin.learning;

import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerData.RiskProfile;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.engine.EngineModule.EngineData;

import java.util.*;

public class FingerprintEngine {

    public Fingerprint generate(PlayerData data, EngineData engineData, UUID sessionId) {
        Fingerprint.Builder b = new Fingerprint.Builder()
                .sessionId(sessionId)
                .playerId(data.getPlayerId())
                .playerName(data.getPlayerName())
                .sessionStart(data.getSessionStartTime())
                .sessionEnd(System.currentTimeMillis());

        double minutes = Math.max(0.1, data.getCurrentSession().getTotalPlayTime() / 60_000.0);
        b.blocksPerMinute(data.getTotalBlocksMined() / minutes);
        b.oresPerMinute(data.getTotalOresFound() / minutes);

        Deque<MiningEvent> events = data.getRecentEvents();
        int ore = 0, hidden = 0;
        for (MiningEvent e : events) {
            if (isOre(e.getBlockType())) {
                ore++;
                if (e.isHiddenOre()) hidden++;
            }
        }
        b.hiddenOreRatio(ore > 0 ? (double) hidden / ore : 0);

        var path = data.getMiningPath();
        if (path.size() > 5) {
            int straight = 0, total = 0;
            var lastDir = new org.bukkit.util.Vector(0, 0, 0);
            var last = path.get(0);
            int c = 0;
            for (int i = 1; i < path.size(); i++) {
                var dir = path.get(i).toVector().subtract(last.toVector());
                if (dir.length() < 0.3) continue;
                total++;
                dir.normalize();
                if (c > 0 && lastDir.dot(dir) > 0.98) straight++;
                lastDir = dir;
                last = path.get(i);
                c++;
            }
            b.tunnelStraightness(total > 0 ? (double) straight / total : 0);
        }

        int cave = 0, evtTotal = 0;
        for (MiningEvent e : events) {
            evtTotal++;
            if (e.getConditions().contains(MiningEvent.MiningCondition.CAVE)) cave++;
        }
        b.caveExplorationRatio(evtTotal > 0 ? (double) cave / evtTotal : 0);

        b.movementEntropy(1.0 / (1.0 + Math.abs(data.getMovement().getYawChangeRate() - 15)));

        if (engineData != null && engineData.getSpatial() != null) {
            b.oreSpaceCorrelation(engineData.getSpatial().oreSpaceCorrelation);
            b.explorationEfficiency(engineData.getSpatial().explorationEfficiency);
            b.averageYLevel(engineData.getSpatial().averageYLevel);
        }

        RiskProfile rp = data.getRiskProfile();
        for (Map.Entry<String, Double> e : rp.getDetectorScores().entrySet()) {
            b.detectorScore(e.getKey(), e.getValue());
        }

        return b.build();
    }

    private boolean isOre(org.bukkit.Material m) { return m.name().endsWith("_ORE"); }
}
