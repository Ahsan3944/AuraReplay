package com.ultraop.aurareplay.director;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportCheckpointStoreTest {
    @Test
    void savesAndLoadsMatchingCheckpointAtomically() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-checkpoint");
        Path file = dir.resolve("export.checkpoint.json");
        DirectorExportSpec spec = new DirectorExportSpec("scene\"one", 10, 20, 40, 1280, 720);
        DirectorExportCheckpointStore store = new DirectorExportCheckpointStore();

        store.save(file, new DirectorExportCheckpoint(spec, 17));
        DirectorExportCheckpoint loaded = store.load(file, spec);

        assertEquals(17, loaded.nextFrameIndex());
        assertEquals(spec, loaded.spec());
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(file.resolveSibling("export.checkpoint.json.tmp")));
    }

    @Test
    void rejectsCheckpointForDifferentSpec() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-checkpoint");
        Path file = dir.resolve("checkpoint.json");
        DirectorExportSpec original = new DirectorExportSpec("scene", 0, 10, 20, 640, 360);
        DirectorExportSpec different = new DirectorExportSpec("scene", 0, 11, 20, 640, 360);
        DirectorExportCheckpointStore store = new DirectorExportCheckpointStore();
        store.save(file, new DirectorExportCheckpoint(original, 4));

        assertThrows(IllegalArgumentException.class, () -> store.load(file, different));
    }

    @Test
    void deletesCheckpoint() throws Exception {
        Path dir = Files.createTempDirectory("aurareplay-checkpoint");
        Path file = dir.resolve("checkpoint.json");
        DirectorExportCheckpointStore store = new DirectorExportCheckpointStore();
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 2, 20, 640, 360);
        store.save(file, new DirectorExportCheckpoint(spec, 1));
        store.delete(file);
        assertFalse(Files.exists(file));
    }
}
