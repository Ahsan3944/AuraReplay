package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread scene registry and viewer-local scene playback coordinator. */
public final class SceneManager {
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeScenes = new ConcurrentHashMap<>();
    private final ActorManager actorManager;
    private final ActorPlaybackController playbackController;

    public SceneManager(ActorManager actorManager, ActorPlaybackController playbackController) {
        this.actorManager = actorManager;
        this.playbackController = playbackController;
    }

    public Scene create(String name) {
        Scene scene = new Scene(name);
        scenes.put(normalize(name), scene);
        return scene;
    }

    public Optional<Scene> get(String name) {
        return Optional.ofNullable(scenes.get(normalize(name)));
    }

    public boolean delete(String name) {
        String key = normalize(name);
        Scene scene = scenes.remove(key);
        if (scene == null) return false;
        activeScenes.entrySet().removeIf(entry -> entry.getValue().equals(key));
        return true;
    }

    public Collection<Scene> all() { return scenes.values().stream().toList(); }

    public boolean addActor(String sceneName, ActorId actorId) {
        Scene scene = scenes.get(normalize(sceneName));
        return scene != null && scene.addActor(actorId);
    }

    public boolean removeActor(String sceneName, ActorId actorId) {
        Scene scene = scenes.get(normalize(sceneName));
        return scene != null && scene.removeActor(actorId);
    }

    /** Starts every valid actor in the scene for one viewer. */
    public int play(Player viewer, String sceneName) {
        Scene scene = get(sceneName).orElseThrow(() -> new IllegalArgumentException("scene not found: " + sceneName));
        stop(viewer);
        int started = 0;
        for (ActorId actorId : scene.actorIds()) {
            Optional<ActorDefinition> actor = actorManager.get(actorId);
            if (actor.isEmpty()) continue;
            playbackController.start(viewer, actor.get());
            started++;
        }
        activeScenes.put(viewer.getUniqueId(), normalize(sceneName));
        return started;
    }

    public boolean stop(Player viewer) {
        String sceneName = activeScenes.remove(viewer.getUniqueId());
        if (sceneName == null) return false;
        Scene scene = scenes.get(sceneName);
        if (scene != null) {
            for (ActorId actorId : scene.actorIds()) playbackController.stop(viewer, actorId);
        }
        return true;
    }

    public Optional<String> activeScene(Player viewer) {
        return Optional.ofNullable(activeScenes.get(viewer.getUniqueId()));
    }

    public void stopAll(Player viewer) { stop(viewer); }

    private static String normalize(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
