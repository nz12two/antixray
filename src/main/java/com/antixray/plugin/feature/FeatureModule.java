package com.antixray.plugin.feature;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureModule extends Module {
    private final AntiXrayPlugin plugin;
    private final FeatureExtractor extractor = new FeatureExtractor();
    private final Map<UUID, MiningFeatures> featureCache = new ConcurrentHashMap<>();

    public FeatureModule(AntiXrayPlugin plugin) {
        super("features");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);

        eventBus.register(MiningEvent.class, this::onMiningEvent);
    }

    @Override
    public void onDisable() {
        featureCache.clear();
    }

    private void onMiningEvent(MiningEvent event) {
        featureCache.remove(event.getPlayerId());
    }

    public MiningFeatures getFeatures(UUID playerId) {
        return featureCache.get(playerId);
    }

    public MiningFeatures getOrCompute(PlayerData data) {
        if (!data.getCurrentSession().isActive()) {
            MiningFeatures cached = featureCache.get(data.getPlayerId());
            if (cached != null) return cached;
        }
        MiningFeatures fresh = extractor.extract(data);
        featureCache.put(data.getPlayerId(), fresh);
        return fresh;
    }

    public void invalidate(UUID playerId) {
        featureCache.remove(playerId);
    }
}
