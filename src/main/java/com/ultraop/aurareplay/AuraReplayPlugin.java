package com.ultraop.aurareplay;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.command.AuraReplayCommand;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.ui.StudioListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuraReplayPlugin extends JavaPlugin {
    private AuraEngine engine;
    @Override public void onEnable() {
        ProtocolManager protocolManager=ProtocolLibrary.getProtocolManager();
        engine=new AuraEngine(this,protocolManager); engine.start();
        AuraReplayCommand command=new AuraReplayCommand(engine);
        if(getCommand("aurareplay")!=null){getCommand("aurareplay").setExecutor(command);getCommand("aurareplay").setTabCompleter(command);}
        getServer().getPluginManager().registerEvents(new StudioListener(engine.studioController()),this);
        getLogger().info("AuraReplay foundation enabled.");
    }
    @Override public void onDisable(){if(engine!=null)engine.shutdown();}
    public AuraEngine engine(){return engine;}
}
