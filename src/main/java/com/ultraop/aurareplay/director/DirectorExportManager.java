package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.camera.CameraTransform;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Coordinates incremental, streaming and checkpoint-resumable Director exports. */
public final class DirectorExportManager {
    private static final int DEFAULT_FRAMES_PER_TICK = 8;
    private final JavaPlugin plugin;
    @SuppressWarnings("unused") private final DirectorExportWriter writer;
    private final Map<UUID, ExportHandle> active = new ConcurrentHashMap<>();

    public DirectorExportManager(JavaPlugin plugin) { this(plugin, new DirectorExportWriter()); }
    public DirectorExportManager(JavaPlugin plugin, DirectorExportWriter writer) { this.plugin = Objects.requireNonNull(plugin, "plugin"); this.writer = Objects.requireNonNull(writer, "writer"); }

    public boolean start(Player viewer, DirectorExportSpec spec, Function<Double, CameraTransform> sampler, Path output, Consumer<Path> onComplete, Consumer<Throwable> onFailure) {
        return start(viewer, spec, sampler, output, null, onComplete, onFailure);
    }

    public boolean start(Player viewer, DirectorExportSpec spec, Function<Double, CameraTransform> sampler, Path output, DirectorCaptureSink captureSink, Consumer<Path> onComplete, Consumer<Throwable> onFailure) {
        return startInternal(viewer, spec, sampler, output, captureSink, 0L, false, output.resolveSibling(output.getFileName() + ".checkpoint.json"), onComplete, onFailure);
    }

    /** Resumes an interrupted export from its atomic checkpoint and .tmp manifest. */
    public boolean resume(Player viewer, DirectorExportSpec spec, Function<Double, CameraTransform> sampler, Path output, Path checkpoint, Consumer<Path> onComplete, Consumer<Throwable> onFailure) throws IOException {
        return resume(viewer, spec, sampler, output, checkpoint, null, onComplete, onFailure);
    }

    public boolean resume(Player viewer, DirectorExportSpec spec, Function<Double, CameraTransform> sampler, Path output, Path checkpoint, DirectorCaptureSink captureSink, Consumer<Path> onComplete, Consumer<Throwable> onFailure) throws IOException {
        DirectorExportCheckpoint value = new DirectorExportCheckpointStore().load(checkpoint, spec);
        if (value.complete()) throw new IllegalArgumentException("checkpoint is already complete");
        return startInternal(viewer, spec, sampler, output, captureSink, value.nextFrameIndex(), true, checkpoint, onComplete, onFailure);
    }

    private boolean startInternal(Player viewer, DirectorExportSpec spec, Function<Double, CameraTransform> sampler, Path output, DirectorCaptureSink captureSink, long startFrame, boolean resume, Path checkpoint, Consumer<Path> onComplete, Consumer<Throwable> onFailure) {
        Objects.requireNonNull(viewer); Objects.requireNonNull(spec); Objects.requireNonNull(sampler); Objects.requireNonNull(output); Objects.requireNonNull(onComplete); Objects.requireNonNull(onFailure);
        UUID id = viewer.getUniqueId(); if (active.containsKey(id)) return false;
        DirectorExportManifestStreamWriter manifest = new DirectorExportManifestStreamWriter(output);
        if (resume) manifest.resume(spec, startFrame); else manifest.start(spec);
        DirectorExportCheckpointStore checkpoints = new DirectorExportCheckpointStore();
        DirectorCaptureSink sink = captureSink == null ? manifest : new DirectorCaptureFanout(manifest, captureSink);
        DirectorCaptureSession capture = new DirectorCaptureSession(spec, sink);
        if (resume) capture.start();
        Consumer<DirectorFrame> consumer = frame -> { capture.accept(frame); try { checkpoints.save(checkpoint, new DirectorExportCheckpoint(spec, frame.frameIndex() + 1)); } catch (IOException ex) { throw new IllegalStateException("failed to persist export checkpoint", ex); } };
        DirectorExportJob job = new DirectorExportJob(spec, sampler, consumer, false);
        if (resume) job.startAt(startFrame);
        ExportHandle handle = new ExportHandle(job, capture, checkpoint, checkpoints);
        active.put(id, handle);
        handle.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active.containsKey(id)) return;
            job.step(DEFAULT_FRAMES_PER_TICK);
            if (job.state() == DirectorExportJob.State.RUNNING) return;
            if (handle.task != null) handle.task.cancel(); active.remove(id, handle);
            if (job.state() == DirectorExportJob.State.COMPLETED) {
                try { handle.checkpoints.delete(handle.checkpoint); } catch (IOException ignored) { }
                onComplete.accept(output.toAbsolutePath().normalize());
            } else if (job.state() == DirectorExportJob.State.FAILED) { Throwable failure = job.failure(); capture.fail(failure); onFailure.accept(failure); }
        }, 1L, 1L);
        return true;
    }

    public boolean cancel(Player viewer) { ExportHandle h = active.remove(viewer.getUniqueId()); if (h == null) return false; h.job.cancel(); h.capture.cancel(); if (h.task != null) h.task.cancel(); return true; }
    public boolean active(Player viewer) { return active.containsKey(viewer.getUniqueId()); }
    public long progress(Player viewer) { ExportHandle h = active.get(viewer.getUniqueId()); return h == null ? 0L : h.job.capturedFrames(); }
    public long total(Player viewer) { ExportHandle h = active.get(viewer.getUniqueId()); return h == null ? 0L : h.job.spec().frameCount(); }
    public void cancelAll() { active.values().forEach(h -> { h.job.cancel(); h.capture.cancel(); if (h.task != null) h.task.cancel(); }); active.clear(); }

    private static final class ExportHandle {
        private final DirectorExportJob job; private final DirectorCaptureSession capture; private final Path checkpoint; private final DirectorExportCheckpointStore checkpoints; private BukkitTask task;
        private ExportHandle(DirectorExportJob job, DirectorCaptureSession capture, Path checkpoint, DirectorExportCheckpointStore checkpoints) { this.job = job; this.capture = capture; this.checkpoint = checkpoint; this.checkpoints = checkpoints; }
    }
}
