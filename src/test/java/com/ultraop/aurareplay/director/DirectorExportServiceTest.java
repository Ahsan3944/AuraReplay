package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportServiceTest {
    private static CameraTransform transform(double tick) {
        return new CameraTransform(tick, 64, tick * 2, 0, 0, 0, 70);
    }

    @Test
    void buildsCompleteManifestFromDeterministicSampler() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 12, 20, 1280, 720);
        DirectorExportManifest manifest = new DirectorExportService().build(spec, DirectorExportServiceTest::transform);

        assertTrue(manifest.complete());
        assertEquals(2, manifest.frames().size());
        assertEquals(10.0, manifest.frames().get(0).sceneTick());
        assertEquals(11.0, manifest.frames().get(1).sceneTick());
        assertEquals(11.0, manifest.frames().get(1).camera().x());
        assertEquals(22.0, manifest.frames().get(1).camera().z());
    }

    @Test
    void writesManifestToNestedOutputPath() throws Exception {
        DirectorExportSpec spec = new DirectorExportSpec("export", 0, 1, 20, 640, 360);
        Path root = Files.createTempDirectory("aurareplay-export-test");
        Path output = root.resolve("nested").resolve("shot.json");

        Path written = new DirectorExportService().export(output, spec, tick -> transform(tick));

        assertEquals(output.toAbsolutePath().normalize(), written);
        assertTrue(Files.exists(written));
        String json = Files.readString(written);
        assertTrue(json.contains("\"scene\": \"export\""));
        assertTrue(json.contains("\"frameCount\": 1"));
    }

    @Test
    void preservesSamplerFailure() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 1, 20, 640, 360);
        DirectorExportService service = new DirectorExportService();

        assertThrows(IllegalStateException.class,
                () -> service.build(spec, tick -> { throw new IllegalStateException("sampler failed"); }));
    }
}
