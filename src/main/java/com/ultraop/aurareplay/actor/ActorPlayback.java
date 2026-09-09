package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ActorPlayback {
    private final ActorDefinition actor;
    private final PlaybackCursor cursor = new PlaybackCursor();
    private long elapsedTicks;

    public ActorPlayback(ActorDefinition actor) {
        this.actor = Objects.requireNonNull(actor);
        reset();
    }

    public ActorDefinition actor() { return actor; }
    public PlaybackCursor cursor() { return cursor; }
    public long elapsedTicks() { return elapsedTicks; }

    public void tick() {
        elapsedTicks++;
        if (actor.frozen() || elapsedTicks <= actor.startDelayTicks()) return;
        cursor.advance(1.0d, actor.playbackSpeed(), actor.reverse(), actor.recording().durationTicks(), actor.loop());
    }

    /** Positions this actor from a shared scene-time clock without mutating the scene clock. */
    public void setScenePosition(double sceneTick) {
        if (!Double.isFinite(sceneTick)) return;
        double local = sceneTick - actor.startDelayTicks();
        if (local < 0.0d) local = 0.0d;
        double duration = Math.max(0.0d, actor.recording().durationTicks());
        double position = local * actor.playbackSpeed();
        if (actor.reverse() && duration > 0.0d) position = duration - 1.0d - position;
        if (actor.loop() && duration > 0.0d) {
            position %= duration;
            if (position < 0.0d) position += duration;
        }
        cursor.setPosition(position);
        elapsedTicks = Math.max(elapsedTicks, (long) Math.floor(Math.max(0.0d, sceneTick)));
    }

    public void reset() {
        elapsedTicks = 0L;
        if (actor.reverse() && actor.recording().durationTicks() > 0) {
            cursor.setPosition(actor.recording().durationTicks() - 1.0d);
        } else {
            cursor.reset();
        }
    }

    public EntitySnapshot sample() {
        ActorSample sample = sampleState();
        return sample == null ? null : sample.source();
    }

    public ActorSample sampleState() {
        return sampleAt(cursor.position());
    }

    /** Samples the immutable recording at an explicit local timeline position. */
    public ActorSample sampleAt(double position) {
        List<TickSnapshot> frames = actor.recording().frames();
        if (frames.isEmpty()) return null;

        position = Math.max(0.0d, Math.min(position, frames.size() - 1.0d));
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = Math.min(frames.size() - 1, lowerIndex + 1);
        double alpha = position - lowerIndex;

        EntitySnapshot lower = findSource(frames.get(lowerIndex));
        EntitySnapshot upper = findSource(frames.get(upperIndex));
        if (lower == null) lower = upper;
        if (upper == null) upper = lower;
        if (lower == null) return null;

        EntitySnapshot sampled = interpolate(lower, upper, alpha);
        ActorTransform base = actor.transform();
        ActorTransform transformed = new ActorTransform(
                sampled.x() + base.x(), sampled.y() + base.y(), sampled.z() + base.z(),
                normalizeDegrees(sampled.yaw() + base.yaw()),
                normalizeDegrees(sampled.pitch() + base.pitch()),
                base.roll(), base.scale()
        );
        return new ActorSample(sampled, transformed, position);
    }

    private EntitySnapshot findSource(TickSnapshot frame) {
        UUID sourceUuid = actor.sourceEntityUuid();
        Integer sourceId = actor.sourceEntityId();
        if (sourceUuid == null && sourceId == null) return frame.entities().isEmpty() ? null : frame.entities().get(0);
        for (EntitySnapshot entity : frame.entities()) {
            if (sourceUuid != null && sourceUuid.equals(entity.uuid())) return entity;
            if (sourceId != null && sourceId.intValue() == entity.entityId()) return entity;
        }
        return null;
    }

    private EntitySnapshot interpolate(EntitySnapshot a, EntitySnapshot b, double alpha) {
        if (a == b || alpha <= 0.0d) return a;
        if (alpha >= 1.0d) return b;
        float yaw = interpolateAngle(a.yaw(), b.yaw(), alpha);
        float pitch = (float) lerp(a.pitch(), b.pitch(), alpha);
        return new EntitySnapshot(
                a.entityId(), a.uuid(), a.type(),
                lerp(a.x(), b.x(), alpha), lerp(a.y(), b.y(), alpha), lerp(a.z(), b.z(), alpha),
                yaw, pitch,
                lerp(a.velocityX(), b.velocityX(), alpha), lerp(a.velocityY(), b.velocityY(), alpha), lerp(a.velocityZ(), b.velocityZ(), alpha),
                alpha < 0.5d ? a.flags() : b.flags(),
                alpha < 0.5d ? a.equipment() : b.equipment(),
                alpha < 0.5d ? a.identity() : b.identity(),
                a.exists() && b.exists()
        );
    }

    private static double lerp(double a, double b, double alpha) { return a + (b - a) * alpha; }

    private static float interpolateAngle(float a, float b, double alpha) {
        float delta = normalizeDegrees(b - a);
        return normalizeDegrees((float) (a + delta * alpha));
    }

    private static float normalizeDegrees(float value) {
        float normalized = value % 360.0f;
        if (normalized > 180.0f) normalized -= 360.0f;
        if (normalized <= -180.0f) normalized += 360.0f;
        return normalized;
    }
}
