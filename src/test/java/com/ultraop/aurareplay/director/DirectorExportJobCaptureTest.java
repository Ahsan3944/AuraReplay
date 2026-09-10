package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorExportJobCaptureTest {
    @Test
    void forwardsExactSampledFramesIncludingNonZeroStartTick() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 11, 40, 1280, 720);
        List<DirectorFrame> captured = new ArrayList<>();
        DirectorExportJob job = new DirectorExportJob(
                spec,
                tick -> new CameraTransform(tick.floatValue(), 0, 0, 0, 0, 0, 70),
                captured::add);

        assertEquals(2, job.step(8));
        assertEquals(2, captured.size());
        assertEquals(0, captured.get(0).frameIndex());
        assertEquals(1, captured.get(1).frameIndex());
        assertEquals(10.0, captured.get(0).sceneTick());
        assertEquals(10.5, captured.get(1).sceneTick());
        assertEquals(List.of(0L, 1L), captured.stream().map(DirectorFrame::frameIndex).toList());
    }
}
