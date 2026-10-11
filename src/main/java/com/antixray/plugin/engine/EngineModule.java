package com.antixray.plugin.engine;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.engine.BehavioralEngine.BehavioralSnapshot;
import com.antixray.plugin.engine.SpatialEngine.SpatialSnapshot;
import com.antixray.plugin.engine.StatisticalEngine.StatisticalSnapshot;
import com.antixray.plugin.feature.FeatureModule;
import com.antixray.plugin.feature.MiningFeatures;
import com.antixray.plugin.learning.LearningModule;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EngineModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<UUID, EngineData> cache = new ConcurrentHashMap<>();
    private StatisticalEngine statisticalEngine;
    private BehavioralEngine behavioralEngine;
    private SpatialEngine spatialEngine;
    private FeatureModule featureModule;

    public EngineModule(AntiXrayPlugin plugin) {
        super("engines");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.featureModule = plugin.getModuleManager().getModule(FeatureModule.class);
        LearningModule learningModule = plugin.getModuleManager().getModule(LearningModule.class);

        this.statisticalEngine = new StatisticalEngine(learningModule);
        this.behavioralEngine = new BehavioralEngine();
        this.spatialEngine = new SpatialEngine();

        plugin.getServiceRegistry().register(StatisticalEngine.class, statisticalEngine);
        plugin.getServiceRegistry().register(BehavioralEngine.class, behavioralEngine);
        plugin.getServiceRegistry().register(SpatialEngine.class, spatialEngine);

        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        eventBus.register(MiningEvent.class, this::onMiningEvent);
    }

    @Override
    public void onDisable() {
        cache.clear();
    }

    private void onMiningEvent(MiningEvent event) {
        cache.remove(event.getPlayerId());
    }

    public EngineData get(UUID playerId) {
        return cache.get(playerId);
    }

    public EngineData getOrCompute(PlayerData data) {
        EngineData cached = cache.get(data.getPlayerId());
        if (cached != null) return cached;

        MiningFeatures features = featureModule.getOrCompute(data);
        StatisticalSnapshot stat = statisticalEngine.analyze(data);
        BehavioralSnapshot beh = behavioralEngine.analyze(data, features);
        SpatialSnapshot spa = spatialEngine.analyze(data, features);

        EngineData fresh = new EngineData(stat, beh, spa);
        cache.put(data.getPlayerId(), fresh);
        return fresh;
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
    }

    public StatisticalEngine getStatisticalEngine() { return statisticalEngine; }
    public BehavioralEngine getBehavioralEngine() { return behavioralEngine; }
    public SpatialEngine getSpatialEngine() { return spatialEngine; }

    public static class EngineData {
        private final StatisticalSnapshot statistical;
        private final BehavioralSnapshot behavioral;
        private final SpatialSnapshot spatial;

        public EngineData(StatisticalSnapshot s, BehavioralSnapshot b, SpatialSnapshot sp) {
            this.statistical = s;
            this.behavioral = b;
            this.spatial = sp;
        }

        public StatisticalSnapshot getStatistical() { return statistical; }
        public BehavioralSnapshot getBehavioral() { return behavioral; }
        public SpatialSnapshot getSpatial() { return spatial; }

        public double getCombinedAnomalyScore() {
            return (statistical.getAnomalyScore() * 0.25
                    + behavioral.getSuspicionScore() * 0.40
                    + spatial.getSuspicionScore() * 0.35);
        }
    }
}
