package com.antixray.plugin.detection;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.autovalidation.AutoValidationModule;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.config.ConfigLoader.DetectorConfig;
import com.antixray.plugin.detection.combiners.MetaDetector;
import com.antixray.plugin.detection.combiners.OreTargetingDetector;
import com.antixray.plugin.detection.combiners.SystematicMiningDetector;
import com.antixray.plugin.detection.detectors.*;
import com.antixray.plugin.detection.detectors.CrossDetector;
import com.antixray.plugin.detection.result.DetectionBatch;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.detection.result.ExplainVector;
import com.antixray.plugin.engine.EngineModule;
import com.antixray.plugin.engine.EngineModule.EngineData;
import com.antixray.plugin.feature.FeatureModule;
import com.antixray.plugin.feature.MiningFeatures;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import com.antixray.plugin.pipeline.DetectorPipeline;
import com.antixray.plugin.pipeline.DetectorPipeline.Tier;
import com.antixray.plugin.profiler.DetectorProfiler;
import com.antixray.plugin.marketplace.DetectorMarketplace;
import com.antixray.plugin.profile.PlayerProfile;
import com.antixray.plugin.profile.PlayerProfileModule;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrates the detection pipeline execution cycle.
 * <p>
 * Runs asynchronously every 5 seconds, processing all tracked players through
 * the DetectorPipeline. Manages cooldowns, dynamic weight adjustments based on
 * co-firing analysis, and auto-validation through the ThreeTierStore.
 * <p>
 * Integrates with DetectorStateManager for per-detector state tracking and
 * DetectorProfiler for execution time measurement.
 */
public class DetectionModule extends Module {
    private final AntiXrayPlugin plugin;
    private final List<Detector> detectors = new ArrayList<>();
    private final List<MetaDetector> metaDetectors = new ArrayList<>();
    private final Map<UUID, Map<String, Long>> detectorCooldowns = new ConcurrentHashMap<>();
    private PlayerDataManager dataManager;
    private EventBus eventBus;
    private FeatureModule featureModule;
    private EngineModule engineModule;
    private PlayerProfileModule profileModule;
    private DetectorPipeline pipeline;
    private DetectorProfiler profiler;
    private DetectorStateManager stateManager;
    private DynamicWeight dynamicWeight;
    private DetectorMarketplace marketplace;
    private BukkitTask taskId;

    public DetectionModule(AntiXrayPlugin plugin) {
        super("detection");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        this.eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        this.featureModule = plugin.getModuleManager().getModule(FeatureModule.class);
        this.engineModule = plugin.getModuleManager().getModule(EngineModule.class);
        this.profileModule = plugin.getModuleManager().getModule(PlayerProfileModule.class);
        this.pipeline = new DetectorPipeline();
        this.profiler = new DetectorProfiler();
        this.stateManager = new DetectorStateManager();
        this.dynamicWeight = new DynamicWeight(profiler);
        this.marketplace = new DetectorMarketplace(plugin);

        plugin.getServiceRegistry().register(DetectorProfiler.class, profiler);
        plugin.getServiceRegistry().register(DetectorStateManager.class, stateManager);
        plugin.getServiceRegistry().register(DynamicWeight.class, dynamicWeight);
        pipeline.setProfiler(profiler);
        pipeline.setStateManager(stateManager);


        registerDetectors();
        registerMetaDetectors();
        registerDependencies();
        marketplace.loadExternalDetectors();
        marketplace.registerAll(this);



        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processDetections, 100L, 100L);
    }

    @Override
    public void onDisable() {
        if (taskId != null) taskId.cancel();
        detectors.clear();
        metaDetectors.clear();
        detectorCooldowns.clear();
    }

    private void registerDetectors() {
        register(new LuckDetector(), Tier.MEDIUM);
        register(new TunnelDetector(), Tier.FAST);
        register(new BranchMiningDetector(), Tier.FAST);
        register(new WallTrackingDetector(), Tier.MEDIUM);
        register(new HiddenOreDetector(), Tier.FAST);
        register(new PathFindingDetector(), Tier.HEAVY);
        register(new RotationDetector(), Tier.FAST);
        register(new BehaviorDetector(), Tier.MEDIUM);
        register(new OreRatioDetector(), Tier.FAST);
        register(new MiningEfficiencyDetector(), Tier.MEDIUM);
        register(new CaveExplorationDetector(), Tier.MEDIUM);
        register(new DirectionDetector(), Tier.MEDIUM);
        register(new RareOreDetector(), Tier.FAST);
        register(new SessionPatternDetector(), Tier.MEDIUM);
        register(new HistoricalBehaviorDetector(), Tier.FAST);
        register(new ClusterDetector(), Tier.HEAVY);
        register(new SuspiciousTurnDetector(), Tier.FAST);
        register(new MovementDetector(), Tier.FAST);
        register(new ExplorationDetector(), Tier.HEAVY);
        register(new CrossDetector(), Tier.MEDIUM);
        register(new StaircaseDetector(), Tier.MEDIUM);

        setCategories();
    }

    private void setCategories() {
        for (Detector d : detectors) {
            switch (d.getName()) {
                case "TunnelDetector", "BranchMiningDetector", "DirectionDetector",
                     "WallTrackingDetector", "HiddenOreDetector", "ClusterDetector",
                     "ExplorationDetector", "CaveExplorationDetector",
                     "StaircaseDetector" ->
                    d.setCategory("spatial");
                case "RotationDetector", "SuspiciousTurnDetector", "MovementDetector",
                     "PathFindingDetector", "BehaviorDetector",
                     "CrossDetector" ->
                    d.setCategory("behavioral");
                case "LuckDetector", "OreRatioDetector", "RareOreDetector",
                     "MiningEfficiencyDetector", "SessionPatternDetector",
                     "HistoricalBehaviorDetector" ->
                    d.setCategory("statistical");
                default -> d.setCategory("generic");
            }
        }
    }

    private void registerMetaDetectors() {
        registerMeta(new OreTargetingDetector());
        registerMeta(new SystematicMiningDetector());
    }

    private void registerMeta(MetaDetector md) {
        DetectorConfig config = plugin.getConfigLoader().getDetectorConfig(md.getName());
        md.setEnabled(config.enabled);
        metaDetectors.add(md);
        pipeline.registerMetaDetector(md);
    }

    private void register(Detector detector, Tier tier) {
        DetectorConfig config = plugin.getConfigLoader().getDetectorConfig(detector.getName());
        detector.setEnabled(config.enabled);
        detectors.add(detector);
        pipeline.registerDetector(detector, tier);
    }

    private void registerDependencies() {
        pipeline.registerDependency("PathFindingDetector", "MovementDetector");
        pipeline.registerDependency("PathFindingDetector", "HiddenOreDetector");
        pipeline.registerDependency("WallTrackingDetector", "TunnelDetector");
        pipeline.registerDependency("ClusterDetector", "RareOreDetector");
        pipeline.registerDependency("ExplorationDetector", "DirectionDetector");

        pipeline.registerNegativeDependency("RotationDetector", "MovementDetector");
        pipeline.registerNegativeDependency("WallTrackingDetector", "TunnelDetector");
        pipeline.registerNegativeDependency("OreTargetingDetector", "WallTrackingDetector");
        pipeline.registerNegativeDependency("OreTargetingDetector", "HiddenOreDetector");
        pipeline.registerDependency("CrossDetector", "MovementDetector");
    }

    public void registerExternal(Detector detector) {
        DetectorConfig config = plugin.getConfigLoader().getDetectorConfig(detector.getName());
        detector.setEnabled(config.enabled);
        detectors.add(detector);
        pipeline.registerDetector(detector, Tier.MEDIUM);
        plugin.getLogger().info("[Marketplace] Registered external detector: " + detector.getName());
    }



    private void processDetections() {
        for (Map.Entry<UUID, PlayerData> entry : dataManager.getAll().entrySet()) {
            PlayerData data = entry.getValue();
            if (!data.isTracked()) continue;
            if (!data.getCurrentSession().isActive()) continue;
            if (data.getRecentEvents().size() < 50) continue;

            MiningFeatures features = featureModule.getOrCompute(data);
            if (!features.hasMinData()) continue;

            EngineData engineData = engineModule != null ? engineModule.getOrCompute(data) : null;
            PlayerProfile profile = profileModule != null ? profileModule.getOrCreateProfile(data.getPlayerId()) : null;

            DetectionContext context = new DetectionContext(data, features, engineData, profile);

            double adjustedThreshold = profile != null
                    ? profile.getAdjustedThreshold(10)
                    : 10;

            List<DetectionResult> results = pipeline.execute(data, features, context);
            if (results.isEmpty()) continue;

            // Build ExplainVector for the batch
            ExplainVector batchVector = new ExplainVector();
            Map<String, Double> categoryTotals = new HashMap<>();
            for (DetectionResult r : results) {
                double contribution = r.getScore() * r.getConfidence() / 100.0;
                batchVector.addContribution(r.getDetectorName(), contribution);
                String cat = "generic";
                for (Detector d : detectors) {
                    if (d.getName().equals(r.getDetectorName())) {
                        cat = d.getCategory();
                        break;
                    }
                }
                categoryTotals.merge(cat, contribution, Double::sum);
            }
            categoryTotals.forEach(batchVector::addCategory);

            List<DetectionResult> validResults = new ArrayList<>();
            for (DetectionResult r : results) {
                DetectorConfig config = plugin.getConfigLoader().getDetectorConfig(r.getDetectorName());
                if (isOnCooldown(data.getPlayerId(), r.getDetectorName(), config.cooldown)) continue;
                if (r.getConfidence() < config.confidenceThreshold) continue;

                double effectiveWeight = dynamicWeight != null
                        ? dynamicWeight.getEffectiveWeight(r.getDetectorName(), config.threshold)
                        : config.threshold;
                if (r.getScore() < effectiveWeight * (adjustedThreshold / 10.0)) continue;

                validResults.add(r);
                setCooldown(data.getPlayerId(), r.getDetectorName());
                if (profiler != null) profiler.recordScore(r.getDetectorName(), r.getScore());
            }

            if (validResults.isEmpty()) continue;

            if (dynamicWeight != null) {
                DetectionBatch trackingBatch = new DetectionBatch(data.getPlayerId(), data.getPlayerName(), validResults, batchVector);
                for (DetectionResult r : validResults) {
                    dynamicWeight.recordDetection(r.getDetectorName(), trackingBatch);
                }
            }

            var autoValModule = plugin.getModuleManager().getModule(AutoValidationModule.class);
            if (autoValModule != null) {
                for (DetectionResult r : validResults) {
                    autoValModule.recordDetection(r.getDetectorName(), data.getPlayerId());
                }
            }

            eventBus.post(new DetectionBatch(data.getPlayerId(), data.getPlayerName(), validResults, batchVector));

            // Limpa cooldowns de jogadores offline a cada 100 iterações
            if (ThreadLocalRandom.current().nextInt(100) == 0) {
                cleanupCooldowns(dataManager.getAll().keySet());
            }
        }
    }

    private boolean isOnCooldown(UUID playerId, String detectorName, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        Map<String, Long> playerCooldowns = detectorCooldowns.get(playerId);
        if (playerCooldowns == null) return false;
        Long lastTime = playerCooldowns.get(detectorName);
        if (lastTime == null) return false;
        return (System.currentTimeMillis() - lastTime) < (cooldownSeconds * 1000L);
    }

    public void setCooldown(UUID playerId, String detectorName) {
        detectorCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(detectorName, System.currentTimeMillis());
    }

    public void cleanupCooldowns(Set<UUID> activePlayers) {
        detectorCooldowns.keySet().removeIf(uuid -> !activePlayers.contains(uuid));
    }

    public List<Detector> getDetectors() { return Collections.unmodifiableList(detectors); }
    public List<MetaDetector> getMetaDetectors() { return Collections.unmodifiableList(metaDetectors); }
    public DetectorProfiler getProfiler() { return profiler; }
    public DetectorPipeline getPipeline() { return pipeline; }

    @SuppressWarnings("unchecked")
    public <T extends Detector> T getDetector(Class<T> type) {
        for (Detector d : detectors) {
            if (type.isInstance(d)) return (T) d;
        }
        return null;
    }
}
