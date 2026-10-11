package com.antixray.plugin;

import com.antixray.plugin.core.ModuleManager;
import com.antixray.plugin.core.ServiceRegistry;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.config.ConfigLoader;
import com.antixray.plugin.collection.CollectionModule;
import com.antixray.plugin.feature.FeatureModule;
import com.antixray.plugin.statistics.StatisticsModule;
import com.antixray.plugin.detection.DetectionModule;
import com.antixray.plugin.risk.RiskModule;
import com.antixray.plugin.learning.LearningModule;
import com.antixray.plugin.learning.LearningModule.ServerProfile;
import com.antixray.plugin.evidence.EvidenceModule;
import com.antixray.plugin.punishment.PunishmentModule;
import com.antixray.plugin.discord.DiscordModule;
import com.antixray.plugin.database.DatabaseModule;
import com.antixray.plugin.api.ApiModule;
import com.antixray.plugin.commands.CommandModule;
import com.antixray.plugin.explanation.ExplanationModule;
import com.antixray.plugin.replay.ReplayModule;
import com.antixray.plugin.profile.PlayerProfileModule;
import com.antixray.plugin.engine.EngineModule;
import com.antixray.plugin.drift.DriftModule;
import com.antixray.plugin.autovalidation.AutoValidationModule;
import com.antixray.plugin.learning.HistoricalLearningModule;
import com.antixray.plugin.obfuscation.ObfuscationModule;
import com.antixray.plugin.paranoia.DecoyManager;
import com.antixray.plugin.paranoia.DecoyDetector;
import com.antixray.plugin.siem.SIEMLogger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for AntiXray v3.
 * <p>
 * Manages the full plugin lifecycle: onEnable registers 22 modules in dependency order,
 * onDisable shuts them down gracefully, and reloadConfig handles hot-reload via /ax reload.
 * <p>
 * Module registration order ensures that infrastructure (Core, Database) is initialized
 * before dependent modules (Detection, Punishment, Discord).
 */
public class AntiXrayPlugin extends JavaPlugin {
    private static AntiXrayPlugin instance;
    private ServiceRegistry serviceRegistry;
    private ModuleManager moduleManager;
    private EventBus eventBus;
    private ConfigLoader configLoader;
    private long startTime;

    @Override
    public void onEnable() {
        startTime = System.currentTimeMillis();
        instance = this;

        serviceRegistry = new ServiceRegistry();
        serviceRegistry.register(AntiXrayPlugin.class, this);

        eventBus = new EventBus();
        serviceRegistry.register(EventBus.class, eventBus);

        moduleManager = new ModuleManager();
        serviceRegistry.register(ModuleManager.class, moduleManager);

        configLoader = new ConfigLoader(this);
        serviceRegistry.register(ConfigLoader.class, configLoader);

        registerModules();
        moduleManager.enableAll();

        // Register paranoia detector if paranoia is enabled
        if (getConfig().getBoolean("paranoia.enabled", true)) {
            DetectionModule dm = getDetectionModule();
            if (dm != null) {
                DecoyManager decoyManager = new DecoyManager(this);
                serviceRegistry.register(DecoyManager.class, decoyManager);
                DecoyDetector decoyDetector = new DecoyDetector(decoyManager);
                dm.registerExternal(decoyDetector);
                getLogger().info("[Paranoia] Decoy system registered with DecoyDetector");
            }
        }

        int obfActive = getConfig().getBoolean("obfuscation.enabled", true) ? 1 : 0;
        int mlActive = getConfig().getBoolean("ml.enabled", true) ? 1 : 0;
        getLogger().info("+============================================+");
        getLogger().info("| AntiXray v" + getPluginMeta().getVersion() + " enabled               |");
        getLogger().info("| Modules: " + moduleManager.getModuleCount() + "                          |");
        getLogger().info("| Obfuscation: " + (obfActive > 0 ? "ON" : "OFF") + " | ML: " + (mlActive > 0 ? "ON" : "OFF") + "                    |");
        getLogger().info("| Time: " + (System.currentTimeMillis() - startTime) + "ms                    |");
        getLogger().info("+============================================+");
    }

    private void registerModules() {
        moduleManager.register(new CollectionModule(this));
        moduleManager.register(new FeatureModule(this));
        moduleManager.register(new StatisticsModule(this));
        moduleManager.register(new LearningModule(this));
        moduleManager.register(new PlayerProfileModule(this));
        moduleManager.register(new EngineModule(this));
        moduleManager.register(new DetectionModule(this));
        moduleManager.register(new RiskModule(this));
        moduleManager.register(new EvidenceModule(this));
        moduleManager.register(new PunishmentModule(this));
        moduleManager.register(new DiscordModule(this));
        moduleManager.register(new DatabaseModule(this));
        moduleManager.register(new ApiModule(this));
        moduleManager.register(new ExplanationModule(this));
        moduleManager.register(new ReplayModule(this));
        moduleManager.register(new DriftModule(this));
        moduleManager.register(new HistoricalLearningModule(this));
        moduleManager.register(new AutoValidationModule(this));
        moduleManager.register(new ObfuscationModule(this));
        moduleManager.register(new SIEMLogger(this));
        moduleManager.register(new CommandModule(this));
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (serviceRegistry != null) {
            serviceRegistry.clear();
        }
        instance = null;
        getLogger().info("AntiXray disabled.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (configLoader != null) configLoader.reload();
        if (moduleManager != null) moduleManager.reloadAll();
    }

    public static AntiXrayPlugin getInstance() { return instance; }
    public ServiceRegistry getServiceRegistry() { return serviceRegistry; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public EventBus getEventBus() { return eventBus; }
    public ConfigLoader getConfigLoader() { return configLoader; }
    public long getStartTime() { return startTime; }
    public DetectionModule getDetectionModule() {
        return moduleManager.getModule(DetectionModule.class);
    }

    public static ServerProfile getServerProfile() {
        if (instance != null && instance.serviceRegistry != null) {
            return instance.serviceRegistry.resolveOrNull(ServerProfile.class);
        }
        return null;
    }
}
