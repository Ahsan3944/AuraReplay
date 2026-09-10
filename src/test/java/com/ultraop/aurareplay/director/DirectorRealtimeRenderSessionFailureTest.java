package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraBackend;
import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderSessionFailureTest {
    @Test
    void samplerFailureMovesSessionToFailedAndPreservesCause() {
        DirectorExportSpec spec = new DirectorExportSpec("scene", 10, 11, 20, 1280, 720);
        RuntimeException failure = new RuntimeException("sampler failed");
        Player viewer = playerStub();
        DirectorFrameSink sink = frame -> { };
        CameraBackend backend = new CameraBackend() {
            @Override public void activate(Player player, CameraDefinition camera) { }
            @Override public void update(Player player, CameraTransform transform) { }
            @Override public void deactivate(Player player) { }
        };
        DirectorInGameRenderBridge bridge = new DirectorInGameRenderBridge(new CameraController(backend), sink);
        DirectorRealtimeRenderSession session = new DirectorRealtimeRenderSession(
                spec, bridge, viewer, tick -> { throw failure; });

        assertEquals(0, session.tick());
        assertEquals(DirectorRealtimeRenderSession.State.FAILED, session.state());
        assertSame(failure, session.failure());
        assertEquals(0, session.emittedFrames());
    }

    private static Player playerStub() {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    if (method.getName().equals("getName")) return "test";
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == byte.class) return (byte) 0;
                    if (method.getReturnType() == short.class) return (short) 0;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0F;
                    if (method.getReturnType() == double.class) return 0D;
                    if (method.getReturnType() == char.class) return '\0';
                    return null;
                });
    }
}
