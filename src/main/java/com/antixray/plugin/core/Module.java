package com.antixray.plugin.core;

public abstract class Module {
    protected final String name;
    protected boolean enabled;

    public Module(String name) {
        this.name = name;
        this.enabled = false;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public abstract void onEnable();
    public abstract void onDisable();
    public void onReload() {}
}
