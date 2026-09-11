package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorActionEvent;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable deterministic snapshot of one complete Director render frame. */
public record DirectorRenderFrame(
        long frameIndex,
        double sceneTick,
        CameraTransform camera,
        Map<ActorId, ActorSample> actors,
        Map<ActorId, List<ActorActionEvent>> actions
) {
    public DirectorRenderFrame {
        if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
        if (!Double.isFinite(sceneTick)) throw new IllegalArgumentException("sceneTick must be finite");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(actors, "actors");
        Objects.requireNonNull(actions, "actions");
        actors = Collections.unmodifiableMap(new LinkedHashMap<>(actors));
        Map<ActorId, List<ActorActionEvent>> actionCopy = new LinkedHashMap<>();
        for (Map.Entry<ActorId, List<ActorActionEvent>> entry : actions.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "action actor id");
            List<ActorActionEvent> events = List.copyOf(Objects.requireNonNull(entry.getValue(), "action events"));
            actionCopy.put(entry.getKey(), events);
        }
        actions = Collections.unmodifiableMap(actionCopy);
    }

    public DirectorRenderFrame(long frameIndex, double sceneTick, CameraTransform camera,
                               Map<ActorId, ActorSample> actors) {
        this(frameIndex, sceneTick, camera, actors, Map.of());
    }
}
