package com.ultraop.aurareplay;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.command.AuraReplayCommand;
import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuraReplayPlugin extends JavaPlugin {

    private AuraEngine engine;

    @Override
    public void onEnable() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        this.engine = new AuraEngine(this, protocolManager);
        this.engine.start();

        AuraReplayCommand command = new AuraReplayCommand(engine);
        getCommand("aurareplay").setExecutor(command);
        getCommand("aurareplay").setTabCompleter(command);

        getLogger().info("AuraReplay foundation enabled.");
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    public AuraEngine engine() {
        return engine;
    }
}
