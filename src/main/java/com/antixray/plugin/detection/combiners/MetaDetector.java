package com.antixray.plugin.detection.combiners;

import com.antixray.plugin.detection.DetectionContext;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.result.DetectionResult;

import java.util.List;

public abstract class MetaDetector extends Detector {
    protected final List<String> requiredDetectors;

    public MetaDetector(String name, String description, List<String> requiredDetectors) {
        super(name, description);
        this.requiredDetectors = requiredDetectors;
    }

    public List<String> getRequiredDetectors() { return requiredDetectors; }
    public abstract DetectionResult combine(List<DetectionResult> inputs, DetectionContext ctx);
}
