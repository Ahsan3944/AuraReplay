package com.ultraop.aurareplay.director;

import java.util.Objects;
import java.util.function.Function;

/** Converts an export specification into deterministic frame samples. */
public final class DirectorExportPlanner {
    private DirectorExportPlanner() { }

    public static DirectorFrame sample(DirectorExportSpec spec, long frameIndex,
                                       Function<Double, com.ultraop.aurareplay.camera.CameraTransform> sampler) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sampler, "sampler");
        double tick = spec.tickForFrame(frameIndex);
        return new DirectorFrame(frameIndex, tick, Objects.requireNonNull(sampler.apply(tick), "sampler result"));
    }
}
