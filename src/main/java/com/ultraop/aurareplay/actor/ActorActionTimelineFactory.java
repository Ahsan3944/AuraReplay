package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.action.Action;
import com.ultraop.aurareplay.recording.action.ActorActionRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Builds an actor-local action timeline from the immutable recording action stream. */
public final class ActorActionTimelineFactory {
    private ActorActionTimelineFactory() { }

    public static ActorActionTimeline fromRecording(Recording recording, UUID sourceEntityUuid) {
        Objects.requireNonNull(recording, "recording");
        if (sourceEntityUuid == null) return new ActorActionTimeline(List.of());

        List<ActorActionEvent> events = new ArrayList<>();
        for (var frame : recording.frames()) {
            for (Action action : frame.actions()) {
                if (action instanceof ActorActionRecord record && sourceEntityUuid.equals(record.actor())) {
                    events.add(new ActorActionEvent(record.tick(), record.action()));
                }
            }
        }
        return new ActorActionTimeline(events);
    }
}
