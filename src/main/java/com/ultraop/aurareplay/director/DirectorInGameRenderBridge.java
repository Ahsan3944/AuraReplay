package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.camera.CameraController;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Applies deterministic director frames to viewer-local camera and actor render state. */
public final class DirectorInGameRenderBridge {
    private final CameraController cameraController;
    private final ActorPlaybackController actorPlayback;
    private final DirectorFrameSink sink;

    public DirectorInGameRenderBridge(CameraController cameraController, DirectorFrameSink sink) {
        this(cameraController, null, sink);
    }

    public DirectorInGameRenderBridge(CameraController cameraController,
                                      ActorPlaybackController actorPlayback,
                                      DirectorFrameSink sink) {
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController");
        this.actorPlayback = actorPlayback;
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Applies the legacy camera-only frame and forwards it to the capture sink. */
    public boolean render(Player viewer, DirectorFrame frame) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(frame, "frame");
        if (!cameraController.overrideTransform(viewer, frame.camera())) return false;
        sink.accept(frame);
        return true;
    }

    /** Applies a complete deterministic render frame, including every sampled actor state. */
    public boolean render(Player viewer, DirectorRenderFrame frame) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(frame, "frame");
        if (!cameraController.overrideTransform(viewer, frame.camera())) return false;
        if (actorPlayback != null) {
            for (var entry : frame.actors().entrySet()) {
                ActorId actorId = entry.getKey();
                ActorSample sample = entry.getValue();
                actorPlayback.renderSample(viewer, actorId, sample);
            }
        }
        return true;
    }

    public CameraController cameraController() { return cameraController; }
    public ActorPlaybackController actorPlayback() { return actorPlayback; }
    public DirectorFrameSink sink() { return sink; }
}
