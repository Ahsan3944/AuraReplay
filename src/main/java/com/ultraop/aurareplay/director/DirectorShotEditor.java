package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;

import java.util.Objects;

/** Mutable editing facade for a single director shot. */
public final class DirectorShotEditor {
    private final DirectorPlan plan;
    private DirectorShot selected;

    public DirectorShotEditor(DirectorPlan plan) { this.plan = Objects.requireNonNull(plan, "plan"); }
    public DirectorShot selected() { return selected; }
    public boolean select(String id) { selected = plan.get(id).orElse(null); return selected != null; }
    public boolean deleteSelected() { if (selected == null) return false; boolean removed = plan.remove(selected.id()); selected = null; return removed; }

    public boolean cycleType() {
        if (selected == null) return false;
        DirectorShot.Type[] values = DirectorShot.Type.values();
        DirectorShot.Type next = values[(selected.type().ordinal() + 1) % values.length];
        return replace(new DirectorShot(selected.id(), selected.startTick(), selected.endTick(), selected.cameraId(), selected.targetActorId(), next, selected.transition(), selected.path()));
    }

    public boolean cycleTransition() {
        if (selected == null) return false;
        DirectorShot.Transition[] values = DirectorShot.Transition.values();
        DirectorShot.Transition next = values[(selected.transition().ordinal() + 1) % values.length];
        return replace(new DirectorShot(selected.id(), selected.startTick(), selected.endTick(), selected.cameraId(), selected.targetActorId(), selected.type(), next, selected.path()));
    }

    public boolean setRange(long start, long end) {
        if (selected == null) return false;
        return replace(new DirectorShot(selected.id(), start, end, selected.cameraId(), selected.targetActorId(), selected.type(), selected.transition(), selected.path()));
    }

    public boolean setCamera(String cameraId) {
        if (selected == null) return false;
        return replace(new DirectorShot(selected.id(), selected.startTick(), selected.endTick(), cameraId, selected.targetActorId(), selected.type(), selected.transition(), selected.path()));
    }

    public boolean setTarget(String actorId) {
        if (selected == null) return false;
        return replace(new DirectorShot(selected.id(), selected.startTick(), selected.endTick(), selected.cameraId(), actorId, selected.type(), selected.transition(), selected.path()));
    }

    public boolean capturePathPoint(long tick, CameraTransform transform) {
        if (selected == null) return false;
        selected.path().setPoint(tick, transform);
        return true;
    }

    private boolean replace(DirectorShot replacement) {
        plan.remove(selected.id());
        try { plan.add(replacement); selected = replacement; return true; }
        catch (RuntimeException ex) { plan.add(selected); return false; }
    }
}
