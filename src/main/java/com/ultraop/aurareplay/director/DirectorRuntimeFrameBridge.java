package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Consumer;

/** Builds one complete deterministic Director frame and delivers it to the runtime renderer. */
public final class DirectorRuntimeFrameBridge {
    private final ActorPlaybackController actors;
    private final DirectorRenderFrameSink frameSink;

    public DirectorRuntimeFrameBridge(ActorPlaybackController actors, DirectorRenderFrameSink frameSink) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.frameSink = Objects.requireNonNull(frameSink, "frameSink");
    }

    public DirectorRenderFrame sample(Player viewer, Scene scene, long frameIndex, double sceneTick,
                                      CameraTransform camera) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(camera, "camera");
        DirectorRenderFrame frame = DirectorRenderPipeline.sampleAt(
                frameIndex, sceneTick, ignored -> camera, scene.actorIds(),
                actorId -> actors.sample(viewer, actorId));
        frameSink.accept(frame);
        return frame;
    }

    public ActorPlaybackController actors() { return actors; }
    public DirectorRenderFrameSink frameSink() { return frameSink; }
}
