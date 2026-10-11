package com.antixray.plugin.ml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gerencia dataset para treinamento de ML.
 * Cada DataRow contém um vetor de 12 features (mesmo formato de Fingerprint.toVector())
 * e um label: "LEGIT" ou "CHEATER".
 */
public class MLDataset {

    private final List<DataRow> rows;

    public MLDataset() {
        this.rows = new ArrayList<>();
    }

    // ──────────────────────────────────────────────
    // DataRow inner class
    // ──────────────────────────────────────────────

    public static class DataRow {
        private final double[] features;
        private final String label;

        public DataRow(double[] features, String label) {
            this.features = Objects.requireNonNull(features, "features must not be null");
            this.label = Objects.requireNonNull(label, "label must not be null");
            if (!"LEGIT".equals(label) && !"CHEATER".equals(label)) {
                throw new IllegalArgumentException("Label must be 'LEGIT' or 'CHEATER', got: " + label);
            }
        }

        public double[] getFeatures() {
            return features;
        }

        public String getLabel() {
            return label;
        }

        public boolean isCheater() {
            return "CHEATER".equals(label);
        }

        public boolean isLegit() {
            return "LEGIT".equals(label);
        }

        public int getLabelAsInt() {
            return isCheater() ? 1 : 0;
        }
    }

    // ──────────────────────────────────────────────
    // Core operations
    // ──────────────────────────────────────────────

    public void addRow(double[] features, String label) {
        rows.add(new DataRow(features.clone(), label));
    }

    public void addRow(DataRow row) {
        rows.add(new DataRow(row.getFeatures().clone(), row.getLabel()));
    }

    public void removeFirst(int n) {
        if (n <= 0) return;
        int removeCount = Math.min(n, rows.size());
        for (int i = 0; i < removeCount; i++) {
            rows.remove(0);
        }
    }

    public int size() {
        return rows.size();
    }

    public int getLegitCount() {
        return (int) rows.stream().filter(DataRow::isLegit).count();
    }

    public int getCheaterCount() {
        return (int) rows.stream().filter(DataRow::isCheater).count();
    }

    /**
     * Retorna uma cópia imutável das linhas.
     */
    public List<DataRow> getRows() {
        return Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public void clear() {
        rows.clear();
    }

    // ──────────────────────────────────────────────
    // Feature / Label matrices
    // ──────────────────────────────────────────────

    public double[][] getFeatures() {
        double[][] matrix = new double[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            matrix[i] = rows.get(i).getFeatures().clone();
        }
        return matrix;
    }

    public int[] getLabels() {
        int[] labels = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            labels[i] = rows.get(i).getLabelAsInt();
        }
        return labels;
    }

    // ──────────────────────────────────────────────
    // Stats & Balance
    // ──────────────────────────────────────────────

    public String getStats() {
        return "Dataset: " + rows.size() + " rows (" + getLegitCount() + " legit, " + getCheaterCount() + " cheater)";
    }

    /**
     * Verifica se as classes são balanceadas (diferença < 20% entre as classes).
     */
    public boolean isBalanced() {
        if (rows.isEmpty()) return true;
        int legit = getLegitCount();
        int cheater = getCheaterCount();
        if (legit == 0 || cheater == 0) return false;
        double ratio = (double) Math.min(legit, cheater) / Math.max(legit, cheater);
        return ratio >= 0.8; // diferença < 20%
    }

    /**
     * Undersampling da classe majoritária para balancear o dataset.
     */
    public void balance() {
        if (rows.isEmpty() || isBalanced()) return;

        List<DataRow> legitRows = rows.stream().filter(DataRow::isLegit).collect(Collectors.toList());
        List<DataRow> cheaterRows = rows.stream().filter(DataRow::isCheater).collect(Collectors.toList());

        List<DataRow> majority;
        List<DataRow> minority;
        boolean majorityIsLegit;

        if (legitRows.size() > cheaterRows.size()) {
            majority = legitRows;
            minority = cheaterRows;
            majorityIsLegit = true;
        } else {
            majority = cheaterRows;
            minority = legitRows;
            majorityIsLegit = false;
        }

        int targetSize = minority.size();
        Collections.shuffle(majority, new Random());
        List<DataRow> sampledMajority = majority.subList(0, targetSize);

        rows.clear();
        rows.addAll(minority);
        rows.addAll(sampledMajority);
        Collections.shuffle(rows, new Random());
    }

    // ──────────────────────────────────────────────
    // CSV persistence
    // ──────────────────────────────────────────────

    public void saveToFile(File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // Header
            writer.write("label");
            for (int i = 0; i < 12; i++) {
                writer.write(",feature_" + i);
            }
            writer.newLine();

            // Data rows
            for (DataRow row : rows) {
                writer.write(row.getLabel());
                double[] features = row.getFeatures();
                for (int i = 0; i < 12; i++) {
                    writer.write(",");
                    if (i < features.length) {
                        writer.write(String.valueOf(features[i]));
                    } else {
                        writer.write("0.0");
                    }
                }
                writer.newLine();
            }
        }
    }

    public void loadFromFile(File file) throws IOException {
        rows.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine(); // skip header
            if (headerLine == null) return;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                String label = parts[0].trim();
                if (!"LEGIT".equals(label) && !"CHEATER".equals(label)) continue;

                double[] features = new double[12];
                for (int i = 0; i < 12; i++) {
                    if (i + 1 < parts.length) {
                        try {
                            features[i] = Double.parseDouble(parts[i + 1].trim());
                        } catch (NumberFormatException e) {
                            features[i] = 0.0;
                        }
                    } else {
                        features[i] = 0.0;
                    }
                }

                rows.add(new DataRow(features, label));
            }
        }
    }
}
