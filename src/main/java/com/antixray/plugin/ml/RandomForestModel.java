package com.antixray.plugin.ml;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Random Forest classifier using Smile ML 4.x for player classification.
 * <p>
 * Trains on MLDataset (80/20 split), evaluates accuracy/precision/recall/F1,
 * and provides per-sample predictions with probability distributions.
 * Uses 12 fixed features aligned with Fingerprint.toVector() format.
 * <p>
 * Serialization preserves model metadata but requires retraining after
 * deserialization (transient model field).
 */
public class RandomForestModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ──────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────

    private final String modelId;
    private LocalDateTime trainedAt;
    private int numTrees;
    private double accuracy;
    private double precision;
    private double recall;
    private double f1Score;
    private int datasetSize;
    private long seed;
    private Map<String, Double> metadata;

    private transient SimpleRandomForest model;
    private transient boolean trained;

    // ──────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────

    public RandomForestModel() {
        this.modelId = UUID.randomUUID().toString().substring(0, 8);
        this.trainedAt = null;
        this.numTrees = 0;
        this.accuracy = 0.0;
        this.precision = 0.0;
        this.recall = 0.0;
        this.f1Score = 0.0;
        this.datasetSize = 0;
        this.seed = System.currentTimeMillis();
        this.metadata = new LinkedHashMap<>();
        this.trained = false;
    }

    public RandomForestModel(String modelId, LocalDateTime trainedAt, int numTrees,
                             double accuracy, double precision, double recall,
                             double f1Score, int datasetSize, long seed,
                             Map<String, Double> metadata) {
        this.modelId = modelId;
        this.trainedAt = trainedAt;
        this.numTrees = numTrees;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1Score = f1Score;
        this.datasetSize = datasetSize;
        this.seed = seed;
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        this.trained = accuracy > 0;
    }

    // ──────────────────────────────────────────────
    // Training
    // ──────────────────────────────────────────────

    /**
     * Treina o modelo Random Forest com o dataset fornecido.
     *
     * @param dataset  Dataset com features e labels
     * @param numTrees Número de árvores (default 100)
     * @param maxDepth Profundidade máxima (default 20)
     * @param seed     Semente aleatória (pode ser null para usar System.currentTimeMillis())
     */
    public void train(MLDataset dataset, int numTrees, int maxDepth, Long seed) {
        if (dataset == null || dataset.size() == 0) {
            throw new IllegalArgumentException("Dataset cannot be null or empty");
        }
        if (dataset.getLegitCount() == 0 || dataset.getCheaterCount() == 0) {
            throw new IllegalArgumentException("Dataset must contain both LEGIT and CHEATER samples");
        }

        long effectiveSeed = (seed != null) ? seed : System.currentTimeMillis();
        this.seed = effectiveSeed;
        this.numTrees = (numTrees > 0) ? numTrees : 100;
        if (maxDepth <= 0) maxDepth = 20;
        // Split dataset: 80% train, 20% test
        List<MLDataset.DataRow> allRows = new ArrayList<>(dataset.getRows());
        Collections.shuffle(allRows, new Random(effectiveSeed));

        int splitIndex = (int) (allRows.size() * 0.8);
        List<MLDataset.DataRow> trainRows = allRows.subList(0, splitIndex);
        List<MLDataset.DataRow> testRows = allRows.subList(splitIndex, allRows.size());

        // Convert to arrays for our SimpleRandomForest
        double[][] trainFeatures = new double[trainRows.size()][];
        int[] trainLabels = new int[trainRows.size()];
        for (int i = 0; i < trainRows.size(); i++) {
            trainFeatures[i] = trainRows.get(i).getFeatures();
            trainLabels[i] = trainRows.get(i).isCheater() ? 1 : 0;
        }

        double[][] testFeatures = new double[testRows.size()][];
        int[] testLabels = new int[testRows.size()];
        for (int i = 0; i < testRows.size(); i++) {
            testFeatures[i] = testRows.get(i).getFeatures();
            testLabels[i] = testRows.get(i).isCheater() ? 1 : 0;
        }

        // Train Random Forest
        this.model = new SimpleRandomForest(numTrees, maxDepth, 5, effectiveSeed);
        this.model.fit(trainFeatures, trainLabels);

        // Evaluate on test set
        int correct = 0;
        int truePositive = 0;  // correctly predicted CHEATER
        int falsePositive = 0; // predicted CHEATER but was LEGIT
        int falseNegative = 0; // predicted LEGIT but was CHEATER

        double[] testProbs = new double[2]; // binary: LEGIT=0, CHEATER=1
        for (int i = 0; i < testFeatures.length; i++) {
            int actual = testLabels[i];
            int predicted = model.predict(testFeatures[i], testProbs);

            if (predicted == actual) {
                correct++;
            }

            if (predicted == 1) { // predicted CHEATER
                if (actual == 1) {
                    truePositive++;
                } else {
                    falsePositive++;
                }
            } else { // predicted LEGIT
                if (actual == 1) {
                    falseNegative++;
                }
            }
        }

        int totalTest = testFeatures.length;
        this.accuracy = totalTest > 0 ? (double) correct / totalTest * 100.0 : 0.0;
        this.precision = (truePositive + falsePositive) > 0
                ? (double) truePositive / (truePositive + falsePositive) * 100.0 : 0.0;
        this.recall = (truePositive + falseNegative) > 0
                ? (double) truePositive / (truePositive + falseNegative) * 100.0 : 0.0;
        this.f1Score = (precision + recall) > 0
                ? 2.0 * precision * recall / (precision + recall) : 0.0;

        this.datasetSize = dataset.size();
        this.trainedAt = LocalDateTime.now();
        this.trained = true;

        this.metadata.put("numTrees", (double) this.numTrees);
        this.metadata.put("maxDepth", (double) maxDepth);
        this.metadata.put("nodeSize", 5.0); // minSamplesSplit=5
        this.metadata.put("seed", (double) effectiveSeed);
        this.metadata.put("trainSize", (double) trainRows.size());
        this.metadata.put("testSize", (double) testRows.size());
        this.metadata.put("legitCount", (double) dataset.getLegitCount());
        this.metadata.put("cheaterCount", (double) dataset.getCheaterCount());
    }

    // ──────────────────────────────────────────────
    // Prediction
    // ──────────────────────────────────────────────

    /**
     * Prediz o label para um vetor de features.
     */
    public PredictionResult predict(double[] features) {
        if (!isTrained() || model == null) {
            // Modelo não treinado: retorna LEGIT com 50% confiança
            Map<String, Double> probs = new LinkedHashMap<>();
            probs.put("LEGIT", 0.5);
            probs.put("CHEATER", 0.5);
            return new PredictionResult("LEGIT", 50.0, probs);
        }

        double[] rawProbs = new double[2]; // binary: LEGIT=0, CHEATER=1
        int predictedClass = model.predict(features, rawProbs);

        String label = (predictedClass == 1) ? "CHEATER" : "LEGIT";
        double confidence = rawProbs[predictedClass] * 100.0;

        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("LEGIT", rawProbs.length > 0 ? rawProbs[0] : 0.5);
        probabilities.put("CHEATER", rawProbs.length > 1 ? rawProbs[1] : 0.5);

        return new PredictionResult(label, confidence, probabilities);
    }

    /**
     * Retorna apenas a confiança da predição (0-100).
     */
    public double predictConfidence(double[] features) {
        return predict(features).getConfidence();
    }

    // ──────────────────────────────────────────────
    // Serialization
    // ──────────────────────────────────────────────

    /**
     * Serializa o modelo para arquivo (Java serialization).
     */
    public void save(File file) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(this);
        }
    }

    /**
     * Carrega o modelo de um arquivo.
     */
    public static RandomForestModel load(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (RandomForestModel) ois.readObject();
        }
    }

    // ──────────────────────────────────────────────
    // Summary & Status
    // ──────────────────────────────────────────────

    public String getSummary() {
        if (!isTrained()) {
            return "RandomForestModel [" + modelId + "] — NOT TRAINED";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("RandomForestModel [").append(modelId).append("]\n");
        sb.append("  Trained at:  ").append(trainedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("  Dataset:     ").append(datasetSize).append(" rows\n");
        sb.append("  Trees:       ").append(numTrees).append("\n");
        sb.append("  Accuracy:    ").append(String.format("%.2f%%", accuracy)).append("\n");
        sb.append("  Precision:   ").append(String.format("%.2f%%", precision)).append("\n");
        sb.append("  Recall:      ").append(String.format("%.2f%%", recall)).append("\n");
        sb.append("  F1 Score:    ").append(String.format("%.2f%%", f1Score)).append("\n");
        sb.append("  Seed:        ").append(seed).append("\n");
        if (!metadata.isEmpty()) {
            sb.append("  Metadata:    ").append(metadata).append("\n");
        }
        return sb.toString();
    }

    public boolean isTrained() {
        return trained;
    }

    // ──────────────────────────────────────────────
    // Getters
    // ──────────────────────────────────────────────

    public String getModelId() { return modelId; }
    public LocalDateTime getTrainedAt() { return trainedAt; }
    public int getNumTrees() { return numTrees; }
    public double getAccuracy() { return accuracy; }
    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public double getF1Score() { return f1Score; }
    public int getDatasetSize() { return datasetSize; }
    public long getSeed() { return seed; }
    public Map<String, Double> getMetadata() { return Collections.unmodifiableMap(metadata); }

    // ──────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────

    // buildDataFrame() e featuresToTuple() removidos — SimpleRandomForest
    // trabalha diretamente com double[][] e double[].
    // O método featuresToTuple() foi substituído pela chamada direta:
    //   model.predict(features, rawProbs)
    // que aceita double[] nativamente.

    // ──────────────────────────────────────────────
    // Custom serialization (transient model)
    // ──────────────────────────────────────────────

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // trained flag is serialized via default
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // After deserialization, model must be retrained
        this.model = null;
        this.trained = false;
    }

    // ──────────────────────────────────────────────
    // PredictionResult inner class
    // ──────────────────────────────────────────────

    public static class PredictionResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String label; // "LEGIT" or "CHEATER"
        private final double confidence;
        private final Map<String, Double> probabilities;

        public PredictionResult(String label, double confidence, Map<String, Double> probabilities) {
            this.label = label;
            this.confidence = confidence;
            this.probabilities = probabilities != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(probabilities))
                    : Collections.emptyMap();
        }

        public String getLabel() {
            return label;
        }

        public double getConfidence() {
            return confidence;
        }

        public Map<String, Double> getProbabilities() {
            return probabilities;
        }

        @Override
        public String toString() {
            return "PredictionResult{" +
                    "label='" + label + '\'' +
                    ", confidence=" + String.format("%.2f", confidence) +
                    ", probabilities=" + probabilities +
                    '}';
        }
    }
}
