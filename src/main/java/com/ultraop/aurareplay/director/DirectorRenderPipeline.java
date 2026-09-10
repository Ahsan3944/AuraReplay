package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Deterministic frame sampler used by realtime rendering and offline export bridges. */
public final class DirectorRenderPipeline {
    private DirectorRenderPipeline() { }

    public static DirectorRenderFrame sample(
            DirectorExportSpec spec,
            long frameIndex,
            Function<Double, CameraTransform> cameraSampler,
            Iterable<ActorId> actorIds,
            Function<ActorId, ActorSample> actorSampler) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(cameraSampler, "cameraSampler");
        Objects.requireNonNull(actorIds, "actorIds");
        Objects.requireNonNull(actorSampler, "actorSampler");
        double tick = spec.tickForFrame(frameIndex);
        CameraTransform camera = Objects.requireNonNull(cameraSampler.apply(tick), "camera sampler result");
        Map<ActorId, ActorSample> actors = new LinkedHashMap<>();
        for (ActorId actorId : actorIds) {
            Objects.requireNonNull(actorId, "actorId");
            ActorSample sample = actorSampler.apply(actorId);
            if (sample != null) actors.put(actorId, sample);
        }
        return new DirectorRenderFrame(frameIndex, tick, camera, actors);
    }

    public static DirectorRenderFrame sampleAt(
            long frameIndex,
            double sceneTick,
            Function<Double, CameraTransform> cameraSampler,
            Iterable<ActorId> actorIds,
            Function<ActorId, ActorSample> actorSampler) {
        if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
        if (!Double.isFinite(sceneTick)) throw new IllegalArgumentException("sceneTick must be finite");
        Objects.requireNonNull(cameraSampler, "cameraSampler");
        Objects.requireNonNull(actorIds, "actorIds");
        Objects.requireNonNull(actorSampler, "actorSampler");
        CameraTransform camera = Objects.requireNonNull(cameraSampler.apply(sceneTick), "camera sampler result");
        Map<ActorId, ActorSample> actors = new LinkedHashMap<>();
        for (ActorId actorId : actorIds) {
            Objects.requireNonNull(actorId, "actorId");
            ActorSample sample = actorSampler.apply(actorId);
            if (sample != null) actors.put(actorId, sample);
        }
        return new DirectorRenderFrame(frameIndex, sceneTick, camera, actors);
    }
}
