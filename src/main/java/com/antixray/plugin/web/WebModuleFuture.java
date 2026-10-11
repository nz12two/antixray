package com.antixray.plugin.web;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;

/**
 * Web Dashboard Module - Architecture for future implementation.
 *
 * Design:
 * - Embedded HTTP server using Javalin or Netty
 * - WebSocket for real-time risk updates
 * - REST API for data access
 * - Static frontend (React/Svelte) served as embedded resources
 *
 * Routes planned:
 *   GET  /api/players              - List all tracked players with risk scores
 *   GET  /api/players/:uuid        - Player details, statistics, evidence
 *   GET  /api/players/:uuid/heatmap - Mining heatmap data (block coordinates)
 *   GET  /api/evidence             - List all evidence records
 *   GET  /api/evidence/:id         - Single evidence detail
 *   GET  /api/stats                - Server-wide statistics
 *   GET  /api/top                  - Top flagged players
 *   WS   /ws/updates               - Real-time risk score updates
 *
 * Frontend pages:
 *   /                    - Dashboard with key metrics and recent flags
 *   /players             - Player list with search, sort, filter
 *   /players/:uuid       - Player detail with timeline, heatmap, stats
 *   /evidence            - Evidence browser with export
 *   /settings            - Configuration management (future)
 *
 * Security:
 * - Token-based authentication
 * - Permission checks against Minecraft UUID
 * - Rate limiting
 * - HTTPS support
 */
public class WebModuleFuture extends Module {
    private final AntiXrayPlugin plugin;

    public WebModuleFuture(AntiXrayPlugin plugin) {
        super("web");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Web Dashboard module is reserved for future implementation.");
    }

    @Override
    public void onDisable() {}
}
