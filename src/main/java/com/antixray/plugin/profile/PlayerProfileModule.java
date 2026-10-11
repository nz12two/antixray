package com.antixray.plugin.profile;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.model.MiningEvent;
import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProfileModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private PlayerDataManager dataManager;

    public PlayerProfileModule(AntiXrayPlugin plugin) {
        super("playerprofile");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataManager = plugin.getServiceRegistry().resolve(PlayerDataManager.class);
        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        eventBus.register(MiningEvent.class, this::onMiningEvent);

        plugin.getServiceRegistry().register(PlayerProfileModule.class, this);
        plugin.getServiceRegistry().register(PlayerProfileManager.class, new PlayerProfileManager(profiles));
    }

    @Override
    public void onDisable() {
        profiles.clear();
    }

    private void onMiningEvent(MiningEvent event) {
        PlayerProfile profile = getOrCreate(event.getPlayerId());
        updatePlaytime(profile);
        updateMiningStyle(profile, event);
        updateSkillEstimate(profile);
    }

    private PlayerProfile getOrCreate(UUID playerId) {
        return profiles.computeIfAbsent(playerId, PlayerProfile::new);
    }

    public PlayerProfile getProfile(UUID playerId) {
        return profiles.get(playerId);
    }

    public PlayerProfile getOrCreateProfile(UUID playerId) {
        return getOrCreate(playerId);
    }

    private void updatePlaytime(PlayerProfile profile) {
        long elapsed = System.currentTimeMillis() - profile.getLastUpdated();
        profile.setPlaytimeHours(profile.getPlaytimeHours() + elapsed / 3600_000.0);
    }

    private void updateMiningStyle(PlayerProfile profile, MiningEvent event) {
        double straightness = 0;
        double caveRatio = 0;

        PlayerData data = dataManager.get(profile.getPlayerId());
        if (data != null && data.getRecentEvents().size() > 20) {
            long cave = data.getRecentEvents().stream()
                    .filter(e -> e.getConditions().contains(MiningEvent.MiningCondition.CAVE))
                    .count();
            caveRatio = (double) cave / data.getRecentEvents().size();

            var path = data.getMiningPath();
            if (path.size() > 5) {
                int straight = 0;
                var lastDir = new org.bukkit.util.Vector(0, 0, 0);
                var last = path.get(0);
                int count = 0;
                for (int i = 1; i < path.size(); i++) {
                    var dir = path.get(i).toVector().subtract(last.toVector());
                    if (dir.length() < 0.3) continue;
                    dir.normalize();
                    if (count > 0 && lastDir.dot(dir) > 0.98) straight++;
                    lastDir = dir;
                    last = path.get(i);
                    count++;
                }
                straightness = count > 0 ? (double) straight / count : 0;
            }
        }

        if (caveRatio > 0.5) {
            profile.setMiningStyle(PlayerProfile.MiningStyle.CAVE);
        } else if (straightness > 0.4) {
            profile.setMiningStyle(PlayerProfile.MiningStyle.BRANCH);
        } else if (caveRatio > 0.2) {
            profile.setMiningStyle(PlayerProfile.MiningStyle.HYBRID);
        } else {
            profile.setMiningStyle(PlayerProfile.MiningStyle.EXPLORER);
        }
    }

    private void updateSkillEstimate(PlayerProfile profile) {
        double playtimeFactor = Math.min(1.0, profile.getPlaytimeHours() / 50.0);
        double trustDecay = Math.max(0, profile.getTrustScore() - 0.01);
        profile.setTrustScore(Math.min(1.0, trustDecay + 0.001 * Math.min(profile.getPlaytimeHours(), 100)));
        profile.setSkillLevelEstimate(0.3 + playtimeFactor * 0.5 + profile.getTrustScore() * 0.2);
    }

    public void adjustForFlags(UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        if (profile != null) {
            profile.setTrustScore(Math.max(0.1, profile.getTrustScore() - 0.15));
            profile.setSkillLevelEstimate(Math.max(0.1, profile.getSkillLevelEstimate() - 0.05));
        }
    }

    public static class PlayerProfileManager {
        private final Map<UUID, PlayerProfile> profiles;
        public PlayerProfileManager(Map<UUID, PlayerProfile> profiles) { this.profiles = profiles; }
        public PlayerProfile get(UUID id) { return profiles.get(id); }
    }
}
