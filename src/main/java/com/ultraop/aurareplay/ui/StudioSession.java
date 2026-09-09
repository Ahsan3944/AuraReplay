package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorId;
import java.util.UUID;

/** Viewer-local Studio navigation and selection state. */
public final class StudioSession {
    public enum Page { ROOT, CATEGORY, ACTORS, ACTOR, TRANSFORM, MOTION, IDENTITY, EQUIPMENT, CAMERA }
    private final UUID viewerId;
    private Page page=Page.ROOT;
    private String category;
    private ActorId selectedActor;
    private String selectedCamera;
    private long cameraTimelineTick;

    public StudioSession(UUID viewerId){this.viewerId=viewerId;}
    public UUID viewerId(){return viewerId;}
    public Page page(){return page;}
    public String category(){return category;}
    public ActorId selectedActor(){return selectedActor;}
    public String selectedCamera(){return selectedCamera;}
    public long cameraTimelineTick(){return cameraTimelineTick;}

    public void root(){page=Page.ROOT;category=null;selectedActor=null;selectedCamera=null;cameraTimelineTick=0;}
    public void category(String category){page=Page.CATEGORY;this.category=category;}
    public void actors(){page=Page.ACTORS;category="Actors";}
    public void selectActor(ActorId actorId){selectedActor=actorId;page=Page.ACTOR;category="Actors / "+actorId;}
    public void pageActor(){page=Page.ACTOR;category="Actors / "+selectedActor;}
    public void transform(){page=Page.TRANSFORM;category="Transform";}
    public void motion(){page=Page.MOTION;category="Motion";}
    public void identity(){page=Page.IDENTITY;category="Identity";}
    public void equipment(){page=Page.EQUIPMENT;category="Equipment";}
    public void selectCamera(String cameraId){selectedCamera=cameraId;cameraTimelineTick=0;page=Page.CAMERA;category="Camera / "+cameraId;}
    public void setCameraTimelineTick(long tick){cameraTimelineTick=Math.max(0,tick);}
    public void advanceCameraTimeline(long deltaTicks){cameraTimelineTick=Math.max(0,cameraTimelineTick+deltaTicks);}
}
