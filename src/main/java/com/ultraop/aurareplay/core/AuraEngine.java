package com.ultraop.aurareplay.core;

import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.camera.CameraManager;
import com.ultraop.aurareplay.camera.CameraStudioService;
import com.ultraop.aurareplay.director.DirectorController;
import com.ultraop.aurareplay.director.DirectorStudioService;
import com.ultraop.aurareplay.nms.NmsCameraBackend;
import com.ultraop.aurareplay.nms.NmsVirtualActorBackend;
import com.ultraop.aurareplay.recording.RecordingManager;
import com.ultraop.aurareplay.recording.TickRecorder;
import com.ultraop.aurareplay.scene.SceneCameraService;
import com.ultraop.aurareplay.scene.SceneManager;
import com.ultraop.aurareplay.scene.SceneTimelineService;
import com.ultraop.aurareplay.ui.SceneStudioService;
import com.ultraop.aurareplay.ui.StudioController;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AuraEngine {
    private final JavaPlugin plugin; private final ProtocolManager protocolManager; private final TickRecorder tickRecorder; private final RecordingManager recordingManager; private final ActorManager actorManager; private final ActorPlaybackController actorPlaybackController; private final SceneManager sceneManager; private final SceneTimelineService sceneTimelineService; private final CameraManager cameraManager; private final CameraController cameraController; private final DirectorController directorController; private final DirectorStudioService directorStudioService; private final CameraStudioService cameraStudioService; private final SceneCameraService sceneCameraService; private final SceneStudioService sceneStudioService; private final StudioController studioController; private BukkitTask playbackTask;
    public AuraEngine(JavaPlugin plugin, ProtocolManager protocolManager) { this.plugin=plugin; this.protocolManager=protocolManager; this.tickRecorder=new TickRecorder(plugin); this.recordingManager=new RecordingManager(); this.actorManager=new ActorManager(); this.actorPlaybackController=new ActorPlaybackController(new NmsVirtualActorBackend()); this.cameraManager=new CameraManager(); this.cameraController=new CameraController(new NmsCameraBackend()); this.directorController=new DirectorController(cameraManager,cameraController,actorPlaybackController); this.sceneCameraService=new SceneCameraService(cameraManager); this.sceneManager=new SceneManager(actorManager,actorPlaybackController,sceneCameraService,cameraController,directorController); this.sceneTimelineService=new SceneTimelineService(); this.sceneStudioService=new SceneStudioService(sceneManager); this.directorStudioService=new DirectorStudioService(sceneManager,cameraManager,actorManager,directorController); this.cameraStudioService=new CameraStudioService(cameraManager,actorManager); this.studioController=new StudioController(this); }
    public void start(){if(playbackTask!=null)return;playbackTask=Bukkit.getScheduler().runTaskTimer(plugin,()->Bukkit.getOnlinePlayers().forEach(viewer->{sceneManager.tick(viewer);actorPlaybackController.tickStandalone(viewer,sceneManager.activeActorIds(viewer));if(directorController.active(viewer))directorController.tick(viewer);else cameraController.tick(viewer);}),1L,1L);}
    public void shutdown(){if(playbackTask!=null){playbackTask.cancel();playbackTask=null;}Bukkit.getOnlinePlayers().forEach(viewer->{actorPlaybackController.stopAll(viewer);directorController.stop(viewer);studioController.close(viewer);directorStudioService.close(viewer);});actorPlaybackController.clear();cameraController.clear();cameraManager.clear();sceneManager.clear();directorController.clear();sceneTimelineService.clear();tickRecorder.shutdown();}
    public JavaPlugin plugin(){return plugin;} public ProtocolManager protocolManager(){return protocolManager;} public TickRecorder tickRecorder(){return tickRecorder;} public RecordingManager recordingManager(){return recordingManager;} public ActorManager actorManager(){return actorManager;} public ActorPlaybackController actorPlaybackController(){return actorPlaybackController;} public SceneManager sceneManager(){return sceneManager;} public SceneTimelineService sceneTimelineService(){return sceneTimelineService;} public CameraManager cameraManager(){return cameraManager;} public CameraController cameraController(){return cameraController;} public DirectorController directorController(){return directorController;} public DirectorStudioService directorStudioService(){return directorStudioService;} public CameraStudioService cameraStudioService(){return cameraStudioService;} public SceneCameraService sceneCameraService(){return sceneCameraService;} public SceneStudioService sceneStudioService(){return sceneStudioService;} public StudioController studioController(){return studioController;}
}
