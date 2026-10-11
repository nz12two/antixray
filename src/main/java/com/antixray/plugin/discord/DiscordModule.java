package com.antixray.plugin.discord;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.api.events.PlayerFlaggedEvent;
import com.antixray.plugin.config.ConfigLoader.DiscordConfig;
import com.antixray.plugin.detection.result.DetectionResult;
import org.bukkit.Bukkit;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Discord webhook notification module with rate limiting, severity colors,
 * role pings, and compact mode support.
 */
public class DiscordModule extends Module {
    private final AntiXrayPlugin plugin;
    private DiscordConfig config;
    private EventBus eventBus;
    private volatile long lastWebhookTime = 0;

    public DiscordModule(AntiXrayPlugin plugin) {
        super("discord");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.config = plugin.getConfigLoader().getDiscordConfig();
        this.eventBus = plugin.getServiceRegistry().resolve(EventBus.class);

        if (config.enabled()) {
            eventBus.register(PlayerFlaggedEvent.class, this::onPlayerFlagged);
        }
    }

    @Override
    public void onDisable() {}

    @Override
    public void onReload() {
        this.config = plugin.getConfigLoader().getDiscordConfig();
    }

    private void onPlayerFlagged(PlayerFlaggedEvent event) {
        if (!config.enabled() || config.webhookUrl().isEmpty()) return;
        if (config.notifyFlagsOnly() && event.getRiskScore() < config.riskThreshold()) return;

        // Rate limiting
        long now = System.currentTimeMillis();
        if (now - lastWebhookTime < config.rateLimitMs()) return;
        lastWebhookTime = now;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sendWebhook(event);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }

    private void sendWebhook(PlayerFlaggedEvent event) throws Exception {
        String json = buildJson(event);
        URL url = new URI(config.webhookUrl()).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 204) {
            plugin.getLogger().warning("Discord webhook returned: " + responseCode);
        }
        conn.disconnect();
    }

    private String buildJson(PlayerFlaggedEvent event) {
        double riskScore = event.getRiskScore();
        int color = getSeverityColor(riskScore);
        String rolePing = getRolePing(riskScore);
        StringBuilder content = new StringBuilder();
        if (!rolePing.isEmpty()) {
            content.append(rolePing).append(" ");
        }
        content.append("**AntiXray - ").append(getSeverityLabel(riskScore)).append("**");

        StringBuilder fields = new StringBuilder();
        List<DetectionResult> results = event.getResults();

        int maxFields = config.compactMode() ? 3 : 5;
        for (int i = 0; i < Math.min(results.size(), maxFields); i++) {
            DetectionResult r = results.get(i);
            fields.append("{")
                    .append("\"name\":\"").append(escape(r.getDetectorName())).append("\",")
                    .append("\"value\":\"Score: ").append((int) r.getScore())
                    .append(" | Conf: ").append((int) r.getConfidence())
                    .append("%\\n").append(escape(truncate(r.getReason(), 80))).append("\",")
                    .append("\"inline\":").append(!config.compactMode()).append("},");
        }

        if (fields.length() > 0) fields.setLength(fields.length() - 1);

        if (config.compactMode()) {
            // Compact embed — condensed info
            return "{" +
                    "\"content\":\"" + escape(content.toString()) + "\"," +
                    "\"embeds\":[{" +
                    "\"color\":" + color + "," +
                    "\"fields\":[" +
                    "{\"name\":\"Player\",\"value\":\"" + escape(event.getPlayerName()) + "\",\"inline\":true}," +
                    "{\"name\":\"Risk\",\"value\":\"" + String.format("%.0f", riskScore) + "\",\"inline\":true}," +
                    "{\"name\":\"Detectors\",\"value\":\"" + results.size() + "\",\"inline\":true}" +
                    (fields.length() > 0 ? "," + fields : "") +
                    "]," +
                    "\"footer\":{\"text\":\"AntiXray v" + plugin.getPluginMeta().getVersion() + "\"}," +
                    "\"timestamp\":\"" + Instant.now().toString() + "\"" +
                    "}]}";
        } else {
            // Full embed — detailed info
            return "{" +
                    "\"content\":\"" + escape(content.toString()) + "\"," +
                    "\"embeds\":[{" +
                    "\"title\":\"Player Flagged\"," +
                    "\"color\":" + color + "," +
                    "\"fields\":[" +
                    "{\"name\":\"Player\",\"value\":\"" + escape(event.getPlayerName()) + "\",\"inline\":true}," +
                    "{\"name\":\"UUID\",\"value\":\"" + event.getPlayerId().toString() + "\",\"inline\":true}," +
                    "{\"name\":\"Risk Score\",\"value\":\"" + String.format("%.0f", riskScore) + "\",\"inline\":true}," +
                    "{\"name\":\"Detectors Triggered\",\"value\":\"" + results.size() + "\",\"inline\":true}," +
                    "{\"name\":\"Top Detection\",\"value\":\"" + escape(results.isEmpty() ? "N/A" : results.get(0).getReason()) + "\",\"inline\":false}" +
                    (fields.length() > 0 ? "," + fields : "") +
                    "]," +
                    "\"footer\":{\"text\":\"AntiXray v" + plugin.getPluginMeta().getVersion() + "\"}," +
                    "\"timestamp\":\"" + Instant.now().toString() + "\"" +
                    "}]}";
        }
    }

    private int getSeverityColor(double riskScore) {
        if (riskScore >= config.riskThreshold() * 2) return config.colorPunish();
        if (riskScore >= config.riskThreshold() * 1.5) return config.colorFlag();
        return config.colorAlert();
    }

    private String getRolePing(double riskScore) {
        if (riskScore >= config.riskThreshold() * 2) return config.pingPunish();
        if (riskScore >= config.riskThreshold() * 1.5) return config.pingFlag();
        return config.pingAlert();
    }

    private String getSeverityLabel(double riskScore) {
        if (riskScore >= config.riskThreshold() * 2) return "PUNISH";
        if (riskScore >= config.riskThreshold() * 1.5) return "FLAG";
        return "ALERT";
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
