package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.util.List;

/** Immutable completed capture. Persistence can serialize this without touching Bukkit. */
public record Recording(
        String name,
        long durationTicks,
        List<TickSnapshot> frames
) {
    public Recording {
        frames = List.copyOf(frames);
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }
}
