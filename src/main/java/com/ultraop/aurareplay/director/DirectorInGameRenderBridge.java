package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraController;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Applies deterministic director frames to a viewer-local camera and forwards them to a capture sink. */
public final class DirectorInGameRenderBridge {
    private final CameraController cameraController;
    private final DirectorFrameSink sink;

    public DirectorInGameRenderBridge(CameraController cameraController, DirectorFrameSink sink) {
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Applies one sampled frame to the viewer. The sink is invoked only after the
     * viewer camera has accepted the transform, so downstream capture sees the
     * exact frame that was requested.
     */
    public boolean render(Player viewer, DirectorFrame frame) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(frame, "frame");
        if (!cameraController.overrideTransform(viewer, frame.camera())) return false;
        sink.accept(frame);
        return true;
    }

    public CameraController cameraController() { return cameraController; }
    public DirectorFrameSink sink() { return sink; }
}
