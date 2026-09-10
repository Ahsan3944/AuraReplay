package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Manages viewer-local realtime Director render sessions on the server tick thread. */
public final class DirectorRealtimeRenderManager {
    private final Map<UUID, DirectorRealtimeRenderSession> sessions = new ConcurrentHashMap<>();

    public boolean start(Player viewer,
                         DirectorExportSpec spec,
                         DirectorInGameRenderBridge bridge,
                         Function<Double, CameraTransform> sampler) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(bridge, "bridge");
        Objects.requireNonNull(sampler, "sampler");
        UUID id = viewer.getUniqueId();
        if (sessions.containsKey(id)) return false;
        sessions.put(id, new DirectorRealtimeRenderSession(spec, bridge, viewer, sampler));
        return true;
    }

    public boolean tick(Player viewer) {
        DirectorRealtimeRenderSession session = sessions.get(viewer.getUniqueId());
        if (session == null) return false;
        session.tick();
        if (session.state() == DirectorRealtimeRenderSession.State.COMPLETED
                || session.state() == DirectorRealtimeRenderSession.State.STOPPED) {
            sessions.remove(viewer.getUniqueId(), session);
        }
        return true;
    }

    public boolean stop(Player viewer) {
        DirectorRealtimeRenderSession session = sessions.remove(viewer.getUniqueId());
        if (session == null) return false;
        session.stop();
        return true;
    }

    public void stopAll(Iterable<? extends Player> viewers) {
        viewers.forEach(this::stop);
    }

    public boolean active(Player viewer) {
        return sessions.containsKey(viewer.getUniqueId());
    }

    public DirectorRealtimeRenderSession.State state(Player viewer) {
        DirectorRealtimeRenderSession session = sessions.get(viewer.getUniqueId());
        return session == null ? null : session.state();
    }

    public long emittedFrames(Player viewer) {
        DirectorRealtimeRenderSession session = sessions.get(viewer.getUniqueId());
        return session == null ? 0L : session.emittedFrames();
    }

    public long elapsedTicks(Player viewer) {
        DirectorRealtimeRenderSession session = sessions.get(viewer.getUniqueId());
        return session == null ? 0L : session.elapsedTicks();
    }

    public void forEach(Consumer<DirectorRealtimeRenderSession> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        sessions.values().forEach(consumer);
    }

    public int sessionCount() { return sessions.size(); }

    public void clear() { sessions.clear(); }
}
