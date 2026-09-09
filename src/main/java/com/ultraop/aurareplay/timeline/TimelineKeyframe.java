package com.ultraop.aurareplay.timeline;

import java.util.Objects;

/** Generic keyframe anchor. Value interpretation belongs to the owning editor/channel. */
public record TimelineKeyframe(long tick, String channel, String value) {
    public TimelineKeyframe {
        if (tick < 0) throw new IllegalArgumentException("keyframe tick must be >= 0");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(value, "value");
        if (channel.isBlank()) throw new IllegalArgumentException("channel cannot be blank");
    }
}
