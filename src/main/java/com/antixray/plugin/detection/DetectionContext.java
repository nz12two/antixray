package com.antixray.plugin.detection;

import com.antixray.plugin.engine.EngineModule.EngineData;
import com.antixray.plugin.feature.MiningFeatures;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.profile.PlayerProfile;

public class DetectionContext {
    private final PlayerData data;
    private final MiningFeatures features;
    private final EngineData engineData;
    private final PlayerProfile profile;

    public DetectionContext(PlayerData data, MiningFeatures features,
                            EngineData engineData, PlayerProfile profile) {
        this.data = data;
        this.features = features;
        this.engineData = engineData;
        this.profile = profile;
    }

    public PlayerData getData() { return data; }
    public MiningFeatures getFeatures() { return features; }
    public EngineData getEngineData() { return engineData; }
    public PlayerProfile getProfile() { return profile; }
}
