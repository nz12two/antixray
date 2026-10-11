package com.antixray.plugin.siem;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.api.events.PlayerFlaggedEvent;
import com.antixray.plugin.api.events.PlayerPunishmentEvent;
import com.antixray.plugin.api.events.PlayerRiskUpdateEvent;
import com.antixray.plugin.detection.result.DetectionBatch;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Módulo de logging SIEM que gera arquivos JSONL (JSON Lines)
 * para integração com ferramentas como Wazuh, ELK Stack, Splunk.
 * <p>
 * Escuta eventos internos do EventBus (PlayerFlaggedEvent, DetectionBatch,
 * PlayerPunishmentEvent, PlayerRiskUpdateEvent) e escreve cada ocorrência
 * como uma linha JSON no arquivo de log.
 * <p>
 * Thread-safe: todas as escritas são sincronizadas.
 * Silencioso: erros de I/O nunca quebram o plugin.
 */
public class SIEMLogger extends Module {

    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024; // 50 MB
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ROTATE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AntiXrayPlugin plugin;
    private final Gson gson;
    private final Object writeLock = new Object();

    private File logFile;
    private BufferedWriter writer;
    private long currentFileBytes;
    private String currentDateStr;

    private final List<String> lineBuffer = Collections.synchronizedList(new ArrayList<>());
    private static final int BUFFER_FLUSH_SIZE = 50;
    private static final long BUFFER_FLUSH_INTERVAL_MS = 5000;
    private long lastFlushTime = System.currentTimeMillis();
    private boolean flushTaskScheduled = false;

    // Referências dos handlers para limpeza no onDisable
    private Consumer<PlayerFlaggedEvent> flagHandler;
    private Consumer<DetectionBatch> detectionHandler;
    private Consumer<PlayerPunishmentEvent> punishmentHandler;
    private Consumer<PlayerRiskUpdateEvent> riskUpdateHandler;

    // ─────────────────────────────────────────────────────────────────
    // Construtor
    // ─────────────────────────────────────────────────────────────────

    public SIEMLogger(AntiXrayPlugin plugin) {
        super("SIEM");
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .setLenient()
                .create();
    }

    // ─────────────────────────────────────────────────────────────────
    // Ciclo de vida do módulo
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        try {
            File logDir = new File(plugin.getDataFolder(), "logs");
            if (!logDir.exists() && !logDir.mkdirs()) {
                plugin.getLogger().warning("[SIEM] Could not create logs directory at "
                        + logDir.getAbsolutePath());
                return;
            }
            openOrRotate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[SIEM] Failed to initialize log file", e);
            return;
        }

        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);

        flagHandler = event -> {
            if (isEnabled()) logFlag(event);
        };
        eventBus.register(PlayerFlaggedEvent.class, flagHandler);

        detectionHandler = event -> {
            if (isEnabled()) logDetection(event);
        };
        eventBus.register(DetectionBatch.class, detectionHandler);

        punishmentHandler = event -> {
            if (isEnabled()) logPunishment(event);
        };
        eventBus.register(PlayerPunishmentEvent.class, punishmentHandler);

        riskUpdateHandler = event -> {
            if (isEnabled()) logRiskUpdate(event);
        };
        eventBus.register(PlayerRiskUpdateEvent.class, riskUpdateHandler);

        // Task periódica para flush do buffer a cada 5s
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!lineBuffer.isEmpty()) {
                flushBuffer();
            }
        }, 100L, 100L);

        setEnabled(true);
        plugin.getLogger().info("[SIEM] Module enabled, logging to "
                + (logFile != null ? logFile.getAbsolutePath() : "N/A"));
    }

    @Override
    public void onDisable() {
        setEnabled(false);

        // Remove listeners do EventBus
        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        if (flagHandler != null) {
            eventBus.unregister(PlayerFlaggedEvent.class, flagHandler);
            flagHandler = null;
        }
        if (detectionHandler != null) {
            eventBus.unregister(DetectionBatch.class, detectionHandler);
            detectionHandler = null;
        }
        if (punishmentHandler != null) {
            eventBus.unregister(PlayerPunishmentEvent.class, punishmentHandler);
            punishmentHandler = null;
        }
        if (riskUpdateHandler != null) {
            eventBus.unregister(PlayerRiskUpdateEvent.class, riskUpdateHandler);
            riskUpdateHandler = null;
        }

        flushBuffer(); // Garante que tudo foi escrito
        closeWriter();
    }

    // ─────────────────────────────────────────────────────────────────
    // Métodos públicos de logging
    // ─────────────────────────────────────────────────────────────────

    /**
     * Registra um evento de flag (alarme) de um jogador.
     */
    public void logFlag(PlayerFlaggedEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject json = buildBase("FLAG",
                        event.getPlayerId(), event.getPlayerName(), event.getRiskScore());
                addDetectionResults(json, event.getResults());
                enrichWithPlayerContext(json, event.getPlayerId());
                writeLine(json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINEST, "[SIEM] Error logging flag event", e);
            }
        });
    }

    /**
     * Registra um batch de detecção (execução do pipeline de detecção).
     */
    public void logDetection(DetectionBatch batch) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                double overallScore = batch.getResults().stream()
                        .mapToDouble(DetectionResult::getScore)
                        .max()
                        .orElse(0.0);
                JsonObject json = buildBase("DETECTION",
                        batch.getPlayerId(), batch.getPlayerName(), overallScore);
                addDetectionResults(json, batch.getResults());
                enrichWithPlayerContext(json, batch.getPlayerId());
                writeLine(json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINEST, "[SIEM] Error logging detection event", e);
            }
        });
    }

    /**
     * Registra uma punição aplicada a um jogador.
     */
    public void logPunishment(PlayerPunishmentEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject json = buildBase("PUNISHMENT",
                        event.getPlayerId(), event.getPlayerName(), event.getRiskScore());
                JsonArray actions = new JsonArray();
                List<String> executed = event.getActionsExecuted();
                if (executed != null) {
                    for (String action : executed) {
                        actions.add(action);
                    }
                }
                json.add("actions", actions);
                enrichWithPlayerContext(json, event.getPlayerId());
                writeLine(json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINEST, "[SIEM] Error logging punishment event", e);
            }
        });
    }

    /**
     * Registra uma atualização de risco de um jogador.
     */
    public void logRiskUpdate(PlayerRiskUpdateEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject json = buildBase("RISK_UPDATE",
                        event.getPlayerId(), event.getPlayerName(), event.getRiskScore());
                JsonArray detectors = new JsonArray();
                Map<String, Double> scores = event.getDetectorScores();
                if (scores != null) {
                    for (Map.Entry<String, Double> entry : scores.entrySet()) {
                        detectors.add(entry.getKey() + ": "
                                + String.format("%.1f", entry.getValue()));
                    }
                }
                json.add("detectors", detectors);
                enrichWithPlayerContext(json, event.getPlayerId());
                writeLine(json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINEST, "[SIEM] Error logging risk update event", e);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // Construção do JSON base
    // ─────────────────────────────────────────────────────────────────

    private JsonObject buildBase(String eventType, UUID playerId,
                                  String playerName, double riskScore) {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", Instant.now().toString());
        json.addProperty("event_type", eventType);
        json.addProperty("player_id", playerId != null ? playerId.toString() : null);
        json.addProperty("player_name", playerName);
        json.addProperty("risk_score", riskScore);
        json.addProperty("confidence", 0.0);
        json.add("detectors", new JsonArray());
        json.add("categories", new JsonArray());
        json.addProperty("minecraft_version", getMinecraftVersion());
        json.addProperty("server_tps", getServerTPS());
        json.addProperty("plugin_version", plugin.getPluginMeta().getVersion());
        json.addProperty("world", "");
        json.addProperty("dimension", "");
        json.addProperty("total_blocks_mined", 0);
        json.addProperty("total_ores_found", 0);
        json.addProperty("session_minutes", 0.0);
        return json;
    }

    private void addDetectionResults(JsonObject json, List<DetectionResult> results) {
        if (results == null || results.isEmpty()) return;

        JsonArray detectors = new JsonArray();
        double maxConfidence = 0.0;

        for (DetectionResult r : results) {
            if (r == null) continue;
            detectors.add(r.getDetectorName() + ": "
                    + String.format("%.1f", r.getScore()));
            if (r.getConfidence() > maxConfidence) {
                maxConfidence = r.getConfidence();
            }
        }

        json.add("detectors", detectors);
        json.addProperty("confidence", maxConfidence);
    }

    // ─────────────────────────────────────────────────────────────────
    // Enriquecimento com dados do jogador (PlayerData)
    // ─────────────────────────────────────────────────────────────────

    private void enrichWithPlayerContext(JsonObject json, UUID playerId) {
        if (playerId == null) return;

        try {
            PlayerDataManager dataManager = plugin.getServiceRegistry()
                    .resolve(PlayerDataManager.class);
            if (dataManager == null) return;

            PlayerData data = dataManager.get(playerId);
            if (data == null) return;

            json.addProperty("total_blocks_mined", data.getTotalBlocksMined());
            json.addProperty("total_ores_found", data.getTotalOresFound());

            long sessionStart = data.getSessionStartTime();
            if (sessionStart > 0) {
                double minutes = (System.currentTimeMillis() - sessionStart) / 60000.0;
                json.addProperty("session_minutes", Math.round(minutes * 10.0) / 10.0);
            }

            // Informações do mundo/dimensão (Bukkit API)
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                World world = player.getWorld();
                json.addProperty("world", world.getName());
                json.addProperty("dimension", world.getEnvironment().name());
            }
        } catch (Exception e) {
            // Silencioso — dados contextuais não críticos
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Escrita no arquivo JSONL (thread-safe)
    // ─────────────────────────────────────────────────────────────────

    private void writeLine(JsonObject json) {
        String line = gson.toJson(json) + System.lineSeparator();
        lineBuffer.add(line);

        // Verifica se precisa flush
        long now = System.currentTimeMillis();
        if (lineBuffer.size() >= BUFFER_FLUSH_SIZE || (now - lastFlushTime) >= BUFFER_FLUSH_INTERVAL_MS) {
            flushBuffer();
        }

        // Agenda flush se não houver um agendado
        if (!flushTaskScheduled && !lineBuffer.isEmpty()) {
            flushTaskScheduled = true;
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                flushBuffer();
                flushTaskScheduled = false;
            }, 100L); // 5 segundos em ticks
        }
    }

    private void flushBuffer() {
        List<String> batch;
        synchronized (lineBuffer) {
            if (lineBuffer.isEmpty()) return;
            batch = new ArrayList<>(lineBuffer);
            lineBuffer.clear();
        }

        lastFlushTime = System.currentTimeMillis();

        synchronized (writeLock) {
            try {
                ensureWriterReady();
                if (writer == null) return;

                for (String line : batch) {
                    writer.write(line);
                }
                writer.flush();

                // Rotação se necessário (checa tamanho do arquivo)
                String today = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
                if (logFile != null && logFile.length() >= MAX_FILE_BYTES || !today.equals(currentDateStr)) {
                    rotateLog();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINEST, "[SIEM] Error flushing buffer", e);
            }
        }
    }

    private void ensureWriterReady() throws IOException {
        String today = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
        if (writer == null || !today.equals(currentDateStr)) {
            closeWriter();
            openOrRotate();
        }
    }

    private void openOrRotate() throws IOException {
        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        currentDateStr = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
        logFile = new File(logDir, "antixray-siem.jsonl");

        if (logFile.exists()) {
            currentFileBytes = logFile.length();
            if (currentFileBytes >= MAX_FILE_BYTES) {
                rotateLog();
                return;
            }
        } else {
            currentFileBytes = 0;
        }

        writer = new BufferedWriter(
                new FileWriter(logFile, StandardCharsets.UTF_8, true));
    }

    private void rotateLog() throws IOException {
        closeWriter();
        File logDir = new File(plugin.getDataFolder(), "logs");
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(ROTATE_TIMESTAMP);
        File archive = new File(logDir, "antixray-siem-" + timestamp + ".jsonl");

        if (logFile != null && logFile.exists()) {
            boolean renamed = logFile.renameTo(archive);
            if (!renamed) {
                plugin.getLogger().warning("[SIEM] Log rotation: failed to rename "
                        + logFile.getName() + " to " + archive.getName());
            }
        }

        currentFileBytes = 0;
        currentDateStr = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
        logFile = new File(logDir, "antixray-siem.jsonl");
        writer = new BufferedWriter(
                new FileWriter(logFile, StandardCharsets.UTF_8, true));
    }

    private void closeWriter() {
        synchronized (writeLock) {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINEST,
                            "[SIEM] Error closing log writer", e);
                }
                writer = null;
                logFile = null;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Utilitários
    // ─────────────────────────────────────────────────────────────────

    private String getMinecraftVersion() {
        try {
            return Bukkit.getBukkitVersion().split("-")[0];
        } catch (Exception e) {
            return "unknown";
        }
    }

    private double getServerTPS() {
        try {
            // Usa reflection para compatibilidade (Paper/Bukkit)
            Object server = Bukkit.class.getMethod("getServer").invoke(null);
            double[] tps = (double[]) server.getClass()
                    .getMethod("getTPS")
                    .invoke(server);
            if (tps != null && tps.length > 0) {
                return Math.round(tps[0] * 10.0) / 10.0;
            }
        } catch (Exception e) {
            // Fallback silencioso
        }
        return 20.0;
    }
}
