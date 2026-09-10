package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRenderSessionTest {
    private static CameraTransform transform() {
        return new CameraTransform(0, 64, 0, 0, 0, 70);
    }

    @Test
    void capturesFramesInExactOrder() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 40, 60, 30, 1920, 1080);
        List<DirectorFrame> frames = new ArrayList<>();
        DirectorRenderSession session = new DirectorRenderSession(spec, tick -> transform(), frames::add);

        assertEquals(30, session.captureAll());
        assertEquals(30, frames.size());
        assertEquals(0, frames.get(0).frameIndex());
        assertEquals(40.0, frames.get(0).sceneTick());
        assertEquals(29, frames.get(29).frameIndex());
        assertEquals(59.333333333333336, frames.get(29).sceneTick(), 1.0e-12);
        assertTrue(session.completed());
        assertFalse(session.captureNext());
    }

    @Test
    void canCaptureIncrementallyAndStop() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 20, 20, 1280, 720);
        List<Long> indexes = new ArrayList<>();
        DirectorRenderSession session = new DirectorRenderSession(spec, tick -> transform(),
                frame -> indexes.add(frame.frameIndex()));

        assertTrue(session.captureNext());
        assertEquals(1, session.nextFrameIndex());
        assertTrue(session.captureNext());
        assertEquals(List.of(0L, 1L), indexes);
        session.stop();
        assertEquals(DirectorRenderSession.State.STOPPED, session.state());
        assertFalse(session.captureNext());
    }

    @Test
    void rejectsUseAfterStart() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 20, 20, 1280, 720);
        DirectorRenderSession session = new DirectorRenderSession(spec, tick -> transform(), frame -> {});
        session.start();
        assertThrows(IllegalStateException.class, session::start);
    }
}
