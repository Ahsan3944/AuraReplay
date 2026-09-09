package com.ultraop.aurareplay.actor;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread playback coordinator. Each viewer receives an independent cursor/session. */
public final class ActorPlaybackController {
    private final VirtualActorBackend backend;
    private final Map<UUID, Map<ActorId, ActorPlayback>> sessions = new HashMap<>();

    public ActorPlaybackController(VirtualActorBackend backend) {
        this.backend = backend;
    }

    public void start(Player viewer, ActorDefinition actor) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>());
        ActorPlayback playback = new ActorPlayback(actor);
        viewerSessions.put(actor.id(), playback);
        if (actor.visible()) {
            backend.spawn(actor, viewer);
            backend.updateIdentity(actor, viewer);
        }
        render(playback, viewer);
    }

    /** Renders all actors in a scene at one shared scene-time position. */
    public void tickScene(Player viewer, Iterable<ActorId> actorIds, double sceneTick) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.get(viewer.getUniqueId());
        if (viewerSessions == null) return;
        for (ActorId actorId : actorIds) {
            ActorPlayback playback = viewerSessions.get(actorId);
            if (playback == null) continue;
            ActorDefinition actor = playback.actor();
            if (!actor.visible()) {
                backend.destroy(actor, viewer);
                continue;
            }
            if (!actor.frozen()) playback.setScenePosition(sceneTick);
            render(playback, viewer);
        }
    }

    public void stop(Player viewer, ActorId actorId) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.get(viewer.getUniqueId());
        if (viewerSessions == null) return;
        ActorPlayback playback = viewerSessions.remove(actorId);
        if (playback != null) backend.destroy(playback.actor(), viewer);
        if (viewerSessions.isEmpty()) sessions.remove(viewer.getUniqueId());
    }

    public void stopAll(Player viewer) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.remove(viewer.getUniqueId());
        if (viewerSessions == null) return;
        for (ActorPlayback playback : viewerSessions.values()) backend.destroy(playback.actor(), viewer);
    }

    public void tick(Player viewer) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.get(viewer.getUniqueId());
        if (viewerSessions == null) return;
        for (ActorPlayback playback : viewerSessions.values()) {
            ActorDefinition actor = playback.actor();
            if (!actor.visible()) {
                backend.destroy(actor, viewer);
                continue;
            }
            playback.tick();
            render(playback, viewer);
        }
    }

    public void clear() { sessions.clear(); }
    public int sessionCount() { return sessions.values().stream().mapToInt(Map::size).sum(); }

    private void render(ActorPlayback playback, Player viewer) {
        ActorSample sample = playback.sampleState();
        if (sample == null || !sample.exists()) return;
        backend.update(playback.actor(), viewer, sample);
    }
}
