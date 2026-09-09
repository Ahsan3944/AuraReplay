package com.ultraop.aurareplay.actor;

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
                mainThreadExecutor.execute(() -> {
                    if (ready instanceof IndexedActorPlaybackSource indexed && prefetchExecutor != null) indexed.prefetchAsync(initialFrame(actor, indexed), IndexedActorPlaybackSource.DEFAULT_PREFETCH_RADIUS, prefetchExecutor);
                    replaceSource(viewer.getUniqueId(), actor.id(), ready);
                });
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
    public void tick(Player viewer){tickStandalone(viewer,Set.of());}
    public void clear(){for(Map<ActorId,ActorPlayback> viewerSessions:sessions.values())for(ActorPlayback playback:viewerSessions.values())closeSource(playback.source());sessions.clear();}
    public int sessionCount(){return sessions.values().stream().mapToInt(Map::size).sum();}

    private void prefetch(ActorPlayback playback){if(!(playback.source() instanceof IndexedActorPlaybackSource indexed)||prefetchExecutor==null||indexed.frameCount()==0)return;int center=(int)Math.floor(Math.max(0.0d,playback.cursor().position()));if(center%IndexedActorPlaybackSource.DEFAULT_PREFETCH_RADIUS!=0)return;indexed.prefetchAsync(center,IndexedActorPlaybackSource.DEFAULT_PREFETCH_RADIUS,prefetchExecutor);}
    private static int initialFrame(ActorDefinition actor,IndexedActorPlaybackSource source){return actor.reverse()?Math.max(0,source.frameCount()-1):0;}
    private static void closeSource(ActorPlaybackSource source){if(source instanceof IndexedActorPlaybackSource indexed)indexed.clearCache();}
    private void render(ActorPlayback playback,Player viewer){ActorSample sample=playback.sampleState();if(sample==null||!sample.exists())return;backend.update(playback.actor(),viewer,sample);}
}
