package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;

/** Frame source abstraction allowing actors to play from memory or indexed disk recordings. */
public interface ActorPlaybackSource {
    int frameCount();
    TickSnapshot frame(int index) throws IOException;
}
