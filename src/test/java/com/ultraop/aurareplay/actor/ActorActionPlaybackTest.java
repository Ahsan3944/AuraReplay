package com.ultraop.aurareplay.actor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActorActionPlaybackTest {
    @Test
    void emitsEachForwardEventOnceAndHandlesSeekBackwards() {
        ActorActionPlayback playback = new ActorActionPlayback(new ActorActionTimeline(List.of(
                new ActorActionEvent(0, ActorAction.SWING),
                new ActorActionEvent(3, ActorAction.HURT),
                new ActorActionEvent(5, ActorAction.DEATH)
        )));

        assertEquals(List.of(new ActorActionEvent(0, ActorAction.SWING)), playback.advance(0));
        assertEquals(List.of(new ActorActionEvent(3, ActorAction.HURT)), playback.advance(3));
        assertTrue(playback.advance(3).isEmpty());
        assertTrue(playback.advance(2).isEmpty());
        assertEquals(List.of(new ActorActionEvent(5, ActorAction.DEATH)), playback.advance(5));
    }

    @Test
    void resetReplaysFromBeginningAndSeekDoesNotEmit() {
        ActorActionPlayback playback = new ActorActionPlayback(new ActorActionTimeline(
                List.of(new ActorActionEvent(2, ActorAction.SWING))));
        assertTrue(playback.advance(2).size() == 1);
        playback.reset();
        assertEquals(1, playback.advance(2).size());
        playback.seek(10);
        assertTrue(playback.advance(10).isEmpty());
    }
}
