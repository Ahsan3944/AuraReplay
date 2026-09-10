package com.ultraop.aurareplay.director;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Persists director export manifests safely without introducing a JSON dependency. */
public final class DirectorExportWriter {
    public Path write(Path output, DirectorExportManifest manifest) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(manifest, "manifest");
        Path target = output.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, manifest.toJson(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
