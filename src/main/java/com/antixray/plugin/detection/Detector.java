package com.antixray.plugin.detection;

import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;

/**
 * Abstract base class for all detection algorithms.
 * <p>
 * Each detector analyzes PlayerData and returns a DetectionResult if suspicious
 * patterns are found. Detectors are organized into three tiers (FAST, MEDIUM, HEAVY)
 * in the DetectorPipeline and can declare dependencies on other detectors.
 * <p>
 * Subclasses must implement {@link #analyze(PlayerData)} and provide a unique
 * detector name via constructor.
 */
public abstract class Detector {
    protected final String name;
    protected final String description;
    protected boolean enabled;
    protected String category;

    public Detector(String name, String description) {
        this.name = name;
        this.description = description;
        this.enabled = true;
        this.category = "generic";
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public abstract DetectionResult analyze(PlayerData data);

    public void onEnable() {}
    public void onDisable() {}
    public void onReload() {}
}
