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
    public DirectorExportManager(JavaPlugin plugin, DirectorExportWriter writer) { this.plugin = Objects.requireNonNull(plugin); this.writer = Objects.requireNonNull(writer); }
    public boolean start(Player v, DirectorExportSpec s, Function<Double, CameraTransform> sampler, Path out, Consumer<Path> ok, Consumer<Throwable> fail) { return start(v,s,sampler,out,null,ok,fail); }
    public boolean start(Player v, DirectorExportSpec s, Function<Double, CameraTransform> sampler, Path out, DirectorCaptureSink external, Consumer<Path> ok, Consumer<Throwable> fail) { return startInternal(v,s,sampler,out,external,0,false,out.resolveSibling(out.getFileName()+".checkpoint.json"),ok,fail); }
    public boolean resume(Player v, DirectorExportSpec s, Function<Double, CameraTransform> sampler, Path out, Path checkpoint, Consumer<Path> ok, Consumer<Throwable> fail) throws IOException { return resume(v,s,sampler,out,checkpoint,null,ok,fail); }
    public boolean resume(Player v, DirectorExportSpec s, Function<Double, CameraTransform> sampler, Path out, Path checkpoint, DirectorCaptureSink external, Consumer<Path> ok, Consumer<Throwable> fail) throws IOException {
        DirectorExportCheckpoint cp = new DirectorExportCheckpointStore().load(checkpoint,s);
        if(cp.complete()) throw new IllegalArgumentException("checkpoint is already complete");
        return startInternal(v,s,sampler,out,external,cp.nextFrameIndex(),true,checkpoint,ok,fail);
    }
    private boolean startInternal(Player v, DirectorExportSpec s, Function<Double, CameraTransform> sampler, Path out, DirectorCaptureSink external, long start, boolean resume, Path checkpoint, Consumer<Path> ok, Consumer<Throwable> fail) {
        Objects.requireNonNull(v); Objects.requireNonNull(s); Objects.requireNonNull(sampler); Objects.requireNonNull(out); Objects.requireNonNull(ok); Objects.requireNonNull(fail);
        UUID id=v.getUniqueId(); if(active.containsKey(id)) return false;
        DirectorExportManifestStreamWriter manifest=new DirectorExportManifestStreamWriter(out);
        DirectorCaptureSink sink=external==null?manifest:new DirectorCaptureFanout(manifest,external);
        DirectorCaptureSession capture=new DirectorCaptureSession(s,sink);
        if(resume){ manifest.resume(s,start); if(external!=null) external.start(s); capture.startAt(start,false); } else capture.start();
        DirectorExportCheckpointStore cps=new DirectorExportCheckpointStore();
        Consumer<DirectorFrame> consumer=frame->{ capture.accept(frame); try{ cps.save(checkpoint,new DirectorExportCheckpoint(s,frame.frameIndex()+1)); }catch(IOException ex){throw new IllegalStateException("failed to persist export checkpoint",ex);} };
        DirectorExportJob job=new DirectorExportJob(s,sampler,consumer,false); if(resume) job.startAt(start);
        ExportHandle h=new ExportHandle(job,capture,checkpoint,cps); active.put(id,h);
        h.task=Bukkit.getScheduler().runTaskTimer(plugin,()->{ if(!active.containsKey(id))return; job.step(DEFAULT_FRAMES_PER_TICK); if(job.state()==DirectorExportJob.State.RUNNING)return; if(h.task!=null)h.task.cancel();active.remove(id,h);
            if(job.state()==DirectorExportJob.State.COMPLETED){try{h.checkpoints.delete(h.checkpoint);}catch(IOException ignored){} ok.accept(out.toAbsolutePath().normalize());}
            else if(job.state()==DirectorExportJob.State.FAILED){Throwable x=job.failure();capture.fail(x);fail.accept(x);}
        },1L,1L); return true;
    }
    public boolean cancel(Player v){ExportHandle h=active.remove(v.getUniqueId());if(h==null)return false;h.job.cancel();h.capture.cancel();if(h.task!=null)h.task.cancel();return true;}
    public boolean active(Player v){return active.containsKey(v.getUniqueId());}
    public long progress(Player v){ExportHandle h=active.get(v.getUniqueId());return h==null?0:h.job.capturedFrames();}
    public long total(Player v){ExportHandle h=active.get(v.getUniqueId());return h==null?0:h.job.spec().frameCount();}
    public void cancelAll(){active.values().forEach(h->{h.job.cancel();h.capture.cancel();if(h.task!=null)h.task.cancel();});active.clear();}
    private static final class ExportHandle{final DirectorExportJob job;final DirectorCaptureSession capture;final Path checkpoint;final DirectorExportCheckpointStore checkpoints;BukkitTask task;ExportHandle(DirectorExportJob j,DirectorCaptureSession c,Path p,DirectorExportCheckpointStore s){job=j;capture=c;checkpoint=p;checkpoints=s;}}
}
