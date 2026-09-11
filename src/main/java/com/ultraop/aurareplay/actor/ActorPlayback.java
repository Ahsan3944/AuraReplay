package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.motion.MotionEvaluator;
import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic actor playback with sub-tick sampling, source timing and motion layers. */
public final class ActorPlayback {
    private final ActorDefinition actor;
    private final ActorPlaybackSource source;
    private final MotionEvaluator motionEvaluator = new MotionEvaluator();
    private final PlaybackCursor cursor = new PlaybackCursor();
    private final ActorActionPlayback actionPlayback;
    private long elapsedTicks;
    private double lastActionPosition = Double.NaN;
    private boolean actionDirectionReverse;

    public ActorPlayback(ActorDefinition actor) { this(actor, new MemoryActorPlaybackSource(actor.recording().frames())); }

    public ActorPlayback(ActorDefinition actor, ActorPlaybackSource source) {
        this.actor = Objects.requireNonNull(actor);
        this.source = Objects.requireNonNull(source);
        this.actionPlayback = new ActorActionPlayback(actor.actionTimeline());
        reset();
    }

    public ActorDefinition actor() { return actor; }
    public ActorPlaybackSource source() { return source; }
    public PlaybackCursor cursor() { return cursor; }
    public long elapsedTicks() { return elapsedTicks; }

    /** Advances actor motion by one server tick and returns actions crossed by the new position. */
    public List<ActorActionEvent> tickActions() {
        tick();
        return actionsAt(cursor.position());
    }

    public void tick() {
        elapsedTicks++;
        if (actor.frozen() || elapsedTicks <= actor.startDelayTicks()) return;
        cursor.advance(1.0d, actor.playbackSpeed(), actor.reverse(), playbackDurationTicks(), actor.loop());
    }

    /** Maps scene time to actor-local source time, preserving fractional ticks. */
    public void setScenePosition(double sceneTick) {
        if (!Double.isFinite(sceneTick)) return;
        double previous = cursor.position();
        double local = Math.max(0.0d, sceneTick - actor.startDelayTicks());
        double position = local * actor.playbackSpeed();
        double duration = playbackDuration();
        if (actor.reverse() && duration > 0.0d) position = Math.max(0.0d, duration - 1.0d - position);
        cursor.seek(position);
        if (actor.loop() && duration > 0.0d) cursor.advance(0.0d, 1.0d, false, playbackDurationTicks(), true);
        elapsedTicks = Math.max(elapsedTicks, (long) Math.floor(Math.max(0.0d, sceneTick)));
        if (Double.isFinite(lastActionPosition) && Math.abs(position - previous) > 1.0e-9d) {
            resetActionCursorForSeek(position);
        }
    }

    /** Returns actions crossed while moving to the requested actor-local source position. */
    public List<ActorActionEvent> actionsAt(double position) {
        if (!Double.isFinite(position)) return List.of();
        long tick = Math.max(0L, (long) Math.floor(position));
        boolean reverse = actor.reverse();
        if (reverse != actionDirectionReverse) {
            resetActionCursorForDirection(position, reverse);
        } else if (!Double.isNaN(lastActionPosition)) {
            if (!reverse && position < lastActionPosition) resetActionCursorForDirection(position, false);
            if (reverse && position > lastActionPosition) resetActionCursorForDirection(position, true);
        } else {
            resetActionCursorForDirection(position, reverse);
        }

        List<ActorActionEvent> events = reverse
                ? actionPlayback.advanceReverse(tick)
                : actionPlayback.advance(tick);
        lastActionPosition = position;
        actionDirectionReverse = reverse;
        return events;
    }

    public void reset() {
        elapsedTicks = 0L;
        actionPlayback.reset();
        lastActionPosition = Double.NaN;
        actionDirectionReverse = actor.reverse();
        if (actor.reverse() && playbackDuration() > 0.0d) {
            cursor.seek(playbackDuration() - 1.0d);
            actionPlayback.seek((long) Math.ceil(playbackDuration()));
        } else {
            cursor.reset();
        }
    }

    private void resetActionCursorForSeek(double position) {
        resetActionCursorForDirection(position, actor.reverse());
        lastActionPosition = Double.NaN;
    }

    private void resetActionCursorForDirection(double position, boolean reverse) {
        long tick = Math.max(0L, (long) Math.floor(position));
        if (reverse) {
            actionPlayback.seek(Math.max(tick, (long) Math.ceil(playbackDuration())));
        } else {
            actionPlayback.reset();
        }
        lastActionPosition = Double.NaN;
        actionDirectionReverse = reverse;
    }

    public EntitySnapshot sample() { ActorSample sample = sampleState(); return sample == null ? null : sample.source(); }
    public ActorSample sampleState() { return sampleAt(cursor.position()); }

    public ActorSample sampleAt(double position) {
        int count = source.frameCount();
        if (count <= 0 || !Double.isFinite(position)) return null;

        MotionEvaluator.Evaluation evaluation = motionEvaluator.evaluate(
                position, count, actor.transform(), actor.motionStack());
        double bounded = evaluation.sourcePosition();
        int lowerIndex = (int) Math.floor(bounded);
        int upperIndex = Math.min(count - 1, lowerIndex + 1);
        double alpha = bounded - lowerIndex;
        TickSnapshot lowerFrame = frame(lowerIndex);
        TickSnapshot upperFrame = frame(upperIndex);
        if (lowerFrame == null || upperFrame == null) return null;
        EntitySnapshot lower = findSource(lowerFrame);
        EntitySnapshot upper = findSource(upperFrame);
        if (lower == null) lower = upper;
        if (upper == null) upper = lower;
        if (lower == null) return null;

        EntitySnapshot sampled = interpolate(lower, upper, alpha);
        ActorTransform base = evaluation.transform();
        ActorTransform transformed = new ActorTransform(
                sampled.x() + base.x(), sampled.y() + base.y(), sampled.z() + base.z(),
                normalizeDegrees(sampled.yaw() + base.yaw()), normalizeDegrees(sampled.pitch() + base.pitch()),
                base.roll(), base.scale()
        );
        return new ActorSample(sampled, transformed, bounded);
    }

    private double playbackDuration() { return Math.max(0.0d, source.frameCount()); }
    private long playbackDurationTicks() { return Math.max(0L, source.frameCount()); }

    private TickSnapshot frame(int index) {
        try { return source.frame(index); }
        catch (IOException | RuntimeException ignored) { return null; }
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
        return new EntitySnapshot(a.entityId(), a.uuid(), a.type(),
                lerp(a.x(), b.x(), alpha), lerp(a.y(), b.y(), alpha), lerp(a.z(), b.z(), alpha),
                interpolateAngle(a.yaw(), b.yaw(), alpha), (float) lerp(a.pitch(), b.pitch(), alpha),
                lerp(a.velocityX(), b.velocityX(), alpha), lerp(a.velocityY(), b.velocityY(), alpha), lerp(a.velocityZ(), b.velocityZ(), alpha),
                alpha < 0.5d ? a.flags() : b.flags(), alpha < 0.5d ? a.equipment() : b.equipment(),
                alpha < 0.5d ? a.identity() : b.identity(), a.exists() && b.exists());
    }

    private static double lerp(double a, double b, double alpha) { return a + (b - a) * alpha; }
    private static float interpolateAngle(float a, float b, double alpha) { return normalizeDegrees((float) (a + normalizeDegrees(b - a) * alpha)); }
    private static float normalizeDegrees(float value) { float n = value % 360.0f; if (n > 180.0f) n -= 360.0f; if (n <= -180.0f) n += 360.0f; return n; }

    private static final class MemoryActorPlaybackSource implements ActorPlaybackSource {
        private final List<TickSnapshot> frames;
        private MemoryActorPlaybackSource(List<TickSnapshot> frames) { this.frames = List.copyOf(frames); }
        @Override public int frameCount() { return frames.size(); }
        @Override public TickSnapshot frame(int index) { return frames.get(index); }
    }
}
