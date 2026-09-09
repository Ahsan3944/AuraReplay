package com.ultraop.aurareplay.camera;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates viewer-local camera sessions while keeping rendering behind CameraBackend. */
public final class CameraController {
    private final CameraBackend backend;
    private final Map<UUID, CameraPlaybackSession> sessions = new ConcurrentHashMap<>();

    public CameraController(CameraBackend backend) { this.backend = backend; }

    public void start(Player viewer, CameraDefinition camera) {
        stop(viewer);
        CameraPlaybackSession session = new CameraPlaybackSession(camera);
        session.start();
        sessions.put(viewer.getUniqueId(), session);
        backend.activate(viewer, camera);
        backend.update(viewer, session.sample());
    }

    public void tick(Player viewer) {
        CameraPlaybackSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.active()) return;
        session.advance(1.0);
        backend.update(viewer, session.sample());
    }

    public void stop(Player viewer) {
        CameraPlaybackSession session = sessions.remove(viewer.getUniqueId());
        if (session != null) session.stop();
        backend.deactivate(viewer);
    }

    public void stopAll(Iterable<? extends Player> viewers) {
        viewers.forEach(this::stop);
    }

    public int sessionCount() { return sessions.size(); }
    public void clear() { sessions.clear(); }
}
