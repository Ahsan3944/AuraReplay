package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.camera.CameraController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread scene registry and viewer-local scene playback coordinator. */
public final class SceneManager {
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<UUID, ScenePlaybackSession> activeSessions = new ConcurrentHashMap<>();
    private final ActorManager actorManager;
    private final ActorPlaybackController playbackController;
    private final SceneCameraService sceneCameras;
    private final CameraController cameraController;

    public SceneManager(ActorManager actorManager, ActorPlaybackController playbackController,
                        SceneCameraService sceneCameras, CameraController cameraController) {
        this.actorManager = actorManager;
        this.playbackController = playbackController;
        this.sceneCameras = sceneCameras;
        this.cameraController = cameraController;
    }

    public Scene create(String name) {
        String key = normalize(name);
        if (scenes.containsKey(key)) throw new IllegalArgumentException("scene already exists: " + name);
        Scene scene = new Scene(name);
        scenes.put(key, scene);
        return scene;
    }

    public Optional<Scene> get(String name) { return Optional.ofNullable(scenes.get(normalize(name))); }
    public boolean exists(String name) { return scenes.containsKey(normalize(name)); }

    public boolean delete(String name) {
        String key = normalize(name);
        Scene scene = scenes.get(key);
        if (scene == null) return false;
        activeSessions.entrySet().stream().filter(entry -> entry.getValue().scene() == scene).map(Map.Entry::getKey).toList().forEach(uuid -> {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) stop(viewer); else activeSessions.remove(uuid);
        });
        scenes.remove(key, scene);
        sceneCameras.unbind(scene);
        return true;
    }

    public Collection<Scene> all() { return scenes.values().stream().toList(); }

    public boolean bindCamera(String sceneName, String cameraId) {
        Scene scene = scenes.get(normalize(sceneName));
        return scene != null && sceneCameras.bind(scene, cameraId);
    }

    public boolean unbindCamera(String sceneName) {
        Scene scene = scenes.get(normalize(sceneName));
        return scene != null && sceneCameras.unbind(scene);
    }

    public Optional<String> cameraId(String sceneName) {
        Scene scene = scenes.get(normalize(sceneName));
        return scene == null ? Optional.empty() : sceneCameras.cameraId(scene);
    }

    public boolean addActor(String sceneName, ActorId actorId) {
        Scene scene = scenes.get(normalize(sceneName));
        if (scene == null) return false;
        Optional<ActorDefinition> actor = actorManager.get(actorId);
        if (actor.isEmpty()) return false;
        boolean added = scene.addActor(actorId);
        if (added) {
            long actorDuration = actor.get().recording().durationTicks();
            if (actorDuration > scene.timeline().durationTicks()) scene.timeline().setDurationTicks(actorDuration);
        }
        return added;
    }

    public boolean removeActor(String sceneName, ActorId actorId) {
        Scene scene = scenes.get(normalize(sceneName));
        return scene != null && scene.removeActor(actorId);
    }

    /** Removes an actor reference from every scene and stops any viewer playback using it. Main-thread only. */
    public int removeActorEverywhere(ActorId actorId) {
        if (actorId == null) return 0;
        playbackController.stopUsingActor(actorId);
        int removed = 0;
        for (Scene scene : scenes.values()) {
            if (scene.removeActor(actorId)) removed++;
        }
        return removed;
    }

    public int play(Player viewer, String sceneName) {
        Scene scene = get(sceneName).orElseThrow(() -> new IllegalArgumentException("scene not found: " + sceneName));
        stop(viewer);
        ensureDuration(scene);
        int started = startActors(viewer, scene);
        ScenePlaybackSession session = new ScenePlaybackSession(scene);
        activeSessions.put(viewer.getUniqueId(), session);
        renderAt(viewer, session);
        sceneCameras.camera(scene).ifPresent(camera -> cameraController.start(viewer, camera));
        return started;
    }

    private int startActors(Player viewer, Scene scene) {
        int started = 0;
        for (ActorId actorId : scene.actorIds()) {
            Optional<ActorDefinition> actor = actorManager.get(actorId);
            if (actor.isEmpty()) continue;
            playbackController.start(viewer, actor.get());
            started++;
        }
        return started;
    }

    private void ensureDuration(Scene scene) {
        if (scene.timeline().durationTicks() != 0L) return;
        long duration = scene.actorIds().stream().map(actorManager::get).flatMap(Optional::stream).mapToLong(actor -> actor.recording().durationTicks()).max().orElse(0L);
        scene.timeline().setDurationTicks(duration);
    }

    public void tick(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) return;
        boolean active = session.tick();
        renderAt(viewer, session);
        if (!active && session.finished()) stop(viewer);
    }

    private void renderAt(Player viewer, ScenePlaybackSession session) {
        playbackController.tickScene(viewer, session.scene().actorIds(), session.cursor().tick());
        if (cameraController.active(viewer)) cameraController.seek(viewer, session.cursor().tick());
    }

    public boolean pause(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) return false;
        session.setPaused(true);
        return true;
    }

    public boolean resume(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) return false;
        session.setPaused(false);
        return true;
    }

    public boolean paused(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        return session != null && session.paused();
    }

    public Optional<Double> currentTick(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        return session == null ? Optional.empty() : Optional.of(session.cursor().tick());
    }

    public boolean seek(Player viewer, double tick) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) return false;
        session.seek(tick);
        renderAt(viewer, session);
        return true;
    }

    public boolean step(Player viewer, double deltaTicks) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) return false;
        session.seek(session.cursor().tick() + deltaTicks);
        renderAt(viewer, session);
        return true;
    }

    public boolean stop(Player viewer) {
        ScenePlaybackSession session = activeSessions.remove(viewer.getUniqueId());
        if (session == null) return false;
        for (ActorId actorId : session.scene().actorIds()) playbackController.stop(viewer, actorId);
        if (sceneCameras.camera(session.scene()).isPresent()) cameraController.stop(viewer);
        return true;
    }

    public Optional<String> activeScene(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        return session == null ? Optional.empty() : Optional.of(session.scene().name());
    }

    public Set<ActorId> activeActorIds(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        return session == null ? Set.of() : Set.copyOf(session.scene().actorIds());
    }

    public void stopAll(Player viewer) { stop(viewer); }

    public void clear() {
        for (Player viewer : Bukkit.getOnlinePlayers()) stop(viewer);
        activeSessions.clear();
        scenes.clear();
        sceneCameras.clear();
    }

    private static String normalize(String name) { return name.trim().toLowerCase(java.util.Locale.ROOT); }
}
