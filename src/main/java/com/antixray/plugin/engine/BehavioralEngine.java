package com.antixray.plugin.engine;

import com.antixray.plugin.feature.MiningFeatures;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerData.MovementData;

import java.util.*;

public class BehavioralEngine {

    public BehavioralSnapshot analyze(PlayerData data, MiningFeatures features) {
        BehavioralSnapshot snap = new BehavioralSnapshot();
        MovementData movement = data.getMovement();

        snap.yawChangeRate = movement.getYawChangeRate();
        snap.pitchChangeRate = movement.getPitchChangeRate();
        snap.suspiciousTurns = movement.getSuspiciousTurns();
        snap.totalDistance = movement.getTotalDistance();
        snap.movementEntropy = computeMovementEntropy(movement);

        snap.pathStraightness = features.getTunnelStraightness();
        snap.branchRatio = features.getBranchMiningRatio();
        snap.averageTurnAngle = features.getAverageTurnAngle();
        snap.directionalConsistency = features.getDirectionalConsistency();

        snap.actionPattern = classifyActionPattern(data.getRecentEvents());

        return snap;
    }

    private double computeMovementEntropy(MovementData m) {
        double yawNorm = Math.min(1, m.getYawChangeRate() / 30.0);
        double pitchNorm = Math.min(1, m.getPitchChangeRate() / 20.0);
        double turnNorm = Math.min(1, m.getSuspiciousTurns() / 10.0);
        double combined = (yawNorm + pitchNorm + turnNorm) / 3.0;
        if (combined <= 0) return 1.0;
        return -combined * Math.log(combined) / Math.log(3);
    }

    private ActionPattern classifyActionPattern(Deque<MiningEvent> events) {
        if (events.size() < 10) return ActionPattern.NORMAL;
        long oreCount = events.stream().filter(e -> e.getBlockType().name().endsWith("_ORE")).count();
        long hiddenCount = events.stream().filter(MiningEvent::isHiddenOre).count();
        double oreRatio = (double) oreCount / events.size();
        double hiddenRatio = oreCount > 0 ? (double) hiddenCount / oreCount : 0;

        if (hiddenRatio > 0.6 && oreRatio > 0.1) return ActionPattern.TARGETED;
        if (hiddenRatio > 0.3 && oreRatio > 0.15) return ActionPattern.SUSPICIOUS;
        if (oreRatio > 0.3) return ActionPattern.ORE_FOCUSED;
        return ActionPattern.NORMAL;
    }

    public enum ActionPattern { NORMAL, ORE_FOCUSED, SUSPICIOUS, TARGETED }

    public static class BehavioralSnapshot {
        public double yawChangeRate;
        public double pitchChangeRate;
        public int suspiciousTurns;
        public double totalDistance;
        public double movementEntropy;
        public double pathStraightness;
        public double branchRatio;
        public double averageTurnAngle;
        public double directionalConsistency;
        public ActionPattern actionPattern;

        public double getSuspicionScore() {
            double score = 0;
            if (pathStraightness > 0.5) score += pathStraightness * 40;
            if (actionPattern == ActionPattern.TARGETED) score += 35;
            if (actionPattern == ActionPattern.SUSPICIOUS) score += 20;
            if (movementEntropy < 0.3) score += (1 - movementEntropy) * 25;
            if (suspiciousTurns > 5) score += Math.min(20, suspiciousTurns * 3);
            return Math.min(100, score);
        }
    }
}
