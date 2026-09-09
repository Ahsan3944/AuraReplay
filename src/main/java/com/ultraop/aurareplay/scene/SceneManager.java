package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
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

    public SceneManager(ActorManager actorManager, ActorPlaybackController playbackController) {
        this.actorManager = actorManager;
        this.playbackController = playbackController;
    }

    public Scene create(String name) {
        Scene scene = new Scene(name);
        scenes.put(normalize(name), scene);
        return scene;
    }

    public Optional<Scene> get(String name) { return Optional.ofNullable(scenes.get(normalize(name))); }

    public boolean delete(String name) {
        String key = normalize(name);
        Scene scene = scenes.remove(key);
        if (scene == null) return false;
        activeSessions.entrySet().removeIf(entry -> entry.getValue().scene() == scene);
        return true;
    }

    public Collection<Scene> all() { return scenes.values().stream().toList(); }

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

    public int play(Player viewer, String sceneName) {
        Scene scene = get(sceneName).orElseThrow(() -> new IllegalArgumentException("scene not found: " + sceneName));
        stop(viewer);
        if (scene.timeline().durationTicks() == 0L) {
            long duration = scene.actorIds().stream()
                    .map(actorManager::get)
                    .flatMap(Optional::stream)
                    .mapToLong(actor -> actor.recording().durationTicks())
                    .max().orElse(0L);
            scene.timeline().setDurationTicks(duration);
        }

        int started = 0;
        for (ActorId actorId : scene.actorIds()) {
            Optional<ActorDefinition> actor = actorManager.get(actorId);
            if (actor.isEmpty()) continue;
            playbackController.start(viewer, actor.get());
            started++;
        }
        ScenePlaybackSession session = new ScenePlaybackSession(scene);
        activeSessions.put(viewer.getUniqueId(), session);
        playbackController.tickScene(viewer, scene.actorIds(), session.cursor().tick());
        return started;
    }

    public void tick(Player viewer) {
        ScenePlaybackSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) return;
        boolean active = session.tick();
        playbackController.tickScene(viewer, session.scene().actorIds(), session.cursor().tick());
        if (!active) stop(viewer);
    }

    public boolean stop(Player viewer) {
        ScenePlaybackSession session = activeSessions.remove(viewer.getUniqueId());
        if (session == null) return false;
        for (ActorId actorId : session.scene().actorIds()) playbackController.stop(viewer, actorId);
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
    private static String normalize(String name) { return name.trim().toLowerCase(java.util.Locale.ROOT); }
}
