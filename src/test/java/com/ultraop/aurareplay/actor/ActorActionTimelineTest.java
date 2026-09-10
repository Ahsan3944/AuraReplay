package com.ultraop.aurareplay.actor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActorActionTimelineTest {
    @Test
    void ordersEventsDeterministicallyAndQueriesHalfOpenRanges() {
        ActorActionTimeline timeline = new ActorActionTimeline(List.of(
                new ActorActionEvent(10, ActorAction.HURT),
                new ActorActionEvent(5, ActorAction.SWING),
                new ActorActionEvent(10, ActorAction.SWING)
        ));

        assertEquals(List.of(
                new ActorActionEvent(5, ActorAction.SWING),
                new ActorActionEvent(10, ActorAction.SWING),
                new ActorActionEvent(10, ActorAction.HURT)
        ), timeline.events());
        assertEquals(2, timeline.at(10).size());
        assertEquals(2, timeline.between(5, 10).size());
        assertTrue(timeline.between(10, 11).contains(new ActorActionEvent(10, ActorAction.HURT)));
    }

    @Test
    void rejectsDuplicateEventsAndInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActorActionEvent(-1, ActorAction.SWING));
        assertThrows(IllegalArgumentException.class,
                () -> new ActorActionTimeline(List.of(
                        new ActorActionEvent(2, ActorAction.SWING),
                        new ActorActionEvent(2, ActorAction.SWING))));
        assertThrows(IllegalArgumentException.class,
                () -> new ActorActionTimeline(List.of()).between(5, 4));
    }
}
