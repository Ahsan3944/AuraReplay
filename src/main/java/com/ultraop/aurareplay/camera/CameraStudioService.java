package com.ultraop.aurareplay.camera;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Command/UI-facing camera editing service. Keeps camera data separate from rendering. */
public final class CameraStudioService {
    private final CameraManager cameras;
    private final ActorManager actors;

    public CameraStudioService(CameraManager cameras, ActorManager actors) {
        this.cameras = Objects.requireNonNull(cameras, "cameras");
        this.actors = Objects.requireNonNull(actors, "actors");
    }
    public CameraDefinition create(String id, String name, CameraTransform transform) { return cameras.create(id, name, transform); }
    public Optional<CameraDefinition> get(String id) { return cameras.get(id); }
    public boolean remove(String id) { return cameras.remove(id); }
    public void move(String id,double x,double y,double z){CameraDefinition c=camera(id);c.setTransform(c.transform().withPosition(x,y,z));}
    public void rotate(String id,float yaw,float pitch,float roll){CameraDefinition c=camera(id);c.setTransform(c.transform().withRotation(yaw,pitch,roll));}
    public void setFov(String id,float fov){CameraDefinition c=camera(id);c.setTransform(c.transform().withFov(fov));}
    public void setFollowActor(String id,String actorId){
        ActorDefinition actor=actors.get(new ActorId(UUID.fromString(actorId))).orElse(null);
        if(actor==null)throw new IllegalArgumentException("unknown actor: "+actorId);
        camera(id).setFollowActorId(actor.id().toString());
    }
    public void clearFollowActor(String id){camera(id).setFollowActorId(null);}
    public void setHeadTrack(String id,boolean enabled){camera(id).setHeadTrack(enabled);}
    private CameraDefinition camera(String id){return cameras.get(id).orElseThrow(()->new IllegalArgumentException("unknown camera: "+id));}
}
