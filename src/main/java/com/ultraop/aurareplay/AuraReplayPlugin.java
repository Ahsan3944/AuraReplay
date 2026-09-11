package com.ultraop.aurareplay;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.command.PersistentAuraReplayCommand;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.director.DirectorExportRecovery;
import com.ultraop.aurareplay.director.DirectorStudioListener;
import com.ultraop.aurareplay.listener.ActorActionCaptureListener;
import com.ultraop.aurareplay.listener.DirectorExportRecoveryListener;
import com.ultraop.aurareplay.listener.PlaybackLifecycleListener;
import com.ultraop.aurareplay.storage.*;
import com.ultraop.aurareplay.ui.ActorSourceBrowser;
import com.ultraop.aurareplay.ui.ActorSourceBrowserListener;
import com.ultraop.aurareplay.ui.SceneStudioListener;
import com.ultraop.aurareplay.ui.StudioCommand;
import com.ultraop.aurareplay.ui.StudioListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

public final class AuraReplayPlugin extends JavaPlugin {
    private AuraEngine engine; private ProjectStorage projectStorage; private RecordingStorageManager recordingStorage; private ActorStorage actorStorage; private ActorAppearanceStorage appearanceStorage; private PersistenceCoordinator persistenceCoordinator; private ExecutorService storageExecutor;
    private CompletableFuture<Void> pendingPersistence=CompletableFuture.completedFuture(null); private BukkitTask persistenceTask;
    @Override public void onEnable(){
        ProtocolManager protocolManager=ProtocolLibrary.getProtocolManager();engine=new AuraEngine(this,protocolManager);storageExecutor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AuraReplay-Storage");t.setDaemon(true);return t;});
        projectStorage=new ProjectStorage(this,storageExecutor);recordingStorage=new RecordingStorageManager(this,storageExecutor);actorStorage=new ActorStorage(this,storageExecutor);persistenceCoordinator=new PersistenceCoordinator(this,storageExecutor);persistenceCoordinator.recoverIfNeeded();appearanceStorage=new ActorAppearanceStorage(this,storageExecutor);
        engine.recordingManager().attachStorage(recordingStorage);engine.actorPlaybackController().setSourceFactory(null);engine.actorPlaybackController().setAsyncSourceFactory(actor->engine.recordingManager().createPlaybackSourceAsync(actor.recording().name()),storageExecutor);Executor mainThreadExecutor=task->Bukkit.getScheduler().runTask(this,task);engine.actorPlaybackController().setMainThreadExecutor(mainThreadExecutor);
        engine.recordingManager().setDeletionListener(name->mainThreadExecutor.execute(()->{for(var actor:engine.actorManager().usingRecording(name))engine.sceneManager().removeActorEverywhere(actor.id());engine.actorManager().removeUsingRecording(name);}));
        engine.recordingManager().loadAllAsync().join();actorStorage.loadInto(engine.actorManager(),engine.recordingManager());appearanceStorage.loadInto(engine.actorManager());projectStorage.loadInto(engine.sceneManager(),engine.cameraManager());engine.sceneManager().reconcileActorReferences();
        recordingStorage.validateStoredRecordingsAsync().thenAccept(results->{long invalid=results.stream().filter(result->!result.valid()).count();if(invalid>0)getLogger().warning("Recording integrity scan quarantined "+invalid+" invalid recording(s). Check plugins/AuraReplay/recordings/corrupt/. ");}).exceptionally(error->{getLogger().warning("Recording integrity scan failed: "+error.getMessage());return null;});
        Path exportDirectory=getDataFolder().toPath().resolve("exports");try{List<DirectorExportRecovery> recoveries=engine.directorExportRecoveryManager().discover(exportDirectory);if(!recoveries.isEmpty())getLogger().warning("Found "+recoveries.size()+" interrupted Director export(s). Use /aurareplay director export recover to resume.");}catch(IOException ex){getLogger().warning("Director export recovery scan failed at startup: "+ex.getMessage());}
        engine.start();persistenceTask=Bukkit.getScheduler().runTaskTimer(this,this::persistProjectAsync,100L,100L);PersistentAuraReplayCommand command=new PersistentAuraReplayCommand(engine);if(getCommand("aurareplay")!=null){getCommand("aurareplay").setExecutor(command);getCommand("aurareplay").setTabCompleter(command);}if(getCommand("arstudio")!=null)getCommand("arstudio").setExecutor(new StudioCommand(engine));ActorSourceBrowser sourceBrowser=new ActorSourceBrowser(engine);getServer().getPluginManager().registerEvents(new StudioListener(engine.studioController(),sourceBrowser),this);getServer().getPluginManager().registerEvents(new SceneStudioListener(engine),this);getServer().getPluginManager().registerEvents(new DirectorStudioListener(engine.directorStudioService(),engine.sceneManager(),engine.directorController()),this);getServer().getPluginManager().registerEvents(new ActorSourceBrowserListener(sourceBrowser,engine),this);getServer().getPluginManager().registerEvents(new PlaybackLifecycleListener(engine),this);getServer().getPluginManager().registerEvents(new DirectorExportRecoveryListener(this,engine),this);getServer().getPluginManager().registerEvents(new ActorActionCaptureListener(engine.tickRecorder()),this);getLogger().info("AuraReplay foundation enabled with persistent project, recording, actor, and appearance storage.");
    }
    private void persistProjectAsync(){if(persistenceCoordinator==null||projectStorage==null||actorStorage==null||appearanceStorage==null||engine==null)return;ProjectStorage.ProjectSnapshot p=projectStorage.capture(engine.sceneManager(),engine.cameraManager());List<ActorStorage.ActorSnapshot>a=actorStorage.captureAll(engine.actorManager());List<ActorAppearanceStorage.Snapshot>appearance=appearanceStorage.captureAll(engine.actorManager());pendingPersistence=pendingPersistence.handle((ignored,error)->null).thenCompose(ignored->persistenceCoordinator.saveAsync(p,a)).thenCompose(ignored->appearanceStorage.saveAsync(appearance));}
    @Override public void onDisable(){if(persistenceTask!=null){persistenceTask.cancel();persistenceTask=null;}if(persistenceCoordinator!=null&&projectStorage!=null&&actorStorage!=null&&appearanceStorage!=null&&engine!=null){try{pendingPersistence.join();ProjectStorage.ProjectSnapshot p=projectStorage.capture(engine.sceneManager(),engine.cameraManager());List<ActorStorage.ActorSnapshot>a=actorStorage.captureAll(engine.actorManager());persistenceCoordinator.save(p,a);appearanceStorage.save(appearanceStorage.captureAll(engine.actorManager()));}finally{persistenceCoordinator.close();projectStorage.close();actorStorage.close();appearanceStorage.close();}}if(recordingStorage!=null)recordingStorage.close();if(engine!=null)engine.shutdown();if(storageExecutor!=null){storageExecutor.shutdown();storageExecutor=null;}}
    public AuraEngine engine(){return engine;}public ProjectStorage projectStorage(){return projectStorage;}public RecordingStorageManager recordingStorage(){return recordingStorage;}public ActorStorage actorStorage(){return actorStorage;}public PersistenceCoordinator persistenceCoordinator(){return persistenceCoordinator;}
}
