package com.ultraop.aurareplay.director;

import java.util.Objects;

/** Immutable restart checkpoint for an incremental Director export. */
public record DirectorExportCheckpoint(
        DirectorExportSpec spec,
        long nextFrameIndex
) {
    public DirectorExportCheckpoint {
        Objects.requireNonNull(spec, "spec");
        if (nextFrameIndex < 0 || nextFrameIndex > spec.frameCount()) {
            throw new IllegalArgumentException("nextFrameIndex must be within export frame range");
        }
    }

    public boolean complete() {
        return nextFrameIndex == spec.frameCount();
    }
}
