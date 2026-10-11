package com.antixray.plugin.punishment;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.api.events.PlayerFlaggedEvent;
import com.antixray.plugin.detection.result.DetectionResult;
import com.antixray.plugin.learning.HistoricalLearningModule;
import com.antixray.plugin.learning.ThreeTierStore;
import com.antixray.plugin.learning.RiskHistory;
import com.antixray.plugin.learning.Fingerprint;
import com.antixray.plugin.learning.DetectorConsensus;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import com.antixray.plugin.replay.ReplayModule;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<PunishmentStage, StageConfig> stageConfigs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<PunishmentStage, Long>> cooldowns = new ConcurrentHashMap<>();

    private PlayerDataManager dataManager;
    private HistoricalLearningModule hlm;
    private ThreeTierStore store;
    private RiskHistory riskHistory;
    private DetectorConsensus detectorConsensus;
    private ReplayModule replayModule;

    public PunishmentModule(AntiXrayPlugin plugin) {
        super("punishment");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        this.hlm = plugin.getModuleManager().getModule(HistoricalLearningModule.class);
        this.store = plugin.getServiceRegistry().resolveOrNull(ThreeTierStore.class);
        this.riskHistory = plugin.getServiceRegistry().resolveOrNull(RiskHistory.class);
        this.detectorConsensus = plugin.getServiceRegistry().resolveOrNull(DetectorConsensus.class);
        this.replayModule = plugin.getModuleManager().getModule(ReplayModule.class);

        plugin.getServiceRegistry().register(PunishmentModule.class, this);
        loadConfig();

        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        eventBus.register(PlayerFlaggedEvent.class, this::onFlagged);
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
        stageConfigs.clear();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection punishments = config.getConfigurationSection("punishments");
        if (punishments == null) return;

        stageConfigs.clear();
        for (PunishmentStage stage : PunishmentStage.values()) {
            String key = stage.name().toLowerCase();
            ConfigurationSection section = punishments.getConfigurationSection(key);
            stageConfigs.put(stage, StageConfig.fromSection(section));
        }
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfig();
        cooldowns.clear();
        plugin.getLogger().info("[Punishment] Config reloaded. Stages active: "
                + stageConfigs.entrySet().stream()
                        .filter(e -> e.getValue().enabled())
                        .map(e -> e.getKey().name())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("none"));
    }

    private void onFlagged(PlayerFlaggedEvent event) {
        UUID playerId = event.getPlayerId();
        PlayerData data = dataManager.get(playerId);
        if (data == null) return;

        PunishmentEvaluation eval = buildEvaluation(playerId, event);
        if (eval == null) return;

        for (PunishmentStage stage : PunishmentStage.values()) {
            StageConfig cfg = stageConfigs.get(stage);
            if (cfg == null || !cfg.enabled()) continue;
            if (isOnCooldown(playerId, stage)) continue;
            if (!eval.qualifiesFor(cfg)) continue;

            executeStage(playerId, stage, cfg, eval);
            setCooldown(playerId, stage, cfg.cooldownSeconds());
        }
    }

    private PunishmentEvaluation buildEvaluation(UUID playerId, PlayerFlaggedEvent event) {
        PlayerData data = dataManager.get(playerId);
        Fingerprint fp = hlm != null ? hlm.recordSession(playerId) : null;

        double riskScore = event.getRiskScore();
        double totalConf = 0;
        int confCount = 0;
        Map<String, Double> detectorScores = new HashMap<>();
        if (event.getResults() != null) {
            for (DetectionResult r : event.getResults()) {
                if (r.isSignificant()) {
                    detectorScores.put(r.getDetectorName(), r.getScore());
                    totalConf += r.getConfidence();
                    confCount++;
                }
            }
        }
        double confidence = confCount > 0 ? totalConf / confCount : 50;

        DetectorConsensus.ConsensusResult cr = detectorConsensus != null
                ? detectorConsensus.evaluate(detectorScores, 1)
                : null;

        int activeDetectors = cr != null ? cr.detectorCount() : 0;
        int categoryCount = cr != null ? cr.categoryCount() : 0;
        List<String> categories = cr != null ? cr.categories() : List.of();
        int metaFlagCount = countMetaFlags(detectorScores);

        double persistenceMinutes = 0;
        int sessionCount = 0;
        if (riskHistory != null && riskHistory.isSustainedAbove(playerId, 60, 600_000)) {
            persistenceMinutes = riskHistory.sampleCount(playerId) > 0 ? 10 : 0;
        }
        if (riskHistory != null) {
            persistenceMinutes = Math.max(persistenceMinutes,
                    riskHistory.getLast(playerId, 1).stream()
                            .mapToDouble(s -> (System.currentTimeMillis() - s.timestamp()) / 60_000.0)
                            .findFirst().orElse(0));
        }

        if (fp != null) sessionCount = 1;

        double fpSimilarity = 0;
        if (store != null && fp != null) {
            fpSimilarity = store.bestPositiveSimilarity(fp);
        }

        boolean hasReplay = replayModule != null;

        return new PunishmentEvaluation.Builder()
                .playerId(playerId)
                .playerName(data.getPlayerName() != null ? data.getPlayerName() : "unknown")
                .riskScore(riskScore)
                .confidence(confidence)
                .activeDetectors(activeDetectors)
                .categoryCount(categoryCount)
                .categories(categories)
                .metaFlagCount(metaFlagCount)
                .persistenceMinutes(persistenceMinutes)
                .sessionCount(sessionCount)
                .fingerprintSimilarity(fpSimilarity)
                .hasReplayEvidence(hasReplay)
                .hasRecentFlags(true)
                .build();
    }

    private int countMetaFlags(Map<String, Double> scores) {
        if (scores == null) return 0;
        return (int) scores.entrySet().stream()
                .filter(e -> e.getValue() >= 80)
                .count();
    }

    private void executeStage(UUID playerId, PunishmentStage stage, StageConfig cfg, PunishmentEvaluation eval) {
        Player player = Bukkit.getPlayer(playerId);
        String name = eval.playerName();

        switch (stage) {
            case LOG:
                break;

            case EVIDENCE:
                break;

            case MONITOR:
                break;

            case HEAVY:
                break;

            case NOTIFY:
                String msg = cfg.message().isEmpty()
                        ? "§8[§cAntiXray§8] §e" + name + " §7flagged §c" + String.format("%.0f", eval.riskScore())
                        + "%§7 risk"
                        : cfg.message().replace("%player%", name)
                                .replace("%risk%", String.format("%.0f", eval.riskScore()))
                                .replace("%confidence%", String.format("%.0f", eval.confidence()));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("antixray.notify")) {
                        p.sendMessage(msg);
                    }
                }
                break;

            case DISCORD:
                break;

            case FREEZE:
                if (player != null) {
                    String freezeMsg = cfg.message().isEmpty()
                            ? "§cYou have been frozen. Please wait for staff."
                            : cfg.message();
                    player.sendMessage(freezeMsg);
                    player.setWalkSpeed(0);
                    player.setFlySpeed(0);
                }
                break;

            case COMMAND:
                for (String cmd : cfg.commands()) {
                    String resolved = cmd.replace("%player%", name)
                            .replace("%risk%", String.format("%.0f", eval.riskScore()))
                            .replace("%uuid%", playerId.toString());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
                break;

            case KICK:
                if (player != null) {
                    String kickMsg = cfg.message().isEmpty()
                            ? "§cDisconnected. If you believe this is an error, contact staff."
                            : cfg.message();
                    player.kick(Component.text(kickMsg));
                }
                break;

            case BAN:
                if (player != null) {
                    String banMsg = cfg.message().isEmpty()
                            ? "§cBanned for suspicious activity."
                            : cfg.message();
                    player.ban(banMsg, (java.time.Instant) null, "AntiXray");
                }
                break;
        }
    }

    private boolean isOnCooldown(UUID playerId, PunishmentStage stage) {
        Map<PunishmentStage, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return false;
        Long expiry = playerCooldowns.get(stage);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    private void setCooldown(UUID playerId, PunishmentStage stage, long seconds) {
        cooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(stage, System.currentTimeMillis() + seconds * 1000);
    }

    public StageConfig getStageConfig(PunishmentStage stage) {
        return stageConfigs.get(stage);
    }

    public Map<PunishmentStage, StageConfig> allStageConfigs() {
        return Collections.unmodifiableMap(stageConfigs);
    }
}
