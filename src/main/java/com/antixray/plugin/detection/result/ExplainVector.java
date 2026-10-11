package com.antixray.plugin.detection.result;

import java.util.*;

public class ExplainVector {
    private final Map<String, Double> contributions;
    private final Map<String, Double> categories;

    public ExplainVector() {
        this.contributions = new LinkedHashMap<>();
        this.categories = new LinkedHashMap<>();
    }

    public void addContribution(String detectorName, double contribution) {
        contributions.put(detectorName, contribution);
    }

    public void addCategory(String category, double total) {
        categories.put(category, total);
    }

    public Map<String, Double> getContributions() {
        return Collections.unmodifiableMap(contributions);
    }

    public Map<String, Double> getCategories() {
        return Collections.unmodifiableMap(categories);
    }

    public double getTotalContribution() {
        return contributions.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public List<Map.Entry<String, Double>> getTopContributions(int n) {
        return contributions.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .toList();
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        for (var entry : getTopContributions(8)) {
            String prefix = entry.getValue() > 0 ? "§c+" : "§a";
            sb.append("  ").append(prefix).append(String.format("%.0f", Math.abs(entry.getValue())))
                    .append(" §7").append(entry.getKey().replace("Detector", "")).append("\n");
        }
        if (!categories.isEmpty()) {
            sb.append("§8Categories: ");
            categories.forEach((cat, val) -> {
                if (val > 5) sb.append(cat).append("=").append(String.format("%.0f", val)).append(" ");
            });
            sb.append("\n");
        }
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"contributions\":{");
        boolean first = true;
        for (var e : contributions.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
        }
        sb.append("},\"categories\":{");
        first = true;
        for (var e : categories.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
        }
        sb.append("}}");
        return sb.toString();
    }
}
