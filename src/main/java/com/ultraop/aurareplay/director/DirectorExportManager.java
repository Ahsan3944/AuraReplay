package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Coordinates incremental main-thread sampling, capture delivery, and asynchronous manifest persistence. */
public final class DirectorExportManager {
    private static final int DEFAULT_FRAMES_PER_TICK = 8;

    private final JavaPlugin plugin;
    private final DirectorExportWriter writer;
    private final Map<UUID, ExportHandle> active = new ConcurrentHashMap<>();

    public DirectorExportManager(JavaPlugin plugin) {
        this(plugin, new DirectorExportWriter());
    }

    public DirectorExportManager(JavaPlugin plugin, DirectorExportWriter writer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public boolean start(Player viewer,
                         DirectorExportSpec spec,
                         Function<Double, CameraTransform> sampler,
                         Path output,
                         Consumer<Path> onComplete,
                         Consumer<Throwable> onFailure) {
        return start(viewer, spec, sampler, output, null, onComplete, onFailure);
    }

    /**
     * Starts an export and optionally forwards every sampled frame through a
     * lifecycle-aware capture sink. The sink receives the same deterministic
     * frames that are persisted to the export manifest.
     */
    public boolean start(Player viewer,
                         DirectorExportSpec spec,
                         Function<Double, CameraTransform> sampler,
                         Path output,
                         DirectorCaptureSink captureSink,
                         Consumer<Path> onComplete,
                         Consumer<Throwable> onFailure) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(onComplete, "onComplete");
        Objects.requireNonNull(onFailure, "onFailure");

        UUID id = viewer.getUniqueId();
        if (active.containsKey(id)) return false;

        DirectorCaptureSession capture = captureSink == null ? null : new DirectorCaptureSession(spec, captureSink);
        DirectorExportJob job = new DirectorExportJob(spec, sampler, capture == null ? null : capture::accept);
        ExportHandle handle = new ExportHandle(job, capture);
        active.put(id, handle);
        handle.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active.containsKey(id)) return;
            job.step(DEFAULT_FRAMES_PER_TICK);
            if (job.state() == DirectorExportJob.State.RUNNING) return;
            if (handle.task != null) handle.task.cancel();
            active.remove(id, handle);

            if (job.state() == DirectorExportJob.State.COMPLETED) {
                DirectorExportManifest manifest = new DirectorExportManifest(spec, job.frames());
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Path written = writer.write(output, manifest);
                        Bukkit.getScheduler().runTask(plugin, () -> onComplete.accept(written));
                    } catch (Throwable ex) {
                        Bukkit.getScheduler().runTask(plugin, () -> onFailure.accept(ex));
                    }
                });
            } else if (job.state() == DirectorExportJob.State.FAILED) {
                onFailure.accept(job.failure());
            }
        }, 1L, 1L);
        return true;
    }

    public boolean cancel(Player viewer) {
        ExportHandle handle = active.remove(viewer.getUniqueId());
        if (handle == null) return false;
        handle.job.cancel();
        if (handle.capture != null) handle.capture.cancel();
        if (handle.task != null) handle.task.cancel();
        return true;
    }

    public boolean active(Player viewer) { return active.containsKey(viewer.getUniqueId()); }

    public long progress(Player viewer) {
        ExportHandle handle = active.get(viewer.getUniqueId());
        return handle == null ? 0L : handle.job.capturedFrames();
    }

    public long total(Player viewer) {
        ExportHandle handle = active.get(viewer.getUniqueId());
        return handle == null ? 0L : handle.job.spec().frameCount();
    }

    public void cancelAll() {
        active.values().forEach(handle -> {
            handle.job.cancel();
            if (handle.capture != null) handle.capture.cancel();
            if (handle.task != null) handle.task.cancel();
        });
        active.clear();
    }

    private static final class ExportHandle {
        private final DirectorExportJob job;
        private final DirectorCaptureSession capture;
        private BukkitTask task;

        private ExportHandle(DirectorExportJob job, DirectorCaptureSession capture) {
            this.job = job;
            this.capture = capture;
        }
    }
}
