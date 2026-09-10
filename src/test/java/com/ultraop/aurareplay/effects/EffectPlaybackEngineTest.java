package com.ultraop.aurareplay.effects;

import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.timeline.Timeline;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class EffectPlaybackEngineTest {
    @Test
    void ordinaryAdvanceDoesNotFireAtInitialPosition() {
        Scene scene = scene();
        scene.addEffect(EffectCue.particle("a", 10, 0, 64, 0, "FLAME", 1, 0));
        EffectPlaybackCursor cursor = new EffectPlaybackCursor();
        EffectPlaybackEngine engine = new EffectPlaybackEngine();
        Player viewer = player();
        engine.reset(cursor, 0);
        assertEquals(0, engine.advance(viewer, scene, cursor, 0, false));
    }

    @Test
    void loopGenerationAndTraversalAreDeterministic() {
        Scene scene = scene();
        scene.addEffect(EffectCue.particle("a", 5, 0, 64, 0, "FLAME", 1, 0));
        scene.addEffect(EffectCue.particle("b", 15, 0, 64, 0, "FLAME", 1, 0));
        EffectPlaybackCursor cursor = new EffectPlaybackCursor();
        cursor.reset(12);
        assertEquals(0, cursor.loopGeneration());
        cursor.nextLoop();
        cursor.commit(2);
        assertEquals(1, cursor.loopGeneration());
        assertEquals(2, cursor.previousTick());
    }

    private static Scene scene() { return new Scene("effects-test", new Timeline(20)); }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == float.class) return 0f;
            if (method.getReturnType() == double.class) return 0d;
            return null;
        });
    }
}
