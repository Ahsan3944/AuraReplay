package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorPlaybackController;
import com.ultraop.aurareplay.camera.CameraController;
import com.ultraop.aurareplay.effects.EffectPlaybackEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread scene registry and viewer-local scene playback coordinator. */
public final class SceneManager {
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<UUID, ScenePlaybackSession> activeSessions = new ConcurrentHashMap<>();
    private final ActorManager actorManager;
    private final ActorPlaybackController playbackController;
    private final SceneCameraService sceneCameras;
    private final CameraController cameraController;
    private final EffectPlaybackEngine effectPlayback = new EffectPlaybackEngine();

    public SceneManager(ActorManager actorManager, ActorPlaybackController playbackController, SceneCameraService sceneCameras, CameraController cameraController) {
        this.actorManager = actorManager; this.playbackController = playbackController; this.sceneCameras = sceneCameras; this.cameraController = cameraController;
    }
    public Scene create(String name) { String key=normalize(name); if(scenes.containsKey(key)) throw new IllegalArgumentException("scene already exists: "+name); Scene scene=new Scene(name); scenes.put(key,scene); return scene; }
    public Optional<Scene> get(String name) { return Optional.ofNullable(scenes.get(normalize(name))); }
    public boolean exists(String name) { return scenes.containsKey(normalize(name)); }
    public boolean delete(String name) { String key=normalize(name); Scene scene=scenes.get(key); if(scene==null)return false; activeSessions.entrySet().stream().filter(e->e.getValue().scene()==scene).map(Map.Entry::getKey).toList().forEach(uuid->{Player viewer=Bukkit.getPlayer(uuid);if(viewer!=null)stop(viewer);else activeSessions.remove(uuid);}); scenes.remove(key,scene);sceneCameras.unbind(scene);return true; }
    public Collection<Scene> all() { return scenes.values().stream().toList(); }
    public boolean bindCamera(String sceneName,String cameraId){Scene s=scenes.get(normalize(sceneName));return s!=null&&sceneCameras.bind(s,cameraId);}
    public boolean unbindCamera(String sceneName){Scene s=scenes.get(normalize(sceneName));return s!=null&&sceneCameras.unbind(s);}
    public Optional<String> cameraId(String sceneName){Scene s=scenes.get(normalize(sceneName));return s==null?Optional.empty():sceneCameras.cameraId(s);}
    public boolean addActor(String sceneName,ActorId actorId){Scene s=scenes.get(normalize(sceneName));if(s==null)return false;Optional<ActorDefinition>a=actorManager.get(actorId);if(a.isEmpty())return false;boolean added=s.addActor(actorId);if(added){long d=a.get().recording().durationTicks();if(d>s.timeline().durationTicks())s.timeline().setDurationTicks(d);}return added;}
    public boolean removeActor(String sceneName,ActorId actorId){Scene s=scenes.get(normalize(sceneName));return s!=null&&s.removeActor(actorId);}
    public int removeActorEverywhere(ActorId actorId){if(actorId==null)return 0;playbackController.stopUsingActor(actorId);int removed=0;for(Scene s:scenes.values())if(s.removeActor(actorId))removed++;return removed;}
    public int reconcileActorReferences(){int removed=0;for(Scene s:scenes.values())for(ActorId id:s.actorIds())if(actorManager.get(id).isEmpty()&&s.removeActor(id))removed++;return removed;}
    public int play(Player viewer,String sceneName){Scene s=get(sceneName).orElseThrow(()->new IllegalArgumentException("scene not found: "+sceneName));stop(viewer);ensureDuration(s);int started=startActors(viewer,s);ScenePlaybackSession session=new ScenePlaybackSession(s);activeSessions.put(viewer.getUniqueId(),session);renderAt(viewer,session);sceneCameras.camera(s).ifPresent(c->cameraController.start(viewer,c));return started;}
    private int startActors(Player viewer,Scene s){int started=0;for(ActorId id:s.actorIds()){Optional<ActorDefinition>a=actorManager.get(id);if(a.isEmpty())continue;playbackController.start(viewer,a.get());started++;}return started;}
    private void ensureDuration(Scene s){if(s.timeline().durationTicks()!=0)return;long d=s.actorIds().stream().map(actorManager::get).flatMap(Optional::stream).mapToLong(a->a.recording().durationTicks()).max().orElse(0L);s.timeline().setDurationTicks(d);}
    public void tick(Player viewer){ScenePlaybackSession session=activeSessions.get(viewer.getUniqueId());if(session==null)return;double before=session.cursor().tick();boolean active=session.tick();renderAt(viewer,session,before);if(!active&&session.finished())stop(viewer);}
    private void renderAt(Player viewer,ScenePlaybackSession session){renderAt(viewer,session,session.cursor().tick());}
    private void renderAt(Player viewer,ScenePlaybackSession session,double previousTick){double tick=session.cursor().tick();playbackController.tickScene(viewer,session.scene().actorIds(),tick);if(cameraController.active(viewer))cameraController.seek(viewer,tick);if(Math.abs(tick-previousTick)>1.0e-9d){boolean reverse=session.scene().timeline().reverse();if(session.scene().timeline().loop()){long boundary=reverse?session.scene().timeline().inPoint():session.scene().timeline().outPoint();if((!reverse&&tick<previousTick)||(reverse&&tick>previousTick))effectPlayback.advanceLoop(viewer,session.scene(),session.effectCursor(),tick,boundary,reverse);else effectPlayback.advance(viewer,session.scene(),session.effectCursor(),tick,reverse);}else effectPlayback.advance(viewer,session.scene(),session.effectCursor(),tick,reverse);}}
    public boolean pause(Player viewer){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());if(s==null)return false;s.setPaused(true);return true;}
    public boolean resume(Player viewer){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());if(s==null)return false;s.setPaused(false);return true;}
    public boolean paused(Player viewer){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());return s!=null&&s.paused();}
    public Optional<Double> currentTick(Player viewer){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());return s==null?Optional.empty():Optional.of(s.cursor().tick());}
    public boolean seek(Player viewer,double tick){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());if(s==null)return false;s.seek(tick);renderAt(viewer,s);return true;}
    public boolean step(Player viewer,double deltaTicks){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());if(s==null)return false;s.seek(s.cursor().tick()+deltaTicks);renderAt(viewer,s);return true;}
    public boolean stop(Player viewer){ScenePlaybackSession s=activeSessions.remove(viewer.getUniqueId());if(s==null)return false;for(ActorId id:s.scene().actorIds())playbackController.stop(viewer,id);if(sceneCameras.camera(s.scene()).isPresent())cameraController.stop(viewer);return true;}
    public Optional<String> activeScene(Player viewer){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());return s==null?Optional.empty():Optional.of(s.scene().name());}
    public Set<ActorId> activeActorIds(Player viewer){ScenePlaybackSession s=activeSessions.get(viewer.getUniqueId());return s==null?Set.of():Set.copyOf(s.scene().actorIds());}
    public void stopAll(Player viewer){stop(viewer);}
    public void clear(){for(Player viewer:Bukkit.getOnlinePlayers())stop(viewer);activeSessions.clear();scenes.clear();sceneCameras.clear();}
    private static String normalize(String name){return name.trim().toLowerCase(java.util.Locale.ROOT);}
}
