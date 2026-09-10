package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraBackend;
import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRealtimeRenderManagerLifecycleTest {
    @Test
    void stopByUuidCancelsPendingCapture() {
        Player viewer = playerStub();
        UUID id = viewer.getUniqueId();
        DirectorRealtimeRenderManager manager = new DirectorRealtimeRenderManager();
        DirectorExportSpec spec = new DirectorExportSpec("scene", 0, 1, 20, 1280, 720);
        DirectorCaptureSink capture = captureSink();
        manager.start(viewer, spec, bridge(), tick -> transform(), capture);

        assertTrue(manager.active(id));
        assertTrue(manager.stop(id));
        assertFalse(manager.active(id));
        assertEquals(1, cancels.get());
        assertEquals(0, completes.get());
    }

    @Test
    void clearCancelsAllLiveSessionsBeforeDroppingState() {
        DirectorRealtimeRenderManager manager = new DirectorRealtimeRenderManager();
        AtomicInteger localCancels = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            Player viewer = playerStub();
            DirectorCaptureSink capture = new DirectorCaptureSink() {
                @Override public void start(DirectorExportSpec spec) { }
                @Override public void accept(DirectorFrame frame) { }
                @Override public void complete() { }
                @Override public void cancel() { localCancels.incrementAndGet(); }
            };
            assertTrue(manager.start(viewer,
                    new DirectorExportSpec("scene", 0, 1, 20, 1280, 720),
                    bridge(), tick -> transform(), capture));
        }

        manager.clear();

        assertEquals(0, manager.sessionCount());
        assertEquals(2, localCancels.get());
    }

    private static final AtomicInteger cancels = new AtomicInteger();
    private static final AtomicInteger completes = new AtomicInteger();

    private static DirectorCaptureSink captureSink() {
        cancels.set(0);
        completes.set(0);
        return new DirectorCaptureSink() {
            @Override public void start(DirectorExportSpec spec) { }
            @Override public void accept(DirectorFrame frame) { }
            @Override public void complete() { completes.incrementAndGet(); }
            @Override public void cancel() { cancels.incrementAndGet(); }
        };
    }

    private static DirectorInGameRenderBridge bridge() {
        CameraBackend backend = new CameraBackend() {
            @Override public void activate(Player player, CameraDefinition camera) { }
            @Override public void update(Player player, CameraTransform transform) { }
            @Override public void deactivate(Player player) { }
        };
        return new DirectorInGameRenderBridge(new CameraController(backend), frame -> { });
    }

    private static CameraTransform transform() {
        return new CameraTransform(0, 64, 0, 0, 0, 0, 70);
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
