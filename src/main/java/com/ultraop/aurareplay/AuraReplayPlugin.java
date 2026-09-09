package com.ultraop.aurareplay;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.ultraop.aurareplay.command.PersistentAuraReplayCommand;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.storage.ProjectStorage;
import com.ultraop.aurareplay.storage.RecordingStorageManager;
import com.ultraop.aurareplay.ui.SceneStudioListener;
import com.ultraop.aurareplay.ui.StudioCommand;
import com.ultraop.aurareplay.ui.StudioListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuraReplayPlugin extends JavaPlugin {
    private AuraEngine engine;
    private ProjectStorage projectStorage;
    private RecordingStorageManager recordingStorage;
    private ExecutorService storageExecutor;
    private CompletableFuture<Void> pendingPersistence = CompletableFuture.completedFuture(null);
    private BukkitTask persistenceTask;

    @Override public void onEnable() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        engine = new AuraEngine(this, protocolManager);
        storageExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "AuraReplay-Storage");
            thread.setDaemon(true);
            return thread;
        });
        projectStorage = new ProjectStorage(this, storageExecutor);
        recordingStorage = new RecordingStorageManager(this, storageExecutor);
        engine.recordingManager().attachStorage(recordingStorage);
        projectStorage.loadInto(engine.sceneManager(), engine.cameraManager());
        engine.recordingManager().loadAllAsync().join();
        engine.start();
        persistenceTask = Bukkit.getScheduler().runTaskTimer(this, this::persistProjectAsync, 100L, 100L);

        PersistentAuraReplayCommand command = new PersistentAuraReplayCommand(engine);
        if (getCommand("aurareplay") != null) { getCommand("aurareplay").setExecutor(command); getCommand("aurareplay").setTabCompleter(command); }
        if (getCommand("arstudio") != null) getCommand("arstudio").setExecutor(new StudioCommand(engine.studioController()));
        getServer().getPluginManager().registerEvents(new StudioListener(engine.studioController()), this);
        getServer().getPluginManager().registerEvents(new SceneStudioListener(engine), this);
        getLogger().info("AuraReplay foundation enabled with persistent project and recording storage.");
    }

    private void persistProjectAsync() {
        if (projectStorage == null || engine == null) return;
        ProjectStorage.ProjectSnapshot snapshot = projectStorage.capture(engine.sceneManager(), engine.cameraManager());
        pendingPersistence = pendingPersistence.handle((ignored, error) -> null).thenCompose(ignored -> projectStorage.saveAsync(snapshot));
    }

    @Override public void onDisable() {
        if (persistenceTask != null) { persistenceTask.cancel(); persistenceTask = null; }
        if (projectStorage != null && engine != null) {
            try {
                pendingPersistence.join();
                projectStorage.save(projectStorage.capture(engine.sceneManager(), engine.cameraManager()));
            } finally { projectStorage.close(); }
        }
        if (recordingStorage != null) recordingStorage.close();
        if (engine != null) engine.shutdown();
        if (storageExecutor != null) { storageExecutor.shutdown(); storageExecutor = null; }
    }

    public AuraEngine engine() { return engine; }
    public ProjectStorage projectStorage() { return projectStorage; }
    public RecordingStorageManager recordingStorage() { return recordingStorage; }
}
