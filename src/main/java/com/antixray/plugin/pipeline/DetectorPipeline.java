package com.antixray.plugin.pipeline;

import com.antixray.plugin.detection.DetectionContext;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.DetectorState;
import com.antixray.plugin.detection.DetectorStateManager;
import com.antixray.plugin.detection.combiners.MetaDetector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.detection.result.ExplainVector;
import com.antixray.plugin.feature.MiningFeatures;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.profiler.DetectorProfiler;

import java.util.*;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Three-tier detection pipeline with dependency management and risk-gated execution.
 * <p>
 * Tiers:
 * <ul>
 *   <li><b>FAST</b> — Always runs. Lightweight statistical detectors.</li>
 *   <li><b>MEDIUM</b> — Runs when risk ≥ 20 or anomaly score > 15.</li>
 *   <li><b>HEAVY</b> — Runs when risk ≥ 40. Executed in parallel batches via CompletableFuture.</li>
 * </ul>
 * <p>
 * Features risk cache with 30-second TTL, per-detector dependency/negative-dependency
 * resolution, and optional profiling integration.
 */
public class DetectorPipeline {
    private final List<Detector> fastDetectors = new ArrayList<>();
    private final List<Detector> mediumDetectors = new ArrayList<>();
    private final List<Detector> heavyDetectors = new ArrayList<>();
    private final List<MetaDetector> metaDetectors = new ArrayList<>();
    private final Map<String, List<String>> dependencies = new HashMap<>();
    private final Map<String, List<String>> negativeDependencies = new HashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(30);
    private final Map<String, CacheEntry> currentRiskCache = new ConcurrentHashMap<>();
    private DetectorProfiler profiler;
    private DetectorStateManager stateManager;

    public void setProfiler(DetectorProfiler profiler) { this.profiler = profiler; }
    public void setStateManager(DetectorStateManager sm) { this.stateManager = sm; }

    public void registerDetector(Detector d, Tier tier) {
        switch (tier) {
            case FAST -> fastDetectors.add(d);
            case MEDIUM -> mediumDetectors.add(d);
            case HEAVY -> heavyDetectors.add(d);
        }
    }

    public void registerMetaDetector(MetaDetector md) { metaDetectors.add(md); }

    public void registerDependency(String detector, String dependsOn) {
        dependencies.computeIfAbsent(detector, k -> new ArrayList<>()).add(dependsOn);
    }

    public void registerNegativeDependency(String detector, String blockedBy) {
        negativeDependencies.computeIfAbsent(detector, k -> new ArrayList<>()).add(blockedBy);
    }

    public List<DetectionResult> execute(PlayerData data, MiningFeatures features,
                                          DetectionContext context) {
        List<DetectionResult> allResults = new ArrayList<>();
        UUID pid = data.getPlayerId();
        String pidStr = pid.toString();
        CacheEntry entry = currentRiskCache.get(pidStr);
        double currentRisk = (entry != null && !entry.isExpired()) ? entry.risk : 0.0;

        List<DetectionResult> fastResults = runTier(data, fastDetectors, currentRisk, pid);
        allResults.addAll(fastResults);

        ExplainVector fastVector = buildExplainVector(fastResults);
        double anomalyScore = fastVector.getTotalContribution();

        if (currentRisk >= 20 || anomalyScore > 15) {
            List<DetectionResult> mediumResults = runTier(data, mediumDetectors, currentRisk, pid);
            allResults.addAll(mediumResults);
        }

        if (currentRisk >= 40) {
            // HEAVY tier roda em paralelo via CompletableFuture
            List<CompletableFuture<List<DetectionResult>>> futures = new ArrayList<>();
            List<List<Detector>> batches = partition(heavyDetectors, 3); // batches de 3
            for (List<Detector> batch : batches) {
                futures.add(CompletableFuture.supplyAsync(() -> runTier(data, batch, currentRisk, pid)));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (var future : futures) {
                try {
                    allResults.addAll(future.get());
                } catch (Exception e) {
                    // Log e continua
                }
            }
        }

        if (!metaDetectors.isEmpty() && !allResults.isEmpty()) {
            for (MetaDetector md : metaDetectors) {
                if (!md.isEnabled()) continue;
                List<DetectionResult> inputs = new ArrayList<>();
                for (String req : md.getRequiredDetectors()) {
                    for (DetectionResult r : allResults) {
                        if (r.getDetectorName().equals(req)) { inputs.add(r); break; }
                    }
                }
                if (inputs.size() == md.getRequiredDetectors().size()) {
                    try {
                        DetectionResult combined = md.combine(inputs, context);
                        if (combined != null && combined.isSignificant()) allResults.add(combined);
                    } catch (Exception e) {
                        // Log meta-detector failure silently to avoid console spam
                    }
                }
            }
        }

        return allResults;
    }

    public List<DetectionResult> execute(PlayerData data, MiningFeatures features) {
        return execute(data, features, null);
    }

    private ExplainVector buildExplainVector(List<DetectionResult> results) {
        ExplainVector vec = new ExplainVector();
        for (DetectionResult r : results) {
            vec.addContribution(r.getDetectorName(), r.getScore() * r.getConfidence() / 100.0);
        }
        return vec;
    }

    private List<DetectionResult> runTier(PlayerData data, List<Detector> tierDetectors,
                                           double currentRisk, UUID pid) {
        List<DetectionResult> results = new ArrayList<>();
        for (Detector d : tierDetectors) {
            if (!d.isEnabled()) continue;
            if (!dependenciesSatisfied(d, results)) continue;
            if (negativeDependencyBlocked(d, results)) continue;

            long startNanos = System.nanoTime();
            try {
                DetectionResult r = d.analyze(data);
                if (r != null && r.isSignificant()) {
                    if (stateManager != null) {
                        DetectorState state = stateManager.getOrCreate(pid, d.getName());
                        state.recordTrigger(r.getScore(), System.currentTimeMillis());
                    }
                    results.add(r);
                }
            } catch (Exception e) {
                // Individual detector failure logged externally; continue pipeline
            } finally {
                long elapsedNanos = System.nanoTime() - startNanos;
                if (profiler != null) {
                    profiler.record(d.getName(), elapsedNanos);
                }
            }
        }
        return results;
    }

    private boolean dependenciesSatisfied(Detector d, List<DetectionResult> results) {
        List<String> deps = dependencies.get(d.getName());
        if (deps == null || deps.isEmpty()) return true;
        Set<String> triggered = new HashSet<>();
        for (DetectionResult r : results) triggered.add(r.getDetectorName());
        return triggered.containsAll(deps);
    }

    private boolean negativeDependencyBlocked(Detector d, List<DetectionResult> results) {
        List<String> blockedBy = negativeDependencies.get(d.getName());
        if (blockedBy == null || blockedBy.isEmpty()) return false;
        Set<String> triggered = new HashSet<>();
        for (DetectionResult r : results) triggered.add(r.getDetectorName());
        for (String blocker : blockedBy) {
            if (!triggered.contains(blocker)) return true;
        }
        return false;
    }

    public void updateRiskCache(UUID playerId, double risk) {
        currentRiskCache.put(playerId.toString(), new CacheEntry(risk));
    }

    public void cleanupRiskCache(Set<UUID> activePlayers) {
        Set<String> active = activePlayers.stream().map(UUID::toString).collect(Collectors.toSet());
        currentRiskCache.keySet().removeIf(pid -> !active.contains(pid) || currentRiskCache.get(pid).isExpired());
    }

    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    private static class CacheEntry {
        final double risk;
        final long timestamp;

        CacheEntry(double risk) {
            this.risk = risk;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    public enum Tier { FAST, MEDIUM, HEAVY }
}
