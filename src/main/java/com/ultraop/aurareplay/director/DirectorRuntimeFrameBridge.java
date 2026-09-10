package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Consumer;

/** Builds and applies one complete Director render frame on the server thread. */
public final class DirectorRuntimeFrameBridge {
    private final ActorPlaybackController actors;
    private final Consumer<DirectorRenderFrame> frameConsumer;

    public DirectorRuntimeFrameBridge(ActorPlaybackController actors, Consumer<DirectorRenderFrame> frameConsumer) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.frameConsumer = Objects.requireNonNull(frameConsumer, "frameConsumer");
    }

    public DirectorRenderFrame sample(Player viewer, Scene scene, long frameIndex, double sceneTick,
                                      CameraTransform camera) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(camera, "camera");
        DirectorRenderFrame frame = DirectorRenderPipeline.sampleAt(
                frameIndex, sceneTick, ignored -> camera, scene.actorIds(),
                actorId -> actors.sample(viewer, actorId));
        frameConsumer.accept(frame);
        return frame;
    }

    public ActorPlaybackController actors() { return actors; }
}
