package com.antixray.plugin.commands.commands;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.api.AntiXrayAPI;
import com.antixray.plugin.detection.DetectionModule;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.evidence.Evidence;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import com.antixray.plugin.profiler.DetectorProfiler;
import com.antixray.plugin.learning.HistoricalLearningModule;
import com.antixray.plugin.punishment.PunishmentModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AntiXrayBaseCommand implements CommandExecutor, TabCompleter {
    private final AntiXrayPlugin plugin;
    private final AntiXrayAPI api;

    private static final Component PREFIX = Component.text()
            .append(Component.text("[", NamedTextColor.GRAY))
            .append(Component.text("AntiXray", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.GRAY))
            .build();

    public AntiXrayBaseCommand(AntiXrayPlugin plugin) {
        this.plugin = plugin;
        this.api = plugin.getServiceRegistry().resolve(AntiXrayAPI.class);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "check" -> checkPlayer(sender, args);
            case "top" -> listTop(sender);
            case "info" -> playerInfo(sender, args);
            case "evidence" -> showEvidence(sender, args);
            case "reset" -> resetPlayer(sender, args);
            case "reload" -> reloadConfig(sender);
            case "detectors" -> listDetectors(sender);
            case "profiler" -> showProfiler(sender);
            case "confirm" -> confirmXray(sender, args);
            case "falseflag" -> falseFlag(sender, args);
            case "fingerprint" -> showFingerprint(sender, args);
            case "stats" -> serverStats(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text(" Commands:", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  /antixray check <player> ", NamedTextColor.GRAY)
                .append(Component.text("- Check player's risk score", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray top ", NamedTextColor.GRAY)
                .append(Component.text("- Show top flagged players", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray info <player> ", NamedTextColor.GRAY)
                .append(Component.text("- Show detailed player info", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray evidence <player> ", NamedTextColor.GRAY)
                .append(Component.text("- Show player evidence", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray reset <player> ", NamedTextColor.GRAY)
                .append(Component.text("- Reset player's risk data", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray reload ", NamedTextColor.GRAY)
                .append(Component.text("- Reload configuration", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray detectors ", NamedTextColor.GRAY)
                .append(Component.text("- List all detectors", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray profiler ", NamedTextColor.GRAY)
                .append(Component.text("- Detector performance & false positive rate", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray confirm <player> ", NamedTextColor.GRAY)
                .append(Component.text("- Confirm player was using xray", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray falseflag <player> ", NamedTextColor.GRAY)
                .append(Component.text("- Mark flag as false positive", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray fingerprint <player> ", NamedTextColor.GRAY)
                .append(Component.text("- Show fingerprint similarity analysis", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /antixray stats ", NamedTextColor.GRAY)
                .append(Component.text("- Show server statistics", NamedTextColor.WHITE)));
    }

    private boolean checkPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text(" Usage: /antixray check <player>")));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Player not found.", NamedTextColor.RED)));
            return true;
        }
        double score = api.getPlayerRiskScore(target.getUniqueId());
        double peakScore = api.getPlayerPeakRiskScore(target.getUniqueId());
        int flags = api.getPlayerTotalFlags(target.getUniqueId());

        sender.sendMessage(PREFIX.append(Component.text(" Player: ", NamedTextColor.GOLD))
                .append(Component.text(target.getName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Risk Score: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", score), getScoreColor(score))));
        sender.sendMessage(Component.text("  Peak Risk: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", peakScore), getScoreColor(peakScore))));
        sender.sendMessage(Component.text("  Total Flags: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(flags), NamedTextColor.WHITE)));
        return true;
    }

    private boolean listTop(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text(" Top Flagged Players:", NamedTextColor.GOLD)));
        PlayerDataManager dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);

        dataManager.getAll().entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().getRiskProfile().getPeakRiskScore(),
                        a.getValue().getRiskProfile().getPeakRiskScore()))
                .limit(10)
                .forEach(entry -> {
                    PlayerData data = entry.getValue();
                    sender.sendMessage(Component.text("  " + data.getPlayerName() + " - Risk: ", NamedTextColor.GRAY)
                            .append(Component.text(String.format("%.1f", data.getRiskProfile().getCurrentRiskScore()),
                                    getScoreColor(data.getRiskProfile().getCurrentRiskScore())))
                            .append(Component.text(" (Peak: " + String.format("%.1f", data.getRiskProfile().getPeakRiskScore()) + ")", NamedTextColor.GRAY)));
                });
        return true;
    }

    private boolean playerInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text(" Usage: /antixray info <player>")));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Player not found.", NamedTextColor.RED)));
            return true;
        }
        PlayerData data = plugin.getServiceRegistry().resolve(PlayerDataManager.class).get(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(PREFIX.append(Component.text(" No data for this player.", NamedTextColor.RED)));
            return true;
        }

        sender.sendMessage(PREFIX.append(Component.text(" Detailed Info: ", NamedTextColor.GOLD))
                .append(Component.text(target.getName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Blocks mined: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(data.getTotalBlocksMined()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Ores found: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(data.getTotalOresFound()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Mining efficiency: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f%%", data.getStatistics().getMiningEfficiency()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Ore ratio: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.4f", data.getStatistics().getOreToBlockRatio()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Hidden/exposed ratio: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", data.getStatistics().getHiddenToExposedRatio()), NamedTextColor.WHITE)));

        Map<String, Double> detectorScores = data.getRiskProfile().getDetectorScores();
        if (!detectorScores.isEmpty()) {
            sender.sendMessage(Component.text("  Detector scores:", NamedTextColor.GRAY));
            detectorScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> sender.sendMessage(Component.text("    " + e.getKey() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(String.format("%.0f", e.getValue()), getScoreColor(e.getValue())))));
        }
        return true;
    }

    private boolean showEvidence(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text(" Usage: /antixray evidence <player>")));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Player not found.", NamedTextColor.RED)));
            return true;
        }
        List<Evidence> evidenceList = api.getPlayerEvidence(target.getUniqueId());
        if (evidenceList.isEmpty()) {
            sender.sendMessage(PREFIX.append(Component.text(" No evidence for this player.", NamedTextColor.YELLOW)));
            return true;
        }
        sender.sendMessage(PREFIX.append(Component.text(" Evidence for ", NamedTextColor.GOLD))
                .append(Component.text(target.getName() + ":", NamedTextColor.GOLD)));
        for (int i = Math.max(0, evidenceList.size() - 5); i < evidenceList.size(); i++) {
            Evidence ev = evidenceList.get(i);
            sender.sendMessage(Component.text("  [" + (i + 1) + "] ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("Risk: %.0f", ev.getTotalRiskScore()), NamedTextColor.WHITE))
                    .append(Component.text(" | " + ev.getTriggeredDetectors().size() + " detectors", NamedTextColor.GRAY))
                    .append(Component.text(" | " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(ev.getTimestamp())), NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean resetPlayer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("antixray.admin")) {
            sender.sendMessage(PREFIX.append(Component.text(" No permission.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text(" Usage: /antixray reset <player>")));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Player not found.", NamedTextColor.RED)));
            return true;
        }
        PlayerData data = plugin.getServiceRegistry().resolve(PlayerDataManager.class).get(target.getUniqueId());
        if (data != null) {
            data.clearData();
            data.getRiskProfile().setCurrentRiskScore(0);
        }
        sender.sendMessage(PREFIX.append(Component.text(" Data reset for " + target.getName(), NamedTextColor.GREEN)));
        return true;
    }

    private boolean reloadConfig(CommandSender sender) {
        if (!sender.hasPermission("antixray.admin")) {
            sender.sendMessage(PREFIX.append(Component.text(" No permission.", NamedTextColor.RED)));
            return true;
        }
        plugin.reloadConfig();
        PunishmentModule pm = plugin.getModuleManager().getModule(PunishmentModule.class);
        if (pm != null) pm.reload();
        sender.sendMessage(PREFIX.append(Component.text(" Configuration reloaded. ", NamedTextColor.GREEN))
                .append(Component.text(pm != null ? "Punishments reloaded." : "", NamedTextColor.GRAY)));
        return true;
    }

    private boolean listDetectors(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text(" Detectors:", NamedTextColor.GOLD)));
        for (Detector detector : api.getDetectors()) {
            NamedTextColor color = detector.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED;
            sender.sendMessage(Component.text("  ", color)
                    .append(Component.text(detector.isEnabled() ? "\u2714" : "\u2718", color))
                    .append(Component.text(" ", color))
                    .append(Component.text(detector.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" - " + detector.getDescription(), NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean showProfiler(CommandSender sender) {
        if (!sender.hasPermission("antixray.admin")) {
            sender.sendMessage(PREFIX.append(Component.text(" No permission.", NamedTextColor.RED)));
            return true;
        }
        DetectionModule detectionModule = plugin.getDetectionModule();
        if (detectionModule == null) return true;
        DetectorProfiler profiler = detectionModule.getProfiler();
        List<DetectorProfiler.ProfileSnapshot> snapshots = profiler.getAllSnapshots();
        if (snapshots.isEmpty()) {
            sender.sendMessage(PREFIX.append(Component.text(" No profiler data yet.", NamedTextColor.YELLOW)));
            return true;
        }
        sender.sendMessage(PREFIX.append(Component.text(" Detector Profiler:", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  " + String.format("%-22s %-10s %-10s %-8s %s", "Detector", "Avg", "Max", "Flags", "FP Rate"), NamedTextColor.GRAY));
        for (DetectorProfiler.ProfileSnapshot s : snapshots) {
            NamedTextColor color = s.averageTimeMs() > 1.0 ? NamedTextColor.RED
                    : s.averageTimeMs() > 0.5 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
            sender.sendMessage(Component.text("  " + String.format("%-22s ", s.detectorName()), NamedTextColor.GRAY)
                    .append(Component.text(String.format("%-10s", String.format("%.2fms", s.averageTimeMs())), color))
                    .append(Component.text(String.format("%-10s", String.format("%.2fms", s.maxTimeMs())), NamedTextColor.GRAY))
                    .append(Component.text(String.format("%-8d", s.flagsGenerated()), NamedTextColor.GOLD))
                    .append(Component.text(String.format("%.1f%%", s.falsePositiveRate() * 100),
                            s.falsePositiveRate() > 0.1 ? NamedTextColor.RED : NamedTextColor.GREEN)));
        }
        return true;
    }

    private boolean confirmXray(CommandSender sender, String[] args) {
        if (!sender.hasPermission("antixray.admin")) {
            sender.sendMessage(PREFIX.append(Component.text(" No permission.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text(" Usage: /antixray confirm <player>")));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Player not found.", NamedTextColor.RED)));
            return true;
        }
        HistoricalLearningModule hlm = plugin.getModuleManager().getModule(HistoricalLearningModule.class);
        if (hlm != null) {
            hlm.labelPositive(target.getUniqueId());
            sender.sendMessage(PREFIX.append(Component.text(" Confirmed xray for " + target.getName(), NamedTextColor.GREEN))
                    .append(Component.text(". Fingerprints labeled POSITIVE.")));
        }
        return true;
    }

    private boolean falseFlag(CommandSender sender, String[] args) {
        if (!sender.hasPermission("antixray.admin")) {
            sender.sendMessage(PREFIX.append(Component.text(" No permission.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text(" Usage: /antixray falseflag <player>")));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Player not found.", NamedTextColor.RED)));
            return true;
        }
        HistoricalLearningModule hlm = plugin.getModuleManager().getModule(HistoricalLearningModule.class);
        if (hlm != null) {
            hlm.labelNegative(target.getUniqueId());
            sender.sendMessage(PREFIX.append(Component.text(" Marked " + target.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" as false positive. Fingerprints labeled NEGATIVE.")));
        }
        return true;
    }

    private boolean showFingerprint(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text(" Usage: /antixray fingerprint <player>")));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Player not found.", NamedTextColor.RED)));
            return true;
        }
        HistoricalLearningModule hlm = plugin.getModuleManager().getModule(HistoricalLearningModule.class);
        if (hlm == null) {
            sender.sendMessage(PREFIX.append(Component.text(" Historical learning not available.", NamedTextColor.RED)));
            return true;
        }
        String analysis = hlm.explainFull(target.getUniqueId());
        if (analysis == null) {
            sender.sendMessage(PREFIX.append(Component.text(" No historical fingerprint data for " + target.getName(), NamedTextColor.YELLOW)));
            return true;
        }
        sender.sendMessage(PREFIX.append(Component.text(" Fingerprint Analysis: ", NamedTextColor.GOLD))
                .append(Component.text(target.getName(), NamedTextColor.WHITE)));
        for (String line : analysis.split("\n")) sender.sendMessage(Component.text(line));
        return true;
    }

    private boolean serverStats(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text(" Server Statistics:", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  Avg blocks/min: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", api.getServerAverageBlocksPerMinute()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Avg ore rate: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.4f", api.getServerAverageOreRate()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Tracked players: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(plugin.getServiceRegistry().resolve(PlayerDataManager.class).size()), NamedTextColor.WHITE)));
        return true;
    }

    private NamedTextColor getScoreColor(double score) {
        if (score >= 80) return NamedTextColor.RED;
        if (score >= 60) return NamedTextColor.GOLD;
        if (score >= 50) return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("check", "top", "info", "evidence", "reset", "reload", "detectors", "profiler", "confirm", "falseflag", "fingerprint", "stats").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("evidence") || args[0].equalsIgnoreCase("reset"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
