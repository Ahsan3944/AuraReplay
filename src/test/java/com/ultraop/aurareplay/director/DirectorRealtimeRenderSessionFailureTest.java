package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderSessionFailureTest {
    @Test
    void samplerFailureMovesSessionToFailedAndPreservesCause() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 11, 20, 1280, 720);
        RuntimeException failure = new RuntimeException("sampler failed");
        Player viewer = new TestPlayerStub();
        DirectorFrameSink sink = frame -> { };
        DirectorInGameRenderBridge bridge = new DirectorInGameRenderBridge(
                new CameraController(new CameraBackendStub()), sink);
        DirectorRealtimeRenderSession session = new DirectorRealtimeRenderSession(
                spec, bridge, viewer, tick -> { throw failure; });

        assertEquals(0, session.tick());
        assertEquals(DirectorRealtimeRenderSession.State.FAILED, session.state());
        assertSame(failure, session.failure());
        assertEquals(0, session.emittedFrames());
    }

    private static final class CameraBackendStub implements CameraBackend {
        @Override public void activate(Player viewer, CameraDefinition camera) { }
        @Override public void update(Player viewer, CameraTransform transform) { }
        @Override public void deactivate(Player viewer) { }
    }

    /** Minimal Mockito-free test stub; only identity is required by the session. */
    private static final class TestPlayerStub implements Player {
        @Override public UUID getUniqueId() { return UUID.randomUUID(); }
        @Override public String getName() { return "test"; }
    }
}
