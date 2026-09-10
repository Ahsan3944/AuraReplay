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
        sessions.put(id, new DirectorRealtimeRenderSession(spec, bridge, viewer, sampler, captureSink));
        return true;
    }

    public boolean tick(Player viewer) {
        DirectorRealtimeRenderSession session = sessions.get(viewer.getUniqueId());
        if (session == null) return false;
        try {
            session.tick();
        } catch (Throwable ex) {
            failures.put(viewer.getUniqueId(), ex);
        }
        if (session.state() == DirectorRealtimeRenderSession.State.COMPLETED
                || session.state() == DirectorRealtimeRenderSession.State.STOPPED
                || session.state() == DirectorRealtimeRenderSession.State.FAILED) {
            sessions.remove(viewer.getUniqueId(), session);
            if (session.state() == DirectorRealtimeRenderSession.State.FAILED && session.failure() != null) {
                failures.put(viewer.getUniqueId(), session.failure());
            }
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

    public boolean active(Player viewer) { return sessions.containsKey(viewer.getUniqueId()); }

    public DirectorRealtimeRenderSession.State state(Player viewer) {
        DirectorRealtimeRenderSession session = sessions.get(viewer.getUniqueId());
        return session == null ? null : session.state();
    }

    public Throwable failure(Player viewer) { return failures.get(viewer.getUniqueId()); }

    public Throwable consumeFailure(Player viewer) { return failures.remove(viewer.getUniqueId()); }

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

    public void clear() {
        sessions.values().forEach(DirectorRealtimeRenderSession::stop);
        sessions.clear();
        failures.clear();
    }
}
