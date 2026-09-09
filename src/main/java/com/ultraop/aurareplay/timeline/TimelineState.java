package com.ultraop.aurareplay.timeline;

import java.util.List;

/** Immutable snapshot used by timeline history and future project serialization. */
public record TimelineState(
        long durationTicks,
        long inPoint,
        long outPoint,
        double playbackSpeed,
        boolean loop,
        boolean reverse,
        List<TimelineMarker> markers,
        List<TimelineKeyframe> keyframes
) {
    public TimelineState {
        markers = List.copyOf(markers);
        keyframes = List.copyOf(keyframes);
    }

    public static TimelineState capture(Timeline timeline) {
        return new TimelineState(
                timeline.durationTicks(), timeline.inPoint(), timeline.outPoint(),
                timeline.playbackSpeed(), timeline.loop(), timeline.reverse(),
                timeline.markers(), timeline.keyframes());
    }

    public void restore(Timeline timeline) {
        timeline.replaceWith(this);
    }
}
