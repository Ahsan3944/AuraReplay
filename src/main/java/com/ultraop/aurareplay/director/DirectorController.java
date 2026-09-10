package com.ultraop.aurareplay.director;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorSample;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraManager;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates scene director plans with viewer-local cinematic camera and actor rendering. */
public final class DirectorController {
    private final CameraManager cameras;
    private final CameraController cameraController;
    private final ActorPlaybackController actorPlayback;
    private final DirectorRuntimeFrameRenderer runtimeRenderer;
    private final Map<String, DirectorPlan> plans = new ConcurrentHashMap<>();
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<UUID, PlaybackState> playback = new ConcurrentHashMap<>();

    public DirectorController(CameraManager cameras, CameraController cameraController, ActorPlaybackController actorPlayback) {
        this(cameras, cameraController, actorPlayback, null);
    }

    public DirectorController(CameraManager cameras, CameraController cameraController, ActorPlaybackController actorPlayback, DirectorRuntimeFrameRenderer runtimeRenderer) {
        this.cameras = Objects.requireNonNull(cameras, "cameras");
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController");
        this.actorPlayback = Objects.requireNonNull(actorPlayback, "actorPlayback");
        this.runtimeRenderer = runtimeRenderer;
    }

    public DirectorPlan plan(Scene scene) { Objects.requireNonNull(scene, "scene"); scenes.put(scene.name(), scene); return plans.computeIfAbsent(scene.name(), ignored -> new DirectorPlan()); }
    public void setPlan(Scene scene, DirectorPlan plan) { Objects.requireNonNull(scene, "scene"); scenes.put(scene.name(), scene); plans.put(scene.name(), Objects.requireNonNull(plan, "plan")); }

    public boolean start(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(scene, "scene");
        if (!DirectorPlaybackTimeline.canStart(plan(scene), tick)) return false;
        PlaybackState state = new PlaybackState(scene.name(), tick, true); playback.put(viewer.getUniqueId(), state);
        if (!renderAt(viewer, scene, tick)) { playback.remove(viewer.getUniqueId()); cameraController.stop(viewer); return false; }
        return true;
    }

    public boolean play(Player viewer) { PlaybackState state=playback.get(viewer.getUniqueId()); if(state==null)return false; state.playing=true; return true; }
    public boolean pause(Player viewer) { PlaybackState state=playback.get(viewer.getUniqueId()); if(state==null)return false; state.playing=false; return true; }

    public boolean seek(Player viewer, double tick) {
        PlaybackState state=playback.get(viewer.getUniqueId()); if(state==null)return false;
        Scene scene=scenes.get(state.sceneName); DirectorPlan p=scene==null?null:plan(scene);
        if(!DirectorPlaybackTimeline.canSeek(p,tick)||!renderAt(viewer,scene,tick))return false;
        state.tick=tick; return true;
    }

    public void tick(Player viewer) {
        PlaybackState state=playback.get(viewer.getUniqueId()); if(state==null||!state.playing)return;
        DirectorPlan p=plans.get(state.sceneName); Scene scene=scenes.get(state.sceneName); var next=DirectorPlaybackTimeline.nextTick(p,state.tick);
        if(scene==null||next.isEmpty()){stop(viewer);return;}
        double tick=next.getAsDouble(); if(!renderAt(viewer,scene,tick)){stop(viewer);return;} state.tick=tick;
    }

    /** Evaluates the active shot and applies the complete Director runtime frame when the renderer is wired. */
    public boolean renderAt(Player viewer, Scene scene, double tick) {
        Objects.requireNonNull(viewer,"viewer"); Objects.requireNonNull(scene,"scene");
        DirectorPlan p=plan(scene); DirectorShot shot=p.at(tick).orElse(null); if(shot==null)return false;
        CameraDefinition camera=cameras.get(shot.cameraId()).orElse(null); if(camera==null)return false;
        double localTick=Math.max(0.0,tick-shot.startTick()); CameraDefinition activeCamera=cameraController.currentCamera(viewer);
        if(activeCamera==null||!activeCamera.id().equals(camera.id()))cameraController.start(viewer,camera,localTick);else cameraController.seek(viewer,localTick);
        CameraTransform current=targetTransform(viewer,shot,pathTransform(shot,camera.sample(localTick),localTick));
        DirectorShot previous=p.previousBefore(shot.startTick()).orElse(null); CameraTransform previousTransform=previous==null?null:previousTransform(viewer,previous);
        DirectorTransitionEvaluator.Result transition=DirectorTransitionEvaluator.evaluate(shot,previous,current,previousTransform,tick);
        if(!cameraController.overrideTransform(viewer,transition.transform()))return false;
        if(runtimeRenderer!=null){
            actorPlayback.tickScene(viewer,scene.actorIds(),tick);
            Map<ActorId,ActorSample> actors=new LinkedHashMap<>();
            for(ActorId id:scene.actorIds()){ActorSample sample=actorPlayback.sample(viewer,id);if(sample!=null)actors.put(id,sample);}
            runtimeRenderer.render(viewer,new DirectorRenderFrame(Math.max(0L,Math.round(tick)),tick,transition.transform(),actors));
        }
        return true;
    }

    public CameraTransform sample(Player viewer, Scene scene, double tick) { if(!renderAt(viewer,scene,tick))return null; return cameraController.currentTransform(viewer); }
    private CameraTransform previousTransform(Player viewer,DirectorShot shot){CameraDefinition camera=cameras.get(shot.cameraId()).orElse(null);if(camera==null)return null;double localTick=Math.max(0.0,shot.durationTicks()-1.0);return targetTransform(viewer,shot,pathTransform(shot,camera.sample(localTick),localTick));}
    private CameraTransform pathTransform(DirectorShot shot,CameraTransform base,double localTick){return shot.path().sample(shot.type(),localTick,base);}
    private CameraTransform targetTransform(Player viewer,DirectorShot shot,CameraTransform base){ActorSample target=resolveTarget(viewer,shot);if(target==null||!target.exists())return base;double x=target.transform().x(),y=target.transform().y(),z=target.transform().z();return switch(shot.type()){case FOLLOW->{CameraTransform origin=cameras.get(shot.cameraId()).map(c->c.sample(0.0)).orElse(base);yield CameraTargetMath.follow(base,x,y,z,origin.x()-x,origin.y()-y,origin.z()-z);}case LOOK_AT,ORBIT->CameraTargetMath.lookAt(base,x,y,z);default->base;};}
    private ActorSample resolveTarget(Player viewer,DirectorShot shot){if(shot.targetActorId()==null||shot.targetActorId().isBlank())return null;try{return actorPlayback.sample(viewer,new ActorId(UUID.fromString(shot.targetActorId())));}catch(IllegalArgumentException ignored){return null;}}
    public void stop(Player viewer){playback.remove(viewer.getUniqueId());if(runtimeRenderer!=null)runtimeRenderer.stop(viewer);cameraController.stop(viewer);}
    public void stopAll(Iterable<? extends Player> viewers){viewers.forEach(this::stop);}
    public boolean active(Player viewer){return playback.containsKey(viewer.getUniqueId());}
    public boolean playing(Player viewer){PlaybackState s=playback.get(viewer.getUniqueId());return s!=null&&s.playing;}
    public double currentTick(Player viewer){PlaybackState s=playback.get(viewer.getUniqueId());return s==null?0.0:s.tick;}
    public String currentScene(Player viewer){PlaybackState s=playback.get(viewer.getUniqueId());return s==null?null:s.sceneName;}
    public int planCount(){return plans.size();}
    public void clear(){playback.clear();scenes.clear();plans.clear();}
    public DirectorRuntimeFrameRenderer runtimeRenderer(){return runtimeRenderer;}
    private static final class PlaybackState{private final String sceneName;private volatile double tick;private volatile boolean playing;private PlaybackState(String sceneName,double tick,boolean playing){this.sceneName=sceneName;this.tick=tick;this.playing=playing;}}
}
