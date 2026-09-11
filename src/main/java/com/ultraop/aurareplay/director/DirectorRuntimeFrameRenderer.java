package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorActionEvent;
import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.actor.ActorVisualState;
import com.ultraop.aurareplay.actor.ActorVisualStateDiff;
import com.ultraop.aurareplay.actor.VirtualActorBackend;
import com.ultraop.aurareplay.camera.CameraController;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Applies complete deterministic Director frames to viewer-local Minecraft state. */
public final class DirectorRuntimeFrameRenderer {
    public record RenderResult(
            long frameIndex,
            boolean cameraApplied,
            int renderedActors,
            int removedActors,
            Map<ActorId, Throwable> failures
    ) {
        public RenderResult {
            if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
            failures = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(failures, "failures")));
            if (renderedActors < 0 || removedActors < 0) throw new IllegalArgumentException("counts must be >= 0");
        }

        public boolean successful() { return cameraApplied && failures.isEmpty(); }
    }

    private final CameraController camera;
    private final ActorManager actorManager;
    private final VirtualActorBackend actorBackend;
    private final Map<UUID, Set<ActorId>> renderedActors = new HashMap<>();
    private final Map<UUID, Map<ActorId, ActorVisualState>> visualStates = new HashMap<>();
    private final Map<UUID, Map<ActorId, Long>> lastActionSceneTicks = new HashMap<>();

    public DirectorRuntimeFrameRenderer(CameraController camera, ActorManager actorManager, VirtualActorBackend actorBackend) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.actorManager = Objects.requireNonNull(actorManager, "actorManager");
        this.actorBackend = Objects.requireNonNull(actorBackend, "actorBackend");
    }

    public boolean render(Player viewer, DirectorRenderFrame frame) {
        return renderFrame(viewer, frame).cameraApplied();
    }

    /**
     * Renders a frame while isolating actor failures. One broken actor cannot prevent
     * the remaining actors from being updated or cleaned up. Transform updates remain
     * frame-accurate, visual state packets are emitted only when their state changes,
     * and sparse action events are emitted once per scene tick.
     */
    public RenderResult renderFrame(Player viewer, DirectorRenderFrame frame) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(frame, "frame");
        if (!camera.overrideTransform(viewer, frame.camera())) {
            return new RenderResult(frame.frameIndex(), false, 0, 0, Map.of());
        }

        UUID viewerId = viewer.getUniqueId();
        Set<ActorId> current = new LinkedHashSet<>();
        Set<ActorId> previous = renderedActors.getOrDefault(viewerId, Set.of());
        Map<ActorId, ActorVisualState> previousStates = visualStates.getOrDefault(viewerId, Map.of());
        Map<ActorId, ActorVisualState> currentStates = new LinkedHashMap<>();
        Map<ActorId, Long> actionCursor = lastActionSceneTicks.computeIfAbsent(viewerId, ignored -> new HashMap<>());
        Map<ActorId, Throwable> failures = new LinkedHashMap<>();
        long sceneTick = Math.max(0L, (long) Math.floor(frame.sceneTick()));
        int rendered = 0;
        int removed = 0;

        for (Map.Entry<ActorId, ActorSample> entry : frame.actors().entrySet()) {
            ActorId id = entry.getKey();
            ActorDefinition actor = actorManager.get(id).orElse(null);
            ActorSample sample = entry.getValue();
            if (actor == null || sample == null) continue;

            ActorVisualState state = ActorVisualState.from(sample);
            if (!actor.visible() || !state.exists()) {
                try {
                    actorBackend.destroy(actor, viewer);
                    removed++;
                } catch (Throwable error) {
                    failures.put(id, error);
                }
                continue;
            }

            try {
                boolean spawned = !previous.contains(id);
                if (spawned) {
                    actorBackend.spawn(actor, viewer);
                    actorBackend.updateIdentity(actor, viewer);
                }
                actorBackend.update(actor, viewer, sample.transform());

                Set<ActorVisualStateDiff.Change> changes = ActorVisualStateDiff.between(previousStates.get(id), state);
                if (spawned || changes.contains(ActorVisualStateDiff.Change.FLAGS)
                        || changes.contains(ActorVisualStateDiff.Change.EQUIPMENT)
                        || changes.contains(ActorVisualStateDiff.Change.IDENTITY)) {
                    actorBackend.updateEquipment(actor, viewer, sample.source());
                }

                Long lastSceneTick = actionCursor.get(id);
                if (lastSceneTick == null || sceneTick < lastSceneTick) {
                    actionCursor.remove(id);
                    lastSceneTick = null;
                }
                if (lastSceneTick == null || sceneTick > lastSceneTick) {
                    for (ActorActionEvent event : frame.actions().getOrDefault(id, java.util.List.of())) {
                        actorBackend.playAction(actor, viewer, event.action());
                    }
                    if (!frame.actions().getOrDefault(id, java.util.List.of()).isEmpty()) {
                        actionCursor.put(id, sceneTick);
                    }
                }

                current.add(id);
                currentStates.put(id, state);
                rendered++;
            } catch (Throwable error) {
                failures.put(id, error);
                try {
                    actorBackend.destroy(actor, viewer);
                } catch (Throwable cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
        }

        for (ActorId id : previous) {
            if (current.contains(id)) continue;
            ActorDefinition actor = actorManager.get(id).orElse(null);
            if (actor == null) continue;
            try {
                actorBackend.destroy(actor, viewer);
                removed++;
            } catch (Throwable error) {
                failures.putIfAbsent(id, error);
            }
            actionCursor.remove(id);
        }

        if (current.isEmpty()) {
            renderedActors.remove(viewerId);
            visualStates.remove(viewerId);
            lastActionSceneTicks.remove(viewerId);
        } else {
            renderedActors.put(viewerId, current);
            visualStates.put(viewerId, currentStates);
        }
        return new RenderResult(frame.frameIndex(), true, rendered, removed, failures);
    }

    public void stop(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        UUID viewerId = viewer.getUniqueId();
        Set<ActorId> previous = renderedActors.remove(viewerId);
        visualStates.remove(viewerId);
        lastActionSceneTicks.remove(viewerId);
        if (previous == null) return;
        for (ActorId id : previous) {
            actorManager.get(id).ifPresent(actor -> {
                try {
                    actorBackend.destroy(actor, viewer);
                } catch (Throwable ignored) {
                    // Cleanup is best-effort; one actor must not block the rest.
                }
            });
        }
    }

    public void clear() {
        renderedActors.clear();
        visualStates.clear();
        lastActionSceneTicks.clear();
    }

    public int renderedActorCount(Player viewer) {
        return renderedActors.getOrDefault(viewer.getUniqueId(), Set.of()).size();
    }

    public CameraController camera() { return camera; }
    public ActorManager actorManager() { return actorManager; }
    public VirtualActorBackend actorBackend() { return actorBackend; }
}
