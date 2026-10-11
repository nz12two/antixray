package com.antixray.plugin.detection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DetectorStateManager {
    private final Map<String, Map<UUID, DetectorState>> states = new ConcurrentHashMap<>();

    public DetectorState getOrCreate(UUID playerId, String detectorName) {
        return states.computeIfAbsent(detectorName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, id -> new DetectorState(id, detectorName));
    }

    public DetectorState get(UUID playerId, String detectorName) {
        Map<UUID, DetectorState> detectorStates = states.get(detectorName);
        return detectorStates != null ? detectorStates.get(playerId) : null;
    }

    public void remove(UUID playerId) {
        for (Map<UUID, DetectorState> detectorStates : states.values()) {
            detectorStates.remove(playerId);
        }
    }

    public void reset(UUID playerId, String detectorName) {
        Map<UUID, DetectorState> detectorStates = states.get(detectorName);
        if (detectorStates != null) detectorStates.remove(playerId);
    }

    public void cleanup() { states.clear(); }
}
