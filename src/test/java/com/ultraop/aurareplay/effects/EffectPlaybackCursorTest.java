package com.ultraop.aurareplay.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectPlaybackCursorTest {
    @Test
    void forwardCrossingUsesHalfOpenInterval() {
        EffectPlaybackCursor cursor = new EffectPlaybackCursor();
        cursor.reset(10);
        assertFalse(cursor.crossedForward(10, 10, 20));
        assertTrue(cursor.crossedForward(20, 10, 20));
        assertTrue(cursor.crossedForward(15, 10, 20));
        assertFalse(cursor.crossedForward(21, 10, 20));
    }

    @Test
    void reverseCrossingUsesHalfOpenInterval() {
        EffectPlaybackCursor cursor = new EffectPlaybackCursor();
        cursor.reset(20);
        assertFalse(cursor.crossedReverse(20, 20, 10));
        assertTrue(cursor.crossedReverse(10, 20, 10));
        assertTrue(cursor.crossedReverse(15, 20, 10));
        assertFalse(cursor.crossedReverse(9, 20, 10));
    }

    @Test
    void loopGenerationAdvancesOnlyAtSeam() {
        EffectPlaybackCursor cursor = new EffectPlaybackCursor();
        cursor.reset(5);
        assertEquals(0, cursor.loopGeneration());
        cursor.nextLoop();
        assertEquals(1, cursor.loopGeneration());
        cursor.commit(2);
        assertEquals(1, cursor.loopGeneration());
    }
}
