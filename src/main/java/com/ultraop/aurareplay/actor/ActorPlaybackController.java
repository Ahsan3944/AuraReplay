package com.ultraop.aurareplay.actor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Main-thread playback coordinator. Disk-backed sources are loaded and warmed asynchronously. */
public final class ActorPlaybackController {
    private static final int PREFETCH_RADIUS = 16;
    private static final int PREFETCH_TRIGGER = 8;
    private final VirtualActorBackend backend;
    private final Map<UUID, Map<ActorId, ActorPlayback>> sessions = new ConcurrentHashMap<>();
    private Function<ActorDefinition, ActorPlaybackSource> sourceFactory = actor -> null;
    private Function<ActorDefinition, CompletableFuture<ActorPlaybackSource>> asyncSourceFactory;
    private Executor prefetchExecutor;
    private Executor mainThreadExecutor = Runnable::run;

    public ActorPlaybackController(VirtualActorBackend backend) { this.backend = backend; }
    public void setSourceFactory(Function<ActorDefinition, ActorPlaybackSource> factory) { sourceFactory = factory == null ? actor -> null : factory; }
    public void setAsyncSourceFactory(Function<ActorDefinition, CompletableFuture<ActorPlaybackSource>> factory, Executor executor) { asyncSourceFactory = factory; prefetchExecutor = executor; }
    public void setMainThreadExecutor(Executor executor) { mainThreadExecutor = executor == null ? Runnable::run : executor; }

    public void start(Player viewer, ActorDefinition actor) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        ActorPlaybackSource source = sourceFactory.apply(actor);
        ActorPlayback playback = source == null ? new ActorPlayback(actor) : new ActorPlayback(actor, source);
        viewerSessions.put(actor.id(), playback);
        if (actor.visible()) { backend.spawn(actor, viewer); backend.updateIdentity(actor, viewer); }
        render(playback, viewer);
        if (asyncSourceFactory != null) {
            asyncSourceFactory.apply(actor).whenComplete((ready, error) -> {
                if (error != null || ready == null) return;
                int initial = initialFrame(actor, ready);
                CompletableFuture<Void> warm = ready instanceof IndexedActorPlaybackSource indexed && prefetchExecutor != null
                        ? indexed.prefetchAsync(initial, PREFETCH_RADIUS, prefetchExecutor)
                        : CompletableFuture.completedFuture(null);
                warm.whenComplete((ignored, warmError) -> mainThreadExecutor.execute(() -> {
                    if (warmError != null) return;
                    replaceSource(viewer.getUniqueId(), actor.id(), ready);
                }));
            });
        }
    }

    public void replaceSource(UUID viewerId, ActorId actorId, ActorPlaybackSource source) {
        Map<ActorId, ActorPlayback> viewerSessions = sessions.get(viewerId); if (viewerSessions == null) return;
        ActorPlayback old = viewerSessions.get(actorId); if (old == null) return;
        ActorPlayback replacement = new ActorPlayback(old.actor(), source);
        replacement.setScenePosition(old.cursor().position());
        if (viewerSessions.replace(actorId, old, replacement)) closeSource(old.source());
    }

    /** Returns the current render sample for one viewer-local actor session. Must run on the server thread. */
    public ActorSample sample(Player viewer, ActorId actorId) {
        if (viewer == null || actorId == null) return null;
        Map<ActorId, ActorPlayback> viewerSessions = sessions.get(viewer.getUniqueId());
        if (viewerSessions == null) return null;
        ActorPlayback playback = viewerSessions.get(actorId);
        return playback == null ? null : playback.sampleState();
    }

    /** Applies an already sampled actor state without advancing its playback cursor. */
    public boolean renderSample(Player viewer, ActorId actorId, ActorSample sample) {
        if (viewer == null || actorId == null || sample == null || !sample.exists()) return false;
        Map<ActorId, ActorPlayback> viewerSessions = sessions.get(viewer.getUniqueId());
        if (viewerSessions == null) return false;
        ActorPlayback playback = viewerSessions.get(actorId);
        if (playback == null) return false;
        if (!playback.actor().visible()) {
            backend.destroy(playback.actor(), viewer);
            return false;
        }
        backend.update(playback.actor(), viewer, sample);
        return true;
    }

    public void tickScene(Player viewer, Iterable<ActorId> actorIds, double sceneTick) {
        Map<ActorId, ActorPlayback> viewerSessions=sessions.get(viewer.getUniqueId()); if(viewerSessions==null)return;
        for(ActorId actorId:actorIds){ActorPlayback playback=viewerSessions.get(actorId);if(playback==null)continue;ActorDefinition actor=playback.actor();if(!actor.visible()){backend.destroy(actor,viewer);continue;}if(!actor.frozen())playback.setScenePosition(sceneTick);prefetch(playback);render(playback,viewer);}
    }
    public void tickStandalone(Player viewer, Set<ActorId> excluded) {
        Map<ActorId,ActorPlayback> viewerSessions=sessions.get(viewer.getUniqueId());if(viewerSessions==null)return;
        for(ActorPlayback playback:new HashSet<>(viewerSessions.values())){if(excluded.contains(playback.actor().id()))continue;ActorDefinition actor=playback.actor();if(!actor.visible()){backend.destroy(actor,viewer);continue;}playback.tick();prefetch(playback);render(playback,viewer);}
    }
    public void stop(Player viewer, ActorId actorId){Map<ActorId,ActorPlayback>m=sessions.get(viewer.getUniqueId());if(m==null)return;ActorPlayback p=m.remove(actorId);if(p!=null){backend.destroy(p.actor(),viewer);closeSource(p.source());}if(m.isEmpty())sessions.remove(viewer.getUniqueId(),m);}
    public void stopAll(Player viewer){Map<ActorId,ActorPlayback>m=sessions.remove(viewer.getUniqueId());if(m==null)return;for(ActorPlayback p:m.values()){backend.destroy(p.actor(),viewer);closeSource(p.source());}}
    public int stopUsingRecording(String recordingName) {
        String normalized = normalizeRecordingName(recordingName); int stopped = 0;
        for (Map.Entry<UUID, Map<ActorId, ActorPlayback>> entry : new HashSet<>(sessions.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey()); if (viewer == null) continue;
            for (ActorPlayback playback : new HashSet<>(entry.getValue().values())) {
                if (normalizeRecordingName(playback.actor().recording().name()).equals(normalized)) { stop(viewer, playback.actor().id()); stopped++; }
            }
        }
        return stopped;
    }
    public int stopUsingActor(ActorId actorId) {
        if (actorId == null) return 0; int stopped = 0;
        for (Map.Entry<UUID, Map<ActorId, ActorPlayback>> entry : new HashSet<>(sessions.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey()); if (viewer == null) continue;
            if (entry.getValue().containsKey(actorId)) { stop(viewer, actorId); stopped++; }
        }
        return stopped;
    }
    public void tick(Player viewer){tickStandalone(viewer,Set.of());}
    public void clear(){for(Map<ActorId,ActorPlayback> viewerSessions:sessions.values())for(ActorPlayback playback:viewerSessions.values())closeSource(playback.source());sessions.clear();}
    public int sessionCount(){return sessions.values().stream().mapToInt(Map::size).sum();}
    private void prefetch(ActorPlayback playback){
        if(!(playback.source() instanceof IndexedActorPlaybackSource indexed)||prefetchExecutor==null||indexed.frameCount()==0)return;
        int center=(int)Math.floor(Math.max(0.0d,playback.cursor().position())); int distanceToEnd=indexed.frameCount()-1-center;
        if(center<=PREFETCH_TRIGGER || distanceToEnd<=PREFETCH_TRIGGER || center%PREFETCH_TRIGGER==0) indexed.prefetchAsync(center,PREFETCH_RADIUS,prefetchExecutor);
    }
    private static int initialFrame(ActorDefinition actor,ActorPlaybackSource source){return actor.reverse()?Math.max(0,source.frameCount()-1):0;}
    private static void closeSource(ActorPlaybackSource source){if(source instanceof IndexedActorPlaybackSource indexed)indexed.clearCache();}
    private static String normalizeRecordingName(String name){return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);}
    private void render(ActorPlayback playback,Player viewer){ActorSample sample=playback.sampleState();if(sample==null||!sample.exists())return;backend.update(playback.actor(),viewer,sample);}
}