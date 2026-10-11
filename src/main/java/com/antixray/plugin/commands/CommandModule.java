package com.antixray.plugin.commands;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.commands.commands.*;
import com.antixray.plugin.commands.commands.MLCommandHandler;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

public class CommandModule extends Module {
    private final AntiXrayPlugin plugin;
    private MLCommandHandler mlHandler;

    public CommandModule(AntiXrayPlugin plugin) {
        super("commands");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        registerCommand("antixray", new AntiXrayBaseCommand(plugin));
        this.mlHandler = new MLCommandHandler(plugin);
    }

    @Override
    public void onDisable() {}

    public MLCommandHandler getMLHandler() { return mlHandler; }

    private void registerCommand(String name, CommandExecutor executor) {
        try {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(executor);
                if (executor instanceof TabCompleter) {
                    command.setTabCompleter((TabCompleter) executor);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register command /" + name + ": " + e.getMessage());
        }
    }
}
