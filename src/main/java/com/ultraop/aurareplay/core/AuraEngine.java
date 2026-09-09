package com.ultraop.aurareplay.core;

import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.nms.NmsCameraBackend;
import com.ultraop.aurareplay.nms.NmsVirtualActorBackend;
import com.ultraop.aurareplay.recording.RecordingManager;
import com.ultraop.aurareplay.recording.TickRecorder;
import com.ultraop.aurareplay.scene.SceneManager;
import com.ultraop.aurareplay.scene.SceneTimelineService;
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
    private final SceneTimelineService sceneTimelineService;
    private final CameraController cameraController;
    private BukkitTask playbackTask;

    public AuraEngine(JavaPlugin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.tickRecorder = new TickRecorder(plugin);
        this.recordingManager = new RecordingManager();
        this.actorManager = new ActorManager();
        this.actorPlaybackController = new ActorPlaybackController(new NmsVirtualActorBackend());
        this.sceneManager = new SceneManager(actorManager, actorPlaybackController);
        this.sceneTimelineService = new SceneTimelineService();
        this.cameraController = new CameraController(new NmsCameraBackend());
    }

    public void start() {
        if (playbackTask != null) return;
        playbackTask = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                Bukkit.getOnlinePlayers().forEach(viewer -> {
                    sceneManager.tick(viewer);
                    actorPlaybackController.tickStandalone(viewer, sceneManager.activeActorIds(viewer));
                    cameraController.tick(viewer);
                }), 1L, 1L);
    }

    public void shutdown() {
        if (playbackTask != null) {
            playbackTask.cancel();
            playbackTask = null;
        }
        Bukkit.getOnlinePlayers().forEach(viewer -> {
            actorPlaybackController.stopAll(viewer);
            cameraController.stop(viewer);
        });
        actorPlaybackController.clear();
        cameraController.clear();
        sceneTimelineService.clear();
        tickRecorder.shutdown();
    }

    public JavaPlugin plugin() { return plugin; }
    public ProtocolManager protocolManager() { return protocolManager; }
    public TickRecorder tickRecorder() { return tickRecorder; }
    public RecordingManager recordingManager() { return recordingManager; }
    public ActorManager actorManager() { return actorManager; }
    public ActorPlaybackController actorPlaybackController() { return actorPlaybackController; }
    public SceneManager sceneManager() { return sceneManager; }
    public SceneTimelineService sceneTimelineService() { return sceneTimelineService; }
    public CameraController cameraController() { return cameraController; }
}
