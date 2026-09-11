package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.action.ActorActionRecord;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorActionReversePlaybackTest {
    @Test
    void reversePlaybackEmitsOnlyTicksCrossedInReverse() {
        UUID source = UUID.randomUUID();
        Recording recording = recording(source);
        ActorDefinition actor = actor(recording, source);
        actor.setReverse(true);
        ActorPlayback playback = new ActorPlayback(actor);

        assertEquals(List.of(new ActorActionEvent(5, ActorAction.DEATH)), playback.actionsAt(5));
        assertEquals(List.of(), playback.actionsAt(4));
        assertEquals(List.of(new ActorActionEvent(2, ActorAction.HURT)), playback.actionsAt(2));
        assertEquals(List.of(), playback.actionsAt(1));
        assertEquals(List.of(new ActorActionEvent(0, ActorAction.SWING)), playback.actionsAt(0));
        assertEquals(List.of(), playback.actionsAt(0));
    }

    @Test
    void forwardSeekIsSilentAndNextTickResumesFromTarget() {
        UUID source = UUID.randomUUID();
        Recording recording = recording(source);
        ActorDefinition actor = actor(recording, source);
        ActorPlayback playback = new ActorPlayback(actor);

        assertEquals(List.of(new ActorActionEvent(0, ActorAction.SWING)), playback.actionsAt(0));
        assertEquals(List.of(new ActorActionEvent(2, ActorAction.HURT)), playback.actionsAt(2));

        playback.setScenePosition(5);
        assertEquals(List.of(), playback.actionsAt(5));
        assertEquals(List.of(new ActorActionEvent(5, ActorAction.DEATH)), playback.actionsAt(5.99));
    }

    @Test
    void reverseSeekIsSilentAndNextReverseTickResumesFromTarget() {
        UUID source = UUID.randomUUID();
        Recording recording = recording(source);
        ActorDefinition actor = actor(recording, source);
        actor.setReverse(true);
        ActorPlayback playback = new ActorPlayback(actor);

        assertEquals(List.of(new ActorActionEvent(5, ActorAction.DEATH)), playback.actionsAt(5));
        assertEquals(List.of(new ActorActionEvent(2, ActorAction.HURT)), playback.actionsAt(2));

        playback.setScenePosition(3); // reverse maps scene tick 3 -> source tick 2
        assertEquals(List.of(), playback.actionsAt(2));
        assertEquals(List.of(new ActorActionEvent(0, ActorAction.SWING)), playback.actionsAt(0));
    }

    private static ActorDefinition actor(Recording recording, UUID source) {
        ActorDefinition actor = new ActorDefinition(
                ActorId.random(), recording,
                new ActorTransform(0, 0, 0, 0, 0, 0, 1), source, null);
        actor.setActionTimeline(ActorActionTimelineFactory.fromRecording(recording, source));
        return actor;
    }

    private static Recording recording(UUID source) {
        return new Recording("actions", 6, List.of(
                new TickSnapshot(0, List.of(), List.of(new ActorActionRecord(0, source, ActorAction.SWING))),
                new TickSnapshot(1, List.of(), List.of()),
                new TickSnapshot(2, List.of(), List.of(new ActorActionRecord(2, source, ActorAction.HURT))),
                new TickSnapshot(3, List.of(), List.of()),
                new TickSnapshot(4, List.of(), List.of()),
                new TickSnapshot(5, List.of(), List.of(new ActorActionRecord(5, source, ActorAction.DEATH)))
        ));
    }
}
