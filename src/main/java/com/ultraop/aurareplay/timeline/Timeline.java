package com.ultraop.aurareplay.timeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Editable, non-destructive timeline owned by a scene. */
public final class Timeline {
    private long durationTicks;
    private long inPoint;
    private long outPoint;
    private double playbackSpeed = 1.0d;
    private boolean loop;
    private boolean reverse;
    private final List<TimelineMarker> markers = new ArrayList<>();
    private final List<TimelineKeyframe> keyframes = new ArrayList<>();

    public Timeline() { this(0L); }

    public Timeline(long durationTicks) {
        setDurationTicks(durationTicks);
    }

    public long durationTicks() { return durationTicks; }
    public long inPoint() { return inPoint; }
    public long outPoint() { return outPoint; }
    public double playbackSpeed() { return playbackSpeed; }
    public boolean loop() { return loop; }
    public boolean reverse() { return reverse; }
    public List<TimelineMarker> markers() { return List.copyOf(markers); }
    public List<TimelineKeyframe> keyframes() { return List.copyOf(keyframes); }

    public void setDurationTicks(long durationTicks) {
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must be >= 0");
        this.durationTicks = durationTicks;
        this.inPoint = Math.min(inPoint, durationTicks);
        this.outPoint = Math.min(outPoint == 0 ? durationTicks : outPoint, durationTicks);
        if (outPoint < inPoint) outPoint = inPoint;
    }

    public void setRange(long inPoint, long outPoint) {
        if (inPoint < 0 || outPoint < inPoint || outPoint > durationTicks)
            throw new IllegalArgumentException("invalid timeline range");
        this.inPoint = inPoint;
        this.outPoint = outPoint;
    }

    public void setPlaybackSpeed(double playbackSpeed) {
        if (playbackSpeed <= 0.0d || !Double.isFinite(playbackSpeed))
            throw new IllegalArgumentException("playbackSpeed must be finite and > 0");
        this.playbackSpeed = playbackSpeed;
    }

    public void setLoop(boolean loop) { this.loop = loop; }
    public void setReverse(boolean reverse) { this.reverse = reverse; }

    public void addMarker(TimelineMarker marker) {
        markers.removeIf(existing -> existing.tick() == marker.tick());
        markers.add(Objects.requireNonNull(marker));
        markers.sort(Comparator.comparingLong(TimelineMarker::tick));
    }

    public boolean removeMarker(String id) { return markers.removeIf(marker -> marker.id().equals(id)); }

    public void addKeyframe(TimelineKeyframe keyframe) {
        Objects.requireNonNull(keyframe);
        if (keyframe.tick() > durationTicks) setDurationTicks(keyframe.tick());
        keyframes.removeIf(existing -> existing.tick() == keyframe.tick() && existing.channel().equals(keyframe.channel()));
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingLong(TimelineKeyframe::tick));
    }

    public boolean removeKeyframe(long tick, String channel) {
        return keyframes.removeIf(keyframe -> keyframe.tick() == tick && keyframe.channel().equals(channel));
    }
}
