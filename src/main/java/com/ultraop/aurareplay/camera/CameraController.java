package com.ultraop.aurareplay.camera;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates viewer-local camera sessions while keeping rendering behind CameraBackend. */
public final class CameraController {
    private final CameraBackend backend;
    private final Map<UUID, CameraPlaybackSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, CameraTransform> transforms = new ConcurrentHashMap<>();

    public CameraController(CameraBackend backend) { this.backend = backend; }

    public void start(Player viewer, CameraDefinition camera) { start(viewer, camera, 0.0); }

    public void start(Player viewer, CameraDefinition camera, double startTick) {
        stop(viewer);
        CameraPlaybackSession session = new CameraPlaybackSession(camera);
        session.start();
        session.setTick(startTick);
        sessions.put(viewer.getUniqueId(), session);
        backend.activate(viewer, camera);
        update(viewer, session.sample());
    }

    public boolean seek(Player viewer, double tick) {
        CameraPlaybackSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.active()) return false;
        session.setTick(tick);
        update(viewer, session.sample());
        return true;
    }

    /** Applies a viewer-local transform without mutating the shared camera definition. */
    public boolean overrideTransform(Player viewer, CameraTransform transform) {
        CameraPlaybackSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.active()) return false;
        update(viewer, transform);
        return true;
    }

    private void update(Player viewer, CameraTransform transform) {
        transforms.put(viewer.getUniqueId(), transform);
        backend.update(viewer, transform);
    }

    /** Returns the last transform applied to the viewer-local camera. */
    public CameraTransform currentTransform(Player viewer) {
        return transforms.get(viewer.getUniqueId());
    }

    /** Returns the active camera definition for the viewer, if any. */
    public CameraDefinition currentCamera(Player viewer) {
        CameraPlaybackSession session = sessions.get(viewer.getUniqueId());
        return session == null || !session.active() ? null : session.camera();
    }

    public double currentTick(Player viewer) {
        CameraPlaybackSession session = sessions.get(viewer.getUniqueId());
        return session == null ? 0.0 : session.tick();
    }

    public boolean active(Player viewer) {
        CameraPlaybackSession session = sessions.get(viewer.getUniqueId());
        return session != null && session.active();
    }

    public void tick(Player viewer) {
        CameraPlaybackSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.active()) return;
        session.advance(1.0);
        update(viewer, session.sample());
    }

    public void stop(Player viewer) {
        CameraPlaybackSession session = sessions.remove(viewer.getUniqueId());
        transforms.remove(viewer.getUniqueId());
        if (session != null) session.stop();
        backend.deactivate(viewer);
    }

    public void stopAll(Iterable<? extends Player> viewers) { viewers.forEach(this::stop); }
    public int sessionCount() { return sessions.size(); }
    public void clear() { sessions.clear(); transforms.clear(); }
}
