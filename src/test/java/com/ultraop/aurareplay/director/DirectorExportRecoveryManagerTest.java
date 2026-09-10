package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportRecoveryManagerTest {
    private static DirectorFrame frame(long index, DirectorExportSpec spec) {
        return new DirectorFrame(index, spec.tickForFrame(index), new CameraTransform(index, 2, 3, 0, 0, 0, 70));
    }

    @Test
    void discoversOnlyValidatedInterruptedExports() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-recovery-");
        Path output = dir.resolve("shot.json");
        Path checkpoint = output.resolveSibling(output.getFileName() + ".checkpoint.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 4, 20, 640, 360);
        DirectorExportManifestStreamWriter writer = new DirectorExportManifestStreamWriter(output);
        writer.start(spec);
        writer.accept(frame(0, spec));
        writer.accept(frame(1, spec));
        writer.pause();
        new DirectorExportCheckpointStore().save(checkpoint, new DirectorExportCheckpoint(spec, 2));

        List<DirectorExportRecovery> found = new DirectorExportRecoveryManager().discover(dir);
        assertEquals(1, found.size());
        assertEquals(output, found.get(0).output());
        assertEquals(2, found.get(0).nextFrameIndex());
    }

    @Test
    void ignoresCheckpointWithoutTemporaryManifest() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-recovery-");
        Path output = dir.resolve("shot.json");
        Path checkpoint = output.resolveSibling(output.getFileName() + ".checkpoint.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 4, 20, 640, 360);
        new DirectorExportCheckpointStore().save(checkpoint, new DirectorExportCheckpoint(spec, 1));

        assertTrue(new DirectorExportRecoveryManager().discover(dir).isEmpty());
    }

    @Test
    void ignoresCorruptTemporaryManifest() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-recovery-");
        Path output = dir.resolve("shot.json");
        Path checkpoint = output.resolveSibling(output.getFileName() + ".checkpoint.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 4, 20, 640, 360);
        new DirectorExportCheckpointStore().save(checkpoint, new DirectorExportCheckpoint(spec, 2));
        Files.writeString(output.resolveSibling(output.getFileName() + ".tmp"), "broken");

        assertTrue(new DirectorExportRecoveryManager().discover(dir).isEmpty());
        assertTrue(Files.exists(checkpoint));
        assertTrue(Files.exists(output.resolveSibling(output.getFileName() + ".tmp")));
    }

    @Test
    void cleanupDeletesOnlyCheckpoint() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-recovery-");
        Path output = dir.resolve("shot.json");
        Path checkpoint = output.resolveSibling(output.getFileName() + ".checkpoint.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 4, 20, 640, 360);
        DirectorExportManifestStreamWriter writer = new DirectorExportManifestStreamWriter(output);
        writer.start(spec);
        writer.accept(frame(0, spec));
        writer.pause();
        new DirectorExportCheckpointStore().save(checkpoint, new DirectorExportCheckpoint(spec, 1));

        DirectorExportRecovery recovery = new DirectorExportRecoveryManager().discover(dir).get(0);
        assertTrue(new DirectorExportRecoveryManager().deleteCheckpoint(recovery));
        assertFalse(Files.exists(checkpoint));
        assertTrue(Files.exists(recovery.temporaryManifest()));
    }
}
