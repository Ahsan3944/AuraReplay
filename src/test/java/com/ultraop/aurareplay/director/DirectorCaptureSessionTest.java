package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectorCaptureSessionTest {
    private static DirectorFrame frame(long index) {
        return new DirectorFrame(index, index, new CameraTransform((float) index, 0, 0, 0, 0, 0, 70));
    }

    @Test
    void forwardsLifecycleAndCompletesOnlyAfterAllFrames() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 3, 20, 1280, 720);
        List<String> events = new ArrayList<>();
        DirectorCaptureSink sink = new DirectorCaptureSink() {
            public void start(DirectorExportSpec ignored) { events.add("start"); }
            public void accept(DirectorFrame ignored) { events.add("frame"); }
            public void complete() { events.add("complete"); }
        };
        DirectorCaptureSession session = new DirectorCaptureSession(spec, sink);

        session.accept(frame(0));
        assertEquals(DirectorCaptureSession.State.RUNNING, session.state());
        session.accept(frame(1));
        session.accept(frame(2));

        assertEquals(DirectorCaptureSession.State.COMPLETED, session.state());
        assertEquals(3, session.capturedFrames());
        assertEquals(List.of("start", "frame", "frame", "frame", "complete"), events);
    }

    @Test
    void rejectsMisorderedAndWrongTickFrames() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 20, 40, 1280, 720);
        DirectorCaptureSession session = new DirectorCaptureSession(spec, new NoOpSink());

        assertThrows(IllegalArgumentException.class, () -> session.accept(
                new DirectorFrame(1, 10.5, new CameraTransform(0, 0, 0, 0, 0, 0, 70))));
        assertEquals(DirectorCaptureSession.State.RUNNING, session.state());

        assertThrows(IllegalArgumentException.class, () -> session.accept(
                new DirectorFrame(0, 10.1, new CameraTransform(0, 0, 0, 0, 0, 0, 70))));
    }

    @Test
    void cancelAndFailurePropagateLifecycle() {
        List<String> events = new ArrayList<>();
        DirectorCaptureSink sink = new DirectorCaptureSink() {
            public void start(DirectorExportSpec ignored) { events.add("start"); }
            public void accept(DirectorFrame ignored) { }
            public void complete() { }
            public void cancel() { events.add("cancel"); }
            public void fail(Throwable error) { events.add("fail"); }
        };
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 2, 20, 1280, 720);

        DirectorCaptureSession cancelled = new DirectorCaptureSession(spec, sink);
        cancelled.start();
        cancelled.cancel();
        assertEquals(DirectorCaptureSession.State.CANCELLED, cancelled.state());

        DirectorCaptureSession failed = new DirectorCaptureSession(spec, sink);
        RuntimeException error = new RuntimeException("capture failed");
        failed.start();
        failed.fail(error);
        assertEquals(DirectorCaptureSession.State.FAILED, failed.state());
        assertSame(error, failed.failure());
        assertEquals(List.of("start", "cancel", "start", "fail"), events);
    }

    @Test
    void adapterForwardsFrames() {
        List<DirectorFrame> frames = new ArrayList<>();
        DirectorFrameSinkCaptureAdapter adapter = new DirectorFrameSinkCaptureAdapter(frames::add);
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 1, 20, 1280, 720);
        adapter.start(spec);
        DirectorFrame frame = frame(0);
        adapter.accept(frame);
        adapter.complete();
        assertEquals(List.of(frame), frames);
    }

    private static final class NoOpSink implements DirectorCaptureSink {
        public void start(DirectorExportSpec spec) { }
        public void accept(DirectorFrame frame) { }
        public void complete() { }
    }
}
