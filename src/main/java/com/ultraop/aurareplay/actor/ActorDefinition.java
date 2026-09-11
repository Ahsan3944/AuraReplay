package com.ultraop.aurareplay.actor;

import com.ultraop.aurareplay.motion.MotionStack;
import com.ultraop.aurareplay.recording.Recording;

import java.util.Objects;
import java.util.UUID;

public final class ActorDefinition {
    private final ActorId id;
    private final Recording recording;
    private final UUID sourceEntityUuid;
    private final Integer sourceEntityId;
    private final MotionStack motionStack = new MotionStack();
    private final ActorAppearance appearance = new ActorAppearance();
    private ActorActionTimeline actionTimeline = new ActorActionTimeline(java.util.List.of());
    private String name;
    private String namePrefix = "";
    private String nameSuffix = "";
    private double nameHeightOffset;
    private boolean nameVisible;
    private boolean visible = true;
    private boolean frozen;
    private double playbackSpeed = 1.0d;
    private long startDelayTicks;
    private boolean loop;
    private boolean reverse;
    private ActorTransform transform;

    public ActorDefinition(ActorId id, Recording recording, ActorTransform transform) { this(id, recording, transform, null, null); }

    public ActorDefinition(ActorId id, Recording recording, ActorTransform transform,
                           UUID sourceEntityUuid, Integer sourceEntityId) {
        this.id = Objects.requireNonNull(id); this.recording = Objects.requireNonNull(recording);
        this.transform = Objects.requireNonNull(transform); this.sourceEntityUuid = sourceEntityUuid; this.sourceEntityId = sourceEntityId;
        this.name = recording.name(); this.nameVisible = true;
    }
    public ActorId id() { return id; }
    public Recording recording() { return recording; }
    public UUID sourceEntityUuid() { return sourceEntityUuid; }
    public Integer sourceEntityId() { return sourceEntityId; }
    public MotionStack motionStack() { return motionStack; }
    public ActorAppearance appearance() { return appearance; }
    public ActorActionTimeline actionTimeline() { return actionTimeline; }
    public String name() { return name; }
    public String namePrefix() { return namePrefix; }
    public String nameSuffix() { return nameSuffix; }
    public double nameHeightOffset() { return nameHeightOffset; }
    public boolean nameVisible() { return nameVisible; }
    public boolean visible() { return visible; }
    public boolean frozen() { return frozen; }
    public double playbackSpeed() { return playbackSpeed; }
    public long startDelayTicks() { return startDelayTicks; }
    public boolean loop() { return loop; }
    public boolean reverse() { return reverse; }
    public ActorTransform transform() { return transform; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }
    public void setNamePrefix(String prefix) { this.namePrefix = Objects.requireNonNull(prefix); }
    public void setNameSuffix(String suffix) { this.nameSuffix = Objects.requireNonNull(suffix); }
    public void setNameHeightOffset(double offset) { this.nameHeightOffset = offset; }
    public void setNameVisible(boolean visible) { this.nameVisible = visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
    public void setPlaybackSpeed(double speed) { if (speed <= 0.0d) throw new IllegalArgumentException("playback speed must be > 0"); this.playbackSpeed = speed; }
    public void setStartDelayTicks(long ticks) { this.startDelayTicks = Math.max(0, ticks); }
    public void setLoop(boolean loop) { this.loop = loop; }
    public void setReverse(boolean reverse) { this.reverse = reverse; }
    public void setTransform(ActorTransform transform) { this.transform = Objects.requireNonNull(transform); }
    public void setActionTimeline(ActorActionTimeline timeline) { this.actionTimeline = Objects.requireNonNull(timeline); }
}
