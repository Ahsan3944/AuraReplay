package com.ultraop.aurareplay.core;

import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.recording.TickRecorder;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuraEngine {

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final TickRecorder tickRecorder;

    public AuraEngine(JavaPlugin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.tickRecorder = new TickRecorder(plugin);
    }

    public void start() {
        tickRecorder.start();
    }

    public void shutdown() {
        tickRecorder.shutdown();
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public ProtocolManager protocolManager() {
        return protocolManager;
    }

    public TickRecorder tickRecorder() {
        return tickRecorder;
    }
}
