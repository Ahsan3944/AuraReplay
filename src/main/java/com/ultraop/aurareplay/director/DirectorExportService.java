package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Builds deterministic director manifests and optionally persists them as export metadata. */
public final class DirectorExportService {
    private final DirectorExportWriter writer;

    public DirectorExportService() {
        this(new DirectorExportWriter());
    }

    public DirectorExportService(DirectorExportWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public DirectorExportManifest build(DirectorExportSpec spec,
                                        Function<Double, CameraTransform> sampler) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sampler, "sampler");
        List<DirectorFrame> frames = new ArrayList<>((int) Math.min(spec.frameCount(), Integer.MAX_VALUE));
        for (long index = 0; index < spec.frameCount(); index++) {
            frames.add(DirectorExportPlanner.sample(spec, index, sampler));
        }
        return new DirectorExportManifest(spec, frames);
    }

    public Path export(Path output,
                       DirectorExportSpec spec,
                       Function<Double, CameraTransform> sampler) throws IOException {
        DirectorExportManifest manifest = build(spec, sampler);
        return writer.write(output, manifest);
    }

    public DirectorExportWriter writer() {
        return writer;
    }
}
