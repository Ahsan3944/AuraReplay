package com.ultraop.aurareplay.director;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Discovers and validates interrupted Director exports without modifying recoverable state. */
public final class DirectorExportRecoveryManager {
    private static final String CHECKPOINT_SUFFIX = ".checkpoint.json";
    private final DirectorExportCheckpointStore checkpoints;

    public DirectorExportRecoveryManager() { this(new DirectorExportCheckpointStore()); }
    public DirectorExportRecoveryManager(DirectorExportCheckpointStore checkpoints) { this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints"); }

    /** Finds recoverable checkpoint/temp pairs directly under the supplied export directory. */
    public List<DirectorExportRecovery> discover(Path exportDirectory) throws IOException {
        Objects.requireNonNull(exportDirectory, "exportDirectory");
        Path dir = exportDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) return List.of();
        List<DirectorExportRecovery> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path checkpoint : paths.filter(p -> p.getFileName().toString().endsWith(CHECKPOINT_SUFFIX)).toList()) {
                DirectorExportRecovery recovery = inspect(checkpoint);
                if (recovery != null) result.add(recovery);
            }
        }
        result.sort(Comparator.comparing(r -> r.output().toString()));
        return List.copyOf(result);
    }

    /** Returns one validated recovery candidate, or null when the pair is absent/invalid. */
    public DirectorExportRecovery inspect(Path checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Path cp = checkpoint.toAbsolutePath().normalize();
        if (!Files.isRegularFile(cp)) return null;
        DirectorExportCheckpoint state;
        try {
            state = checkpoints.loadMetadata(cp);
        } catch (RuntimeException ex) {
            return null;
        }
        if (state.complete()) return null;
        Path output = outputFromCheckpoint(cp);
        Path temp = output.resolveSibling(output.getFileName() + ".tmp");
        if (!Files.isRegularFile(temp)) return null;
        try {
            DirectorExportManifestStreamWriter.validateCheckpoint(temp, state.spec(), state.nextFrameIndex());
        } catch (RuntimeException | IOException ex) {
            return null;
        }
        return new DirectorExportRecovery(output, cp, temp, state);
    }

    /** Removes only an explicitly supplied stale checkpoint; the caller chooses when cleanup is safe. */
    public boolean deleteCheckpoint(DirectorExportRecovery recovery) throws IOException {
        Objects.requireNonNull(recovery, "recovery");
        return Files.deleteIfExists(recovery.checkpoint());
    }

    private static Path outputFromCheckpoint(Path checkpoint) {
        String name = checkpoint.getFileName().toString();
        String outputName = name.substring(0, name.length() - CHECKPOINT_SUFFIX.length());
        return checkpoint.resolveSibling(outputName);
    }
}
