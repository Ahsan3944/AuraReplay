package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.TickSnapshot;
import com.ultraop.aurareplay.recording.action.ActorActionRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorActionTimelineFactoryTest {
    @Test
    void mapsOnlyMatchingSourceUuidAndPreservesTicks() {
        UUID source = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Recording recording = new Recording("actions", 4, List.of(
                new TickSnapshot(0, List.of(), List.of(new ActorActionRecord(0, other, ActorAction.SWING))),
                new TickSnapshot(1, List.of(), List.of(new ActorActionRecord(1, source, ActorAction.START_SPRINT))),
                new TickSnapshot(2, List.of(), List.of(new ActorActionRecord(2, source, ActorAction.SWING))),
                new TickSnapshot(3, List.of(), List.of(new ActorActionRecord(3, other, ActorAction.DEATH)))
        ));

        ActorActionTimeline timeline = ActorActionTimelineFactory.fromRecording(recording, source);

        assertEquals(List.of(
                new ActorActionEvent(1, ActorAction.START_SPRINT),
                new ActorActionEvent(2, ActorAction.SWING)
        ), timeline.events());
    }

    @Test
    void nullSourceProducesEmptyTimeline() {
        Recording recording = new Recording("empty", 1, List.of(
                new TickSnapshot(0, List.of(), List.of())
        ));

        assertEquals(List.of(), ActorActionTimelineFactory.fromRecording(recording, null).events());
    }
}
