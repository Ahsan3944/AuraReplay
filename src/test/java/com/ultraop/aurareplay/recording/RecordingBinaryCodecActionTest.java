package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.actor.ActorAction;
import com.ultraop.aurareplay.recording.action.ActorActionRecord;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RecordingBinaryCodecActionTest {
    @Test
    void roundTripsActorActionRecord() throws Exception {
        UUID actor = UUID.randomUUID();
        Recording source = new Recording("action-round-trip", 3, List.of(
                new TickSnapshot(0, List.of(), List.of(new ActorActionRecord(0, actor, ActorAction.START_SPRINT))),
                new TickSnapshot(1, List.of(), List.of(new ActorActionRecord(1, actor, ActorAction.USE))),
                new TickSnapshot(2, List.of(), List.of(new ActorActionRecord(2, actor, ActorAction.DEATH)))
        ));

        Recording decoded = new RecordingBinaryCodec().decode(new RecordingBinaryCodec().encode(source));

        assertEquals(source.name(), decoded.name());
        assertEquals(source.durationTicks(), decoded.durationTicks());
        assertEquals(source.frames().size(), decoded.frames().size());
        for (int i = 0; i < source.frames().size(); i++) {
            assertEquals(source.frames().get(i).tick(), decoded.frames().get(i).tick());
            ActorActionRecord expected = source.frames().get(i).actions().stream()
                    .map(ActorActionRecord.class::cast)
                    .findFirst().orElseThrow();
            ActorActionRecord actual = assertInstanceOf(ActorActionRecord.class,
                    decoded.frames().get(i).actions().getFirst());
            assertEquals(expected.tick(), actual.tick());
            assertEquals(expected.actor(), actual.actor());
            assertEquals(expected.action(), actual.action());
        }
    }
}
