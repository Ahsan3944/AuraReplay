package com.ultraop.aurareplay.core;

import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.nms.NmsVirtualActorBackend;
import com.ultraop.aurareplay.recording.RecordingManager;
import com.ultraop.aurareplay.recording.TickRecorder;
import com.ultraop.aurareplay.scene.SceneManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AuraEngine {
    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final TickRecorder tickRecorder;
    private final RecordingManager recordingManager;
    private final ActorManager actorManager;
    private final ActorPlaybackController actorPlaybackController;
    private final SceneManager sceneManager;
    private BukkitTask playbackTask;

    public AuraEngine(JavaPlugin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.tickRecorder = new TickRecorder(plugin);
        this.recordingManager = new RecordingManager();
        this.actorManager = new ActorManager();
        this.actorPlaybackController = new ActorPlaybackController(new NmsVirtualActorBackend());
        this.sceneManager = new SceneManager(actorManager, actorPlaybackController);
    }

    public void start() {
        if (playbackTask != null) return;
        playbackTask = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                Bukkit.getOnlinePlayers().forEach(actorPlaybackController::tick), 1L, 1L);
    }

    public void shutdown() {
        if (playbackTask != null) {
            playbackTask.cancel();
            playbackTask = null;
        }
        Bukkit.getOnlinePlayers().forEach(actorPlaybackController::stopAll);
        actorPlaybackController.clear();
        tickRecorder.shutdown();
    }

    public JavaPlugin plugin() { return plugin; }
    public ProtocolManager protocolManager() { return protocolManager; }
    public TickRecorder tickRecorder() { return tickRecorder; }
    public RecordingManager recordingManager() { return recordingManager; }
    public ActorManager actorManager() { return actorManager; }
    public ActorPlaybackController actorPlaybackController() { return actorPlaybackController; }
    public SceneManager sceneManager() { return sceneManager; }
}
