package com.antixray.plugin.core;

import com.antixray.plugin.AntiXrayPlugin;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleManager {
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public void register(Module module) {
        modules.put(module.getName(), module);
    }

    public void enableAll() {
        for (Module module : modules.values()) {
            try {
                module.onEnable();
                module.setEnabled(true);
            } catch (Exception e) {
                AntiXrayPlugin.getInstance().getLogger().warning("[AntiXray] Failed to enable module: " + module.getName());
                e.printStackTrace();
            }
        }
    }

    public void disableAll() {
        for (Module module : modules.values()) {
            try {
                module.onDisable();
                module.setEnabled(false);
            } catch (Exception e) {
                AntiXrayPlugin.getInstance().getLogger().warning("[AntiXray] Failed to disable module: " + module.getName());
                e.printStackTrace();
            }
        }
    }

    public void reloadAll() {
        for (Module module : modules.values()) {
            try {
                module.onReload();
            } catch (Exception e) {
                AntiXrayPlugin.getInstance().getLogger().warning("[AntiXray] Failed to reload module: " + module.getName());
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(String name) {
        return (T) modules.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> type) {
        for (Module module : modules.values()) {
            if (type.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }

    public int getModuleCount() { return modules.size(); }
}
