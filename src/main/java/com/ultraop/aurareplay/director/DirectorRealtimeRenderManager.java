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
    private final Map<UUID, Throwable> failures = new ConcurrentHashMap<>();

    public boolean start(Player viewer,
                         DirectorExportSpec spec,
                         DirectorInGameRenderBridge bridge,
                         Function<Double, CameraTransform> sampler) {
        return start(viewer, spec, bridge, sampler, null);
    }

    public boolean start(Player viewer,
                         DirectorExportSpec spec,
                         DirectorInGameRenderBridge bridge,
                         Function<Double, CameraTransform> sampler,
                         DirectorCaptureSink captureSink) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(bridge, "bridge");
        Objects.requireNonNull(sampler, "sampler");
        UUID id = viewer.getUniqueId();
        if (sessions.containsKey(id)) return false;
        failures.remove(id);
        DirectorRealtimeRenderSession session = new DirectorRealtimeRenderSession(spec, bridge, viewer, sampler, captureSink);
        sessions.put(id, session);
        return true;
    }

    public boolean tick(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return tick(viewer.getUniqueId());
    }

    public boolean tick(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        DirectorRealtimeRenderSession session = sessions.get(viewerId);
        if (session == null) return false;
        try {
            session.tick();
        } catch (Throwable ex) {
            failures.put(viewerId, ex);
        }
        if (session.state() == DirectorRealtimeRenderSession.State.COMPLETED
                || session.state() == DirectorRealtimeRenderSession.State.STOPPED
                || session.state() == DirectorRealtimeRenderSession.State.FAILED) {
            sessions.remove(viewerId, session);
            if (session.state() == DirectorRealtimeRenderSession.State.FAILED && session.failure() != null) {
                failures.put(viewerId, session.failure());
            }
        }
        return true;
    }

    public boolean stop(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return stop(viewer.getUniqueId());
    }

    /** Stops and removes a session without requiring the Player object, for disconnect cleanup. */
    public boolean stop(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        DirectorRealtimeRenderSession session = sessions.remove(viewerId);
        if (session == null) return false;
        session.stop();
        return true;
    }

    public void stopAll(Iterable<? extends Player> viewers) {
        viewers.forEach(this::stop);
    }

    public boolean active(Player viewer) { return active(viewer.getUniqueId()); }

    public boolean active(UUID viewerId) {
        return sessions.containsKey(Objects.requireNonNull(viewerId, "viewerId"));
    }

    public DirectorRealtimeRenderSession.State state(Player viewer) {
        return state(viewer.getUniqueId());
    }

    public DirectorRealtimeRenderSession.State state(UUID viewerId) {
        DirectorRealtimeRenderSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        return session == null ? null : session.state();
    }

    public Throwable failure(Player viewer) { return failure(viewer.getUniqueId()); }

    public Throwable failure(UUID viewerId) { return failures.get(viewerId); }

    public Throwable consumeFailure(Player viewer) { return consumeFailure(viewer.getUniqueId()); }

    public Throwable consumeFailure(UUID viewerId) { return failures.remove(viewerId); }

    public long emittedFrames(Player viewer) { return emittedFrames(viewer.getUniqueId()); }

    public long emittedFrames(UUID viewerId) {
        DirectorRealtimeRenderSession session = sessions.get(viewerId);
        return session == null ? 0L : session.emittedFrames();
    }

    public long elapsedTicks(Player viewer) { return elapsedTicks(viewer.getUniqueId()); }

    public long elapsedTicks(UUID viewerId) {
        DirectorRealtimeRenderSession session = sessions.get(viewerId);
        return session == null ? 0L : session.elapsedTicks();
    }

    public void forEach(Consumer<DirectorRealtimeRenderSession> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        sessions.values().forEach(consumer);
    }

    public int sessionCount() { return sessions.size(); }

    /** Cancels every live session and capture before dropping manager state. */
    public void clear() {
        sessions.values().forEach(DirectorRealtimeRenderSession::stop);
        sessions.clear();
        failures.clear();
    }
}
