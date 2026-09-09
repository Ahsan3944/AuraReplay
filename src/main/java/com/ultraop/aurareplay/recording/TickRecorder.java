package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.EntitySnapshot;
import com.ultraop.aurareplay.recording.snapshot.EquipmentSnapshot;
import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TickRecorder {

    private final JavaPlugin plugin;
    private final Map<UUID, Entity> tracked = new ConcurrentHashMap<>();
    private final RecordingBuffer buffer = new RecordingBuffer();

    private volatile boolean recording;
    private long tick;
    private int taskId = -1;

    public TickRecorder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (recording) return;
        recording = true;
        tick = 0;
        buffer.start();

        taskId = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::captureTick,
                1L,
                1L
        ).getTaskId();
    }

    public Recording stop(String name) {
        if (!recording) return null;

        recording = false;
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // The async writer normally drains within the next scheduling slice.
        // The returned object is an immutable snapshot of all frames committed so far.
        List<TickSnapshot> frames = buffer.snapshot();
        return new Recording(name, frames.isEmpty() ? 0 : frames.get(frames.size() - 1).tick() + 1, frames);
    }

    public void stop() {
        stop("untitled");
    }

    /** Must execute on the Paper main thread. */
    private void captureTick() {
        if (!recording) return;

        var snapshots = new ArrayList<EntitySnapshot>(tracked.size());
        for (Entity entity : tracked.values()) {
            if (entity.isValid()) {
                snapshots.add(snapshot(entity));
            }
        }

        buffer.append(new TickSnapshot(tick++, List.copyOf(snapshots), List.of()));
    }

    private EntitySnapshot snapshot(Entity entity) {
        var location = entity.getLocation();
        var velocity = entity.getVelocity();
        int flags = 0;

        if (entity instanceof Player player) {
            if (player.isSneaking()) flags |= EntityFlags.SNEAKING;
            if (player.isSwimming()) flags |= EntityFlags.SWIMMING;
            if (player.isGliding()) flags |= EntityFlags.GLIDING;
            if (player.isInvisible()) flags |= EntityFlags.INVISIBLE;
            if (player.isGlowing()) flags |= EntityFlags.GLOWING;
        }
        if (entity.getFireTicks() > 0) flags |= EntityFlags.ON_FIRE;

        return new EntitySnapshot(
                entity.getEntityId(),
                entity.getUniqueId(),
                entity.getType(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                velocity.getX(), velocity.getY(), velocity.getZ(),
                flags,
                EquipmentSnapshot.capture(entity),
                true
        );
    }

    public void track(Entity entity) {
        tracked.put(entity.getUniqueId(), entity);
    }

    public void untrack(Entity entity) {
        tracked.remove(entity.getUniqueId());
    }

    public boolean isRecording() {
        return recording;
    }

    public long currentTick() {
        return tick;
    }

    public void shutdown() {
        stop();
        buffer.shutdown();
    }
}
