package com.antixray.plugin.api;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;

public class ApiModule extends Module {
    private final AntiXrayPlugin plugin;

    public ApiModule(AntiXrayPlugin plugin) {
        super("api");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        AntiXrayAPI api = new AntiXrayAPI(plugin);
        plugin.getServiceRegistry().register(AntiXrayAPI.class, api);
    }

    @Override
    public void onDisable() {}
}
