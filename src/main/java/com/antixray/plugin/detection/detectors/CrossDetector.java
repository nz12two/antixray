package com.antixray.plugin.detection.detectors;

import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.MiningStatistics;
import com.antixray.plugin.model.PlayerData;

import org.bukkit.Material;

import java.util.Deque;
import java.util.Set;
import java.util.HashSet;

public class CrossDetector extends Detector {

    private static final Set<Material> ORE_MATERIALS = new HashSet<>();
    private static final Set<Material> COMMON_MATERIALS = new HashSet<>();

    static {
        for (Material mat : Material.values()) {
            if (mat.name().endsWith("_ORE")) {
                ORE_MATERIALS.add(mat);
            }
        }
        ORE_MATERIALS.add(Material.ANCIENT_DEBRIS);
        ORE_MATERIALS.add(Material.NETHERITE_SCRAP);

        COMMON_MATERIALS.add(Material.STONE);
        COMMON_MATERIALS.add(Material.DEEPSLATE);
        COMMON_MATERIALS.add(Material.NETHERRACK);
        COMMON_MATERIALS.add(Material.END_STONE);
        COMMON_MATERIALS.add(Material.GRANITE);
        COMMON_MATERIALS.add(Material.DIORITE);
        COMMON_MATERIALS.add(Material.ANDESITE);
        COMMON_MATERIALS.add(Material.TUFF);
        COMMON_MATERIALS.add(Material.DIRT);
        COMMON_MATERIALS.add(Material.GRAVEL);
        COMMON_MATERIALS.add(Material.SANDSTONE);
        COMMON_MATERIALS.add(Material.RED_SANDSTONE);
    }

    public CrossDetector() {
        super("CrossDetector", "Cross-pattern detection: correlates x-ray with combat/movement anomalies");
        this.setCategory("behavioral");
    }

    @Override
    public DetectionResult analyze(PlayerData data) {
        PlayerData.MovementData movement = data.getMovement();
        Deque<MiningEvent> events = data.getRecentEvents();
        MiningStatistics stats = data.getStatistics();

        if (events == null || events.size() < 5) {
            return null;
        }

        double gcdScore = analyzeGCD(movement);
        double timingScore = analyzeTiming(events);
        double comboScore = analyzeComboPattern(events, stats);

        if (gcdScore == 0 && timingScore == 0 && comboScore == 0) {
            return null;
        }

        double crossScore = (gcdScore * 0.40) + (timingScore * 0.30) + (comboScore * 0.30);

        int activeAnalyses = 0;
        if (gcdScore > 0) activeAnalyses++;
        if (timingScore > 0) activeAnalyses++;
        if (comboScore > 0) activeAnalyses++;

        double confidence = Math.min(100.0, activeAnalyses * 33.33);

        String predominantBehavior = detectPredominantBehavior(events);

        return new DetectionResult.Builder()
                .detectorName(getName())
                .score(crossScore)
                .confidence(confidence)
                .reason(String.format("Cross-pattern: GCD=%.4f, Timing=%.1f%%, Combo=%.1f%%",
                        gcdScore, timingScore, comboScore))
                .addEvidence("GCD Score: " + String.format("%.2f", gcdScore))
                .addEvidence("Timing Score: " + String.format("%.2f", timingScore))
                .addEvidence("Combo Score: " + String.format("%.2f", comboScore))
                .addEvidence("Predominant behavior: " + predominantBehavior)
                .build();
    }

    /**
     * Analyzes rotation GCD (Greatest Common Divisor) to detect mechanized rotation.
     * Legitimate players have variable GCD; cheaters using aim assistance
     * tend to have very consistent (low) GCD values.
     *
     * @param movement the player's movement data containing yaw/pitch rates
     * @return score 0-40 depending on how low and smooth the GCD is
     */
    private double analyzeGCD(PlayerData.MovementData movement) {
        if (movement == null) return 0;

        double yawRate = movement.getYawChangeRate();
        double pitchRate = movement.getPitchChangeRate();

        if (yawRate <= 0 || pitchRate <= 0) return 0;

        // Compute approximate GCD of yaw and pitch change rates
        double approxGcd = gcd(yawRate, pitchRate);

        // Very low GCD combined with smooth rates suggests mechanized rotation
        if (approxGcd < 0.01 && yawRate < 5.0 && pitchRate < 5.0) {
            double avgSmoothness = (yawRate + pitchRate) / 2.0;

            if (avgSmoothness < 1.0) {
                return 40.0; // Extremely smooth, very likely aim assistance
            } else if (avgSmoothness < 2.5) {
                return 30.0; // Very smooth rotation
            } else {
                return 20.0; // Moderately smooth
            }
        } else if (approxGcd < 0.05) {
            return 10.0; // Somewhat suspicious GCD
        } else if (approxGcd < 0.10) {
            return 5.0; // Mildly suspicious
        }

        return 0;
    }

    /**
     * Analyzes mining timing patterns to detect non-human regularity.
     * Legitimate players have natural variation in mining intervals;
     * cheaters using automation tend to have very consistent timing.
     *
     * @param events the deque of recent mining events
     * @return score 0-30 depending on timing regularity
     */
    private double analyzeTiming(Deque<MiningEvent> events) {
        if (events == null || events.size() < 5) return 0;

        MiningEvent[] arr = events.toArray(new MiningEvent[0]);
        int intervalCount = arr.length - 1;
        double[] intervals = new double[intervalCount];

        for (int i = 0; i < intervalCount; i++) {
            intervals[i] = arr[i + 1].getTimestamp() - arr[i].getTimestamp();
        }

        // Calculate mean interval
        double mean = 0;
        for (double interval : intervals) {
            mean += interval;
        }
        mean /= intervalCount;

        if (mean <= 0) return 0;

        // Calculate standard deviation
        double variance = 0;
        for (double interval : intervals) {
            double diff = interval - mean;
            variance += diff * diff;
        }
        variance /= intervalCount;
        double stdDev = Math.sqrt(variance);

        // Coefficient of variation (relative standard deviation)
        // Lower CV = more regular timing = more suspicious
        double cv = stdDev / mean;

        if (cv < 0.10 && mean < 1500) {
            return 30.0; // Extremely regular, likely automated
        } else if (cv < 0.20 && mean < 2500) {
            return 20.0; // Very regular timing
        } else if (cv < 0.30) {
            return 10.0; // Moderately regular
        } else if (cv < 0.50) {
            return 5.0; // Somewhat regular
        }

        return 0;
    }

    /**
     * Analyzes combo patterns where a player rapidly switches between
     * mining and combat-like activity without natural pauses.
     * Legitimate players typically focus on one activity at a time.
     *
     * @param events the deque of recent mining events
     * @param stats  the player's mining statistics
     * @return score 0-30 depending on alternation frequency
     */
    private double analyzeComboPattern(Deque<MiningEvent> events, MiningStatistics stats) {
        if (events == null || events.size() < 5 || stats == null) return 0;

        MiningEvent[] arr = events.toArray(new MiningEvent[0]);
        int fastTransitions = 0;
        int totalTransitions = 0;

        // Look for rapid switches between ore and non-ore block types,
        // which indicates the player is alternating between mining and
        // other activities (combat, movement, etc.)
        for (int i = 0; i < arr.length - 1; i++) {
            boolean currentOre = isOre(arr[i].getBlockType());
            boolean nextOre = isOre(arr[i + 1].getBlockType());

            if (currentOre != nextOre) {
                totalTransitions++;
                long interval = arr[i + 1].getTimestamp() - arr[i].getTimestamp();
                if (interval < 800) {
                    fastTransitions++;
                }
            }
        }

        if (totalTransitions == 0) return 0;

        double transitionRate = (double) fastTransitions / totalTransitions;
        double blocksPerMin = stats.getBlocksPerMinute();

        if (transitionRate > 0.7 && blocksPerMin > 50) {
            return 30.0; // Very rapid alternation with high activity
        } else if (transitionRate > 0.5 && blocksPerMin > 35) {
            return 20.0; // Frequent activity switching
        } else if (transitionRate > 0.3) {
            return 10.0; // Notable switching pattern
        } else if (transitionRate > 0.1) {
            return 5.0; // Mild switching pattern
        }

        return 0;
    }

    /**
     * Computes the approximate Greatest Common Divisor of two double values.
     * Uses the Euclidean algorithm adapted for floating-point numbers
     * with an epsilon threshold for convergence.
     *
     * @param a first value
     * @param b second value
     * @return the approximate GCD of a and b
     */
    private double gcd(double a, double b) {
        a = Math.abs(a);
        b = Math.abs(b);

        // Ensure a >= b
        if (a < b) {
            double temp = a;
            a = b;
            b = temp;
        }

        // Euclidean algorithm with epsilon convergence
        double epsilon = 0.0001;
        while (b > epsilon) {
            double temp = b;
            b = a % b;
            a = temp;
        }

        return a;
    }

    /**
     * Determines the predominant behavior pattern from recent mining events.
     *
     * @param events the deque of recent mining events
     * @return a string describing the predominant behavior
     */
    private String detectPredominantBehavior(Deque<MiningEvent> events) {
        if (events == null || events.isEmpty()) return "unknown";

        int oreCount = 0;
        int commonCount = 0;
        int otherCount = 0;

        for (MiningEvent event : events) {
            Material type = event.getBlockType();
            if (isOre(type)) {
                oreCount++;
            } else if (isCommonBlock(type)) {
                commonCount++;
            } else {
                otherCount++;
            }
        }

        int total = oreCount + commonCount + otherCount;
        if (total == 0) return "unknown";

        double oreRatio = (double) oreCount / total;
        double commonRatio = (double) commonCount / total;
        double otherRatio = (double) otherCount / total;

        if (oreRatio > 0.5) {
            return "ore_focused";
        } else if (commonRatio > 0.7) {
            return "tunnel_mining";
        } else if (otherRatio > 0.4) {
            return "mixed_combat";
        } else if (oreRatio > 0.2) {
            return "mixed_mining";
        } else {
            return "balanced";
        }
    }

    /**
     * Checks if a material is an ore type.
     */
    private boolean isOre(Material material) {
        return ORE_MATERIALS.contains(material);
    }

    /**
     * Checks if a material is a common mining block.
     */
    private boolean isCommonBlock(Material material) {
        return COMMON_MATERIALS.contains(material);
    }
}
