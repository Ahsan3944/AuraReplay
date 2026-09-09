package com.ultraop.aurareplay.actor;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Main-thread playback coordinator. Each viewer receives an independent cursor/session. */
public final class ActorPlaybackController {
    private final VirtualActorBackend backend;
    private final Map<UUID, Map<ActorId, ActorPlayback>> sessions = new HashMap<>();
    private Function<ActorDefinition, ActorPlaybackSource> sourceFactory = actor -> null;

    public ActorPlaybackController(VirtualActorBackend backend) { this.backend = backend; }

    /** Installs the durable recording source used for newly created playback sessions. */
    public void setSourceFactory(Function<ActorDefinition, ActorPlaybackSource> sourceFactory) {
        this.sourceFactory = sourceFactory == null ? actor -> null : sourceFactory;
    }

    public void start(Player viewer, ActorDefinition actor) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>());
        ActorPlaybackSource source = sourceFactory.apply(actor);
        ActorPlayback playback = source == null ? new ActorPlayback(actor) : new ActorPlayback(actor, source);
        if (source instanceof IndexedActorPlaybackSource indexed) {
            try { indexed.prefetch(actor.reverse() ? Math.max(0, indexed.frameCount() - 1) : 0, IndexedActorPlaybackSource.DEFAULT_PREFETCH_RADIUS); }
            catch (Exception ignored) { }
        }
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
            prefetch(playback);
            render(playback, viewer);
        }
    }

    /** Advances ordinary actor sessions while leaving scene-owned actors to SceneManager. */
    public void tickStandalone(Player viewer, Set<ActorId> excluded) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.get(viewer.getUniqueId());
        if (viewerSessions == null) return;
        for (ActorPlayback playback : new HashSet<>(viewerSessions.values())) {
            if (excluded.contains(playback.actor().id())) continue;
            ActorDefinition actor = playback.actor();
            if (!actor.visible()) {
                backend.destroy(actor, viewer);
                continue;
            }
            playback.tick();
            prefetch(playback);
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

    public void tick(Player viewer) { tickStandalone(viewer, Set.of()); }
    public void clear() { sessions.clear(); }
    public int sessionCount() { return sessions.values().stream().mapToInt(Map::size).sum(); }

    private void prefetch(ActorPlayback playback) {
        if (!(playback.source() instanceof IndexedActorPlaybackSource indexed) || indexed.frameCount() == 0) return;
        int center = (int) Math.floor(Math.max(0.0d, playback.cursor().position()));
        try { indexed.prefetch(center, IndexedActorPlaybackSource.DEFAULT_PREFETCH_RADIUS); }
        catch (Exception ignored) { }
    }

    private void render(ActorPlayback playback, Player viewer) {
        ActorSample sample = playback.sampleState();
        if (sample == null || !sample.exists()) return;
        backend.update(playback.actor(), viewer, sample);
    }
}
