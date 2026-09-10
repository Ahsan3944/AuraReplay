package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.camera.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DirectorRenderFrameTest {
    @Test
    void preservesDeterministicActorOrderAndDefensivelyCopiesMap() {
        ActorId first = new ActorId("first");
        ActorId second = new ActorId("second");
        Map<ActorId, ActorSample> source = new LinkedHashMap<>();
        source.put(first, null);
        source.put(second, null);

        DirectorRenderFrame frame = new DirectorRenderFrame(
                7, 12.5, CameraTransform.origin(1, 2, 3, 4, 5), source);
        source.clear();

        assertEquals(List.of(first, second), List.copyOf(frame.actors().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> frame.actors().clear());
        assertEquals(2, frame.actors().size());
    }

    @Test
    void rejectsInvalidFrameIndexAndSceneTick() {
        CameraTransform camera = CameraTransform.origin(0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new DirectorRenderFrame(-1, 0, camera, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DirectorRenderFrame(0, Double.NaN, camera, Map.of()));
    }
}
