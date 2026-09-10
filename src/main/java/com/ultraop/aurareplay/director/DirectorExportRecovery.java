package com.ultraop.aurareplay.director;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable description of an interrupted Director export that can be safely resumed. */
public record DirectorExportRecovery(
        Path output,
        Path checkpoint,
        Path temporaryManifest,
        DirectorExportCheckpoint checkpointState
) {
    public DirectorExportRecovery {
        output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
        checkpoint = Objects.requireNonNull(checkpoint, "checkpoint").toAbsolutePath().normalize();
        temporaryManifest = Objects.requireNonNull(temporaryManifest, "temporaryManifest").toAbsolutePath().normalize();
        checkpointState = Objects.requireNonNull(checkpointState, "checkpointState");
        if (checkpointState.complete()) throw new IllegalArgumentException("completed checkpoint is not recoverable");
    }

    public DirectorExportSpec spec() { return checkpointState.spec(); }
    public long nextFrameIndex() { return checkpointState.nextFrameIndex(); }
}
