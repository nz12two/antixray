package com.antixray.plugin.commands.commands;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.ml.MLDataset;
import com.antixray.plugin.ml.RandomForestModel;
import com.antixray.plugin.ml.RandomForestModel.PredictionResult;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import com.antixray.plugin.learning.FingerprintEngine;
import com.antixray.plugin.learning.Fingerprint;
import com.antixray.plugin.engine.EngineModule;
import com.antixray.plugin.engine.EngineModule.EngineData;
import com.antixray.plugin.feature.FeatureModule;
import com.antixray.plugin.feature.MiningFeatures;
import com.antixray.plugin.collection.CollectionModule;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for /antixray dataset and /antixray ml subcommands.
 * Delegated from AntiXrayBaseCommand.
 */
public class MLCommandHandler {

    private final AntiXrayPlugin plugin;
    private final PlayerDataManager dataManager;
    private final EngineModule engineModule;
    private final FingerprintEngine fingerprintEngine;

    private static final int EXPECTED_FEATURES = 12;
    private static final int MIN_SAMPLES_PER_CLASS = 10;
    private static final int DEFAULT_TREES = 100;
    private static final int DEFAULT_DEPTH = 20;
    private static final String DATASET_FILE_NAME = "dataset.csv";
    private static final String MODELS_DIR_NAME = "models";
    private static final String ML_DIR_NAME = "ml";
    private static final String MODEL_FILE_PREFIX = "model-";
    private static final String MODEL_FILE_SUFFIX = ".ser";

    public MLCommandHandler(AntiXrayPlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        this.engineModule = plugin.getModuleManager().getModule(EngineModule.class);
        this.fingerprintEngine = new FingerprintEngine();
    }

    // ──────────────────────────────────────────────
    // Dataset subcommands
    // ──────────────────────────────────────────────

    /**
     * Handles /antixray dataset &lt;record|info|trim&gt;
     */
    public boolean handleDataset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /antixray dataset <record|info|trim>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "record" -> recordDataset(sender, args);
            case "info" -> showDatasetInfo(sender);
            case "trim" -> trimDataset(sender, args);
            default -> sender.sendMessage("§cUnknown dataset subcommand. Use /antixray dataset <record|info|trim>");
        }
        return true;
    }

    /**
     * /antixray dataset record &lt;player&gt; &lt;legit|cheater&gt;
     * Extracts fingerprint features from a player and records them as training data.
     */
    private void recordDataset(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /antixray dataset record <player> <legit|cheater>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        String rawLabel = args[3].toLowerCase();
        if (!rawLabel.equals("legit") && !rawLabel.equals("cheater")) {
            sender.sendMessage("§cUsage: /antixray dataset record <player> <legit|cheater>");
            return;
        }

        PlayerData data = dataManager.get(target.getUniqueId());
        if (data == null) {
            sender.sendMessage("§cNo data tracked for " + target.getName() + ".");
            return;
        }

        // Generate fingerprint (same mechanism as /antixray fingerprint)
        EngineData engineData = engineModule.getOrCompute(data);
        UUID sessionId = UUID.randomUUID();
        Fingerprint fingerprint = fingerprintEngine.generate(data, engineData, sessionId);

        // Convert to fixed-size feature vector (MLDataset expects exactly 12 features)
        double[] fullVector = fingerprint.toVector();
        double[] features = new double[EXPECTED_FEATURES];
        for (int i = 0; i < EXPECTED_FEATURES && i < fullVector.length; i++) {
            features[i] = fullVector[i];
        }

        // Add to dataset and persist
        String mlLabel = rawLabel.equals("cheater") ? "CHEATER" : "LEGIT";
        MLDataset dataset = getOrLoadDataset();
        dataset.addRow(features, mlLabel);

        try {
            ensureDirectoriesExist();
            dataset.saveToFile(getDatasetFile());
            sender.sendMessage("§aRecorded §e" + target.getName() + "§a as §e" + mlLabel
                    + "§a (total: §e" + dataset.size() + "§a rows)");
        } catch (IOException e) {
            sender.sendMessage("§cFailed to save dataset: " + e.getMessage());
            plugin.getLogger().warning("Failed to save ML dataset: " + e.getMessage());
        }
    }

    /**
     * /antixray dataset info
     * Displays dataset statistics.
     */
    private void showDatasetInfo(CommandSender sender) {
        File datasetFile = getDatasetFile();
        if (!datasetFile.exists()) {
            sender.sendMessage("§7No dataset found. Record some players first.");
            return;
        }

        MLDataset dataset = getOrLoadDataset();
        int total = dataset.size();
        int legit = dataset.getLegitCount();
        int cheater = dataset.getCheaterCount();

        sender.sendMessage("§8=== §6Dataset Info §8===");
        sender.sendMessage("§8Total rows: §e" + total);
        sender.sendMessage("§8Legit: §a" + legit);
        sender.sendMessage("§8Cheater: §c" + cheater);

        if (total > 0) {
            double legitPct = (double) legit / total * 100.0;
            double cheaterPct = (double) cheater / total * 100.0;
            sender.sendMessage("§8Legit ratio: §e" + String.format("%.1f%%", legitPct));
            sender.sendMessage("§8Cheater ratio: §e" + String.format("%.1f%%", cheaterPct));
            sender.sendMessage("§8Balanced: " + (dataset.isBalanced() ? "§a\u2714" : "§c\u2718 (consider adding more samples to the minority class)"));
        }

        sender.sendMessage("§8File: §7" + datasetFile.getAbsolutePath());
    }

    /**
     * /antixray dataset trim &lt;N&gt;
     * Removes the first N rows from the dataset.
     */
    private void trimDataset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /antixray dataset trim <N>");
            return;
        }

        int n;
        try {
            n = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + args[2]);
            return;
        }

        if (n <= 0) {
            sender.sendMessage("§cN must be positive.");
            return;
        }

        File datasetFile = getDatasetFile();
        if (!datasetFile.exists()) {
            sender.sendMessage("§7No dataset found.");
            return;
        }

        MLDataset dataset = getOrLoadDataset();
        if (n >= dataset.size()) {
            int totalRemoved = dataset.size();
            dataset.clear();
            sender.sendMessage("§eRemoved all " + totalRemoved + " rows. Dataset is now empty.");
        } else {
            int beforeSize = dataset.size();
            dataset.removeFirst(n);
            int removed = beforeSize - dataset.size();
            sender.sendMessage("§aRemoved §e" + removed + "§a rows. Dataset now has §e" + dataset.size() + "§a rows.");
        }

        try {
            dataset.saveToFile(datasetFile);
        } catch (IOException e) {
            sender.sendMessage("§cFailed to save dataset: " + e.getMessage());
            plugin.getLogger().warning("Failed to save trimmed dataset: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // ML subcommands
    // ──────────────────────────────────────────────

    /**
     * Handles /antixray ml &lt;train|status|models&gt;
     */
    public boolean handleML(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /antixray ml <train|status|models>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "train" -> trainModel(sender, args);
            case "status" -> showMLStatus(sender);
            case "models" -> listTrainedModels(sender);
            default -> sender.sendMessage("§cUnknown ml subcommand. Use /antixray ml <train|status|models>");
        }
        return true;
    }

    /**
     * /antixray ml train [trees] [depth]
     * Trains a RandomForest model using the current dataset.
     */
    private void trainModel(CommandSender sender, String[] args) {
        File datasetFile = getDatasetFile();
        if (!datasetFile.exists()) {
            sender.sendMessage("§cNo dataset found. Record some players first (/antixray dataset record).");
            return;
        }

        MLDataset dataset = getOrLoadDataset();
        if (dataset.size() == 0) {
            sender.sendMessage("§cDataset is empty. Record some players first.");
            return;
        }

        if (dataset.getLegitCount() < MIN_SAMPLES_PER_CLASS) {
            sender.sendMessage("§cNot enough legit samples. Need at least §e" + MIN_SAMPLES_PER_CLASS
                    + "§c, have §e" + dataset.getLegitCount());
            return;
        }
        if (dataset.getCheaterCount() < MIN_SAMPLES_PER_CLASS) {
            sender.sendMessage("§cNot enough cheater samples. Need at least §e" + MIN_SAMPLES_PER_CLASS
                    + "§c, have §e" + dataset.getCheaterCount());
            return;
        }

        // Parse optional parameters
        int trees = DEFAULT_TREES;
        int depth = DEFAULT_DEPTH;

        if (args.length >= 3) {
            try {
                trees = Integer.parseInt(args[2]);
                if (trees < 1) trees = DEFAULT_TREES;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid tree count: " + args[2] + ". Using default " + DEFAULT_TREES + ".");
            }
        }
        if (args.length >= 4) {
            try {
                depth = Integer.parseInt(args[3]);
                if (depth < 1) depth = DEFAULT_DEPTH;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid depth: " + args[3] + ". Using default " + DEFAULT_DEPTH + ".");
            }
        }

        sender.sendMessage("§7Training RandomForest with §e" + trees + "§7 trees, §e" + depth + "§7 max depth...");
        sender.sendMessage("§7Dataset: §e" + dataset.size() + "§7 rows (§a" + dataset.getLegitCount()
                + "§7 legit, §c" + dataset.getCheaterCount() + "§7 cheater)");

        // Optionally balance the dataset before training
        if (!dataset.isBalanced()) {
            sender.sendMessage("§7Dataset is imbalanced — applying automatic balancing...");
            dataset.balance();
            sender.sendMessage("§7Balanced to §e" + dataset.size() + "§7 rows (§a" + dataset.getLegitCount()
                    + "§7 legit, §c" + dataset.getCheaterCount() + "§7 cheater)");
        }

        // Train the model
        RandomForestModel model = new RandomForestModel();
        try {
            model.train(dataset, trees, depth, System.currentTimeMillis());
        } catch (Exception e) {
            sender.sendMessage("§cTraining failed: " + e.getMessage());
            plugin.getLogger().severe("ML model training failed: " + e.getMessage());
            return;
        }

        // Save the model
        try {
            ensureDirectoriesExist();
            File modelFile = getModelFile(model.getModelId());
            model.save(modelFile);

            sender.sendMessage("§a\u2714 Training complete!");
            sender.sendMessage("§8Model ID: §e" + model.getModelId());
            sender.sendMessage("§8Accuracy: §e" + String.format("%.2f%%", model.getAccuracy()));
            sender.sendMessage("§8Precision: §e" + String.format("%.2f%%", model.getPrecision()));
            sender.sendMessage("§8Recall: §e" + String.format("%.2f%%", model.getRecall()));
            sender.sendMessage("§8F1 Score: §e" + String.format("%.2f%%", model.getF1Score()));
            sender.sendMessage("§8Dataset size: §e" + model.getDatasetSize() + "§8 rows");
            sender.sendMessage("§8Saved: §7" + modelFile.getAbsolutePath());
        } catch (IOException e) {
            sender.sendMessage("§cFailed to save model: " + e.getMessage());
            plugin.getLogger().severe("Failed to save ML model: " + e.getMessage());
        }
    }

    /**
     * /antixray ml status
     * Shows the current ML model status and dataset stats.
     */
    private void showMLStatus(CommandSender sender) {
        sender.sendMessage("§8=== §6ML Status §8===");

        // Dataset info
        File datasetFile = getDatasetFile();
        if (datasetFile.exists()) {
            MLDataset dataset = getOrLoadDataset();
            sender.sendMessage("§8Dataset: §e" + dataset.size() + "§8 rows (§a" + dataset.getLegitCount()
                    + "§8 legit, §c" + dataset.getCheaterCount() + "§8 cheater)");
            sender.sendMessage("§8Balanced: " + (dataset.isBalanced() ? "§a\u2714" : "§c\u2718"));
        } else {
            sender.sendMessage("§8Dataset: §7(empty \u2014 record players with /antixray dataset record)");
        }

        // Active model info (most recently trained model)
        File modelsDir = getModelsDir();
        if (modelsDir.exists() && modelsDir.isDirectory()) {
            File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(MODEL_FILE_SUFFIX));
            if (modelFiles != null && modelFiles.length > 0) {
                // Find the most recent model
                File latestFile = Arrays.stream(modelFiles)
                        .max(Comparator.comparingLong(File::lastModified))
                        .orElse(null);

                if (latestFile != null) {
                    try {
                        RandomForestModel latest = RandomForestModel.load(latestFile);
                        sender.sendMessage("§8Active model: §e" + latest.getModelId());
                        sender.sendMessage("§8Trained at: §e" + (latest.getTrainedAt() != null
                                ? latest.getTrainedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "unknown"));
                        sender.sendMessage("§8Accuracy: §e" + String.format("%.2f%%", latest.getAccuracy()));
                        sender.sendMessage("§8Precision: §e" + String.format("%.2f%%", latest.getPrecision()));
                        sender.sendMessage("§8Recall: §e" + String.format("%.2f%%", latest.getRecall()));
                        sender.sendMessage("§8F1 Score: §e" + String.format("%.2f%%", latest.getF1Score()));
                        sender.sendMessage("§8Trees: §e" + latest.getNumTrees());
                        sender.sendMessage("§8File: §7" + latestFile.getAbsolutePath());
                    } catch (Exception e) {
                        sender.sendMessage("§8Active model: §7(could not load: " + e.getMessage() + ")");
                    }
                }
            } else {
                sender.sendMessage("§8Active model: §7(none \u2014 train one with /antixray ml train)");
            }
        } else {
            sender.sendMessage("§8Active model: §7(none \u2014 train one with /antixray ml train)");
        }
    }

    /**
     * /antixray ml models
     * Lists all trained models in the models directory.
     */
    private void listTrainedModels(CommandSender sender) {
        File modelsDir = getModelsDir();
        if (!modelsDir.exists() || !modelsDir.isDirectory()) {
            sender.sendMessage("§7No models found. Train one with /antixray ml train");
            return;
        }

        File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(MODEL_FILE_SUFFIX));
        if (modelFiles == null || modelFiles.length == 0) {
            sender.sendMessage("§7No models found. Train one with /antixray ml train");
            return;
        }

        sender.sendMessage("§8=== §6Trained Models (§e" + modelFiles.length + "§8) ===");

        // Sort by last modified descending (newest first)
        Arrays.sort(modelFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        for (int i = 0; i < modelFiles.length; i++) {
            File modelFile = modelFiles[i];
            try {
                RandomForestModel model = RandomForestModel.load(modelFile);

                String trainedDate = model.getTrainedAt() != null
                        ? model.getTrainedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "unknown";
                String accuracy = (model.getAccuracy() > 0)
                        ? String.format("%.1f%%", model.getAccuracy())
                        : "N/A";
                String datasetSize = model.getDatasetSize() > 0
                        ? String.valueOf(model.getDatasetSize())
                        : "N/A";

                sender.sendMessage(String.format(" \u00a78[%d] \u00a7e%s \u00a77(trained: \u00a7f%s\u00a77, acc: \u00a7a%s\u00a77, rows: \u00a7e%s\u00a77)",
                        i + 1,
                        model.getModelId(),
                        trainedDate,
                        accuracy,
                        datasetSize));

            } catch (Exception e) {
                sender.sendMessage(String.format(" \u00a78[%d] \u00a77%s \u00a7c(could not load: %s)",
                        i + 1, modelFile.getName(), e.getMessage()));
            }
        }
    }

    // ──────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────

    /**
     * Returns the dataset file at plugins/AntiXray/ml/dataset.csv.
     */
    private File getDatasetFile() {
        return new File(getMlDir(), DATASET_FILE_NAME);
    }

    /**
     * Returns the models directory at plugins/AntiXray/ml/models/.
     */
    private File getModelsDir() {
        return new File(getMlDir(), MODELS_DIR_NAME);
    }

    /**
     * Returns the ML base directory at plugins/AntiXray/ml/.
     */
    private File getMlDir() {
        return new File(plugin.getDataFolder(), ML_DIR_NAME);
    }

    /**
     * Returns the model file path for a given model ID.
     */
    private File getModelFile(String modelId) {
        return new File(getModelsDir(), MODEL_FILE_PREFIX + modelId + MODEL_FILE_SUFFIX);
    }

    /**
     * Ensures the ml/ and ml/models/ directories exist.
     */
    private void ensureDirectoriesExist() {
        File mlDir = getMlDir();
        if (!mlDir.exists()) {
            mlDir.mkdirs();
        }
        File modelsDir = getModelsDir();
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }
    }

    /**
     * Loads the dataset from disk, or returns a new empty dataset if the file
     * does not exist or cannot be read.
     */
    private MLDataset getOrLoadDataset() {
        File datasetFile = getDatasetFile();
        if (!datasetFile.exists()) {
            return new MLDataset();
        }

        MLDataset dataset = new MLDataset();
        try {
            dataset.loadFromFile(datasetFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load ML dataset from " + datasetFile.getAbsolutePath()
                    + ": " + e.getMessage());
            return new MLDataset();
        }

        return dataset;
    }

    /**
     * Lists all saved models from the models directory.
     * Models that fail to load are silently skipped.
     */
    private List<RandomForestModel> listModels() {
        List<RandomForestModel> models = new ArrayList<>();
        File modelsDir = getModelsDir();

        if (!modelsDir.exists() || !modelsDir.isDirectory()) {
            return models;
        }

        File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(MODEL_FILE_SUFFIX));
        if (modelFiles == null) {
            return models;
        }

        for (File file : modelFiles) {
            try {
                RandomForestModel model = RandomForestModel.load(file);
                models.add(model);
            } catch (Exception e) {
                // Silently skip corrupt models
                plugin.getLogger().fine("Skipping corrupt model file: " + file.getName() + " \u2014 " + e.getMessage());
            }
        }

        return models;
    }
}
