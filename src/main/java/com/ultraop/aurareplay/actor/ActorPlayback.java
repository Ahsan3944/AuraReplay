package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.util.List;

public final class ActorPlayback {
    private final ActorDefinition actor;
    private final PlaybackCursor cursor = new PlaybackCursor();

    public ActorPlayback(ActorDefinition actor) {
        this.actor = actor;
    }

    public ActorDefinition actor() { return actor; }
    public PlaybackCursor cursor() { return cursor; }

    public EntitySnapshot sample() {
        List<TickSnapshot> frames = actor.recording().frames();
        if (frames.isEmpty()) return null;
        int index = (int) Math.max(0, Math.min(cursor.position(), frames.size() - 1));
        TickSnapshot frame = frames.get(index);
        if (frame.entities().isEmpty()) return null;
        return frame.entities().get(0);
    }
}
