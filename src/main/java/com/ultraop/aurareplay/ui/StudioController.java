package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraKeyframe;
import com.ultraop.aurareplay.camera.CameraStudioService;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Viewer-local in-game Studio navigation and actor/camera editing. */
public final class StudioController {
    private static final String TITLE=ChatColor.DARK_AQUA+"AuraReplay Studio";
    private static final double POS_STEP=.1d, SCALE_STEP=.1d;
    private static final float ROT_STEP=5f, FOV_STEP=5f;
    private final AuraEngine engine;
    private final StudioToolService tools;
    private final CameraStudioService cameraTools;
    private final Map<UUID,StudioSession> sessions=new ConcurrentHashMap<>();
    public StudioController(AuraEngine engine){this.engine=engine;this.tools=new StudioToolService(engine);this.cameraTools=engine.cameraStudioService();}
    public void open(Player p){StudioSession s=sessions.computeIfAbsent(p.getUniqueId(),StudioSession::new);s.root();renderRoot(p);}
    public void close(Player p){sessions.remove(p.getUniqueId());}
    public StudioSession session(Player p){return sessions.get(p.getUniqueId());}
    public boolean isStudioInventory(Inventory inventory){return inventory!=null&&inventory.getHolder() instanceof Holder;}

    public void click(Player p,int slot){
        StudioSession s=sessions.get(p.getUniqueId());if(s==null)return;
        if(s.page()==StudioSession.Page.ROOT){
            if(slot==10){s.actors();renderActors(p);return;}
            String c=switch(slot){case 11->"Transform";case 12->"Motion";case 13->"Appearance";case 14->"Camera";case 15->"Effects";case 16->"World";case 19->"Playback";case 20->"Project";case 21->"Settings";default->null;};
            if(c!=null){s.category(c);renderCategory(p,c);}return;
        }
        if(slot==26){
            switch(s.page()){
                case ACTOR,TRANSFORM,MOTION,IDENTITY,EQUIPMENT-> {s.pageActor();renderActor(p,selectedActor(s));}
                case ACTORS-> {s.root();renderRoot(p);}
                case CAMERA-> {s.category("Camera");renderCategory(p,"Camera");}
                default-> {s.root();renderRoot(p);}
            }return;
        }
        if(s.page()==StudioSession.Page.ACTORS){
            var actors=engine.actorManager().all().stream().toList();
            if(slot>=0&&slot<Math.min(26,actors.size())){ActorDefinition a=actors.get(slot);s.selectActor(a.id());renderActor(p,a);}return;
        }
        if(s.page()==StudioSession.Page.CATEGORY && "Camera".equals(s.category())){cameraCategoryClick(p,s,slot);return;}
        if(s.page()==StudioSession.Page.CAMERA){cameraClick(p,s,slot);return;}
        ActorDefinition a=selectedActor(s);
        if(a==null){s.actors();renderActors(p);return;}
        switch(s.page()){
            case ACTOR -> actorClick(p,s,a,slot);
            case TRANSFORM -> transformClick(p,a,slot);
            case MOTION -> motionClick(p,a,slot);
            case IDENTITY -> identityClick(p,a,slot);
            case EQUIPMENT -> equipmentClick(p,a,slot);
            default -> { }
        }
        if(s.page()==StudioSession.Page.CATEGORY && "Transform".equals(s.category())){s.actors();renderActors(p);}
        else if(s.page()==StudioSession.Page.CATEGORY && "Playback".equals(s.category())) playbackClick(p,slot);
    }

    private void actorClick(Player p,StudioSession s,ActorDefinition a,int slot){
        switch(slot){
            case 10->{s.transform();renderTransform(p,a);}
            case 11->{tools.toggleActorVisibility(a.id());renderActor(p,a);}
            case 12->{s.motion();renderMotion(p,a);}
            case 13->{s.identity();renderIdentity(p,a);}
            case 14->{s.equipment();renderEquipment(p,a);}
            default->{ }
        }
    }
    private void transformClick(Player p,ActorDefinition a,int slot){
        ActorTransform t=a.transform();ActorTransform n=switch(slot){
            case 0->t.withPosition(t.x()-POS_STEP,t.y(),t.z());case 1->t.withPosition(t.x()+POS_STEP,t.y(),t.z());
            case 3->t.withPosition(t.x(),t.y()-POS_STEP,t.z());case 4->t.withPosition(t.x(),t.y()+POS_STEP,t.z());
            case 6->t.withPosition(t.x(),t.y(),t.z()-POS_STEP);case 7->t.withPosition(t.x(),t.y(),t.z()+POS_STEP);
            case 9->t.withRotation(t.yaw()-ROT_STEP,t.pitch(),t.roll());case 10->t.withRotation(t.yaw()+ROT_STEP,t.pitch(),t.roll());
            case 12->t.withRotation(t.yaw(),t.pitch()-ROT_STEP,t.roll());case 13->t.withRotation(t.yaw(),t.pitch()+ROT_STEP,t.roll());
            case 15->t.withRotation(t.yaw(),t.pitch(),t.roll()-ROT_STEP);case 16->t.withRotation(t.yaw(),t.pitch(),t.roll()+ROT_STEP);
            case 18->t.withScale(Math.max(SCALE_STEP,t.scale()-SCALE_STEP));case 19->t.withScale(t.scale()+SCALE_STEP);default->null;};
        if(n!=null){a.setTransform(n);renderTransform(p,a);}
    }
    private void motionClick(Player p,ActorDefinition a,int slot){
        switch(slot){case 10->a.setPlaybackSpeed(Math.max(.1d,a.playbackSpeed()-.1d));case 11->a.setPlaybackSpeed(a.playbackSpeed()+.1d);case 13->a.setLoop(!a.loop());case 14->a.setStartDelayTicks(a.startDelayTicks()+10);case 15->a.setStartDelayTicks(Math.max(0,a.startDelayTicks()-10));case 16->a.setReverse(!a.reverse());default->{return;}}renderMotion(p,a);
    }
    private void identityClick(Player p,ActorDefinition a,int slot){
        switch(slot){case 10->a.setNameVisible(!a.nameVisible());case 11->a.setNameHeightOffset(a.nameHeightOffset()+.1d);case 12->a.setNameHeightOffset(a.nameHeightOffset()-.1d);default->{return;}}renderIdentity(p,a);
    }
    private void equipmentClick(Player p,ActorDefinition a,int slot){
        if(slot==10)p.sendMessage(ChatColor.GRAY+"Equipment state is recording-driven. Slot override tracks will attach here.");
        else if(slot==11)p.sendMessage(ChatColor.GRAY+"Main-hand override track foundation is ready.");
        else if(slot==12)p.sendMessage(ChatColor.GRAY+"Off-hand override track foundation is ready.");
    }
    private void playbackClick(Player p,int slot){
        if(slot==11){if(tools.startRecording(p))p.sendMessage(ChatColor.GREEN+"Recording started.");else p.sendMessage(ChatColor.YELLOW+"A recording is already active.");}
        else if(slot==12){String n="capture-"+System.currentTimeMillis();if(tools.stopRecording(n))p.sendMessage(ChatColor.GREEN+"Recording saved: "+n);else p.sendMessage(ChatColor.YELLOW+"No recording is active.");}
    }

    private void cameraCategoryClick(Player p,StudioSession s,int slot){
        List<CameraDefinition> cameras=engine.cameraManager().all().stream().sorted(java.util.Comparator.comparing(CameraDefinition::id)).toList();
        if(slot>=0&&slot<Math.min(20,cameras.size())){s.selectCamera(cameras.get(slot).id());renderCamera(p,cameras.get(slot));return;}
        if(slot==22)p.sendMessage(ChatColor.GRAY+"Registered cameras: "+cameras.size());
    }
    private void cameraClick(Player p,StudioSession s,int slot){
        CameraDefinition c=selectedCamera(s);if(c==null){s.category("Camera");renderCategory(p,"Camera");return;}
        CameraTransform t=c.transform();
        switch(slot){
            case 0->cameraTools.move(c.id(),t.x()-POS_STEP,t.y(),t.z());case 1->cameraTools.move(c.id(),t.x()+POS_STEP,t.y(),t.z());
            case 3->cameraTools.move(c.id(),t.x(),t.y()-POS_STEP,t.z());case 4->cameraTools.move(c.id(),t.x(),t.y()+POS_STEP,t.z());
            case 6->cameraTools.move(c.id(),t.x(),t.y(),t.z()-POS_STEP);case 7->cameraTools.move(c.id(),t.x(),t.y(),t.z()+POS_STEP);
            case 9->cameraTools.rotate(c.id(),t.yaw()-ROT_STEP,t.pitch(),t.roll());case 10->cameraTools.rotate(c.id(),t.yaw()+ROT_STEP,t.pitch(),t.roll());
            case 12->cameraTools.rotate(c.id(),t.yaw(),t.pitch()-ROT_STEP,t.roll());case 13->cameraTools.rotate(c.id(),t.yaw(),t.pitch()+ROT_STEP,t.roll());
            case 15->cameraTools.rotate(c.id(),t.yaw(),t.pitch(),t.roll()-ROT_STEP);case 16->cameraTools.rotate(c.id(),t.yaw(),t.pitch(),t.roll()+ROT_STEP);
            case 18->cameraTools.setFov(c.id(),Math.max(1f,t.fov()-FOV_STEP));case 19->cameraTools.setFov(c.id(),Math.min(179f,t.fov()+FOV_STEP));
            case 20->cameraTools.setHeadTrack(c.id(),!c.headTrack());case 21->toggleFollowActor(p,c);case 22->addCameraKeyframe(c);case 23->removeLastCameraKeyframe(c);
            case 24->engine.cameraController().start(p,c);case 25->engine.cameraController().stop(p);default->{ }
        }
        renderCamera(p,c);
    }
    private void toggleFollowActor(Player p,CameraDefinition c){
        if(c.followActorId()!=null){cameraTools.clearFollowActor(c.id());p.sendMessage(ChatColor.GRAY+"Camera follow target cleared.");return;}
        var actors=engine.actorManager().all().stream().sorted(java.util.Comparator.comparing(a->a.id().toString())).toList();
        if(actors.isEmpty()){p.sendMessage(ChatColor.YELLOW+"No actors available for camera follow.");return;}
        ActorDefinition actor=actors.get(0);cameraTools.setFollowActor(c.id(),actor.id().toString());p.sendMessage(ChatColor.GREEN+"Following actor: "+actor.name());
    }
    private void addCameraKeyframe(CameraDefinition c){c.addKeyframe(new CameraKeyframe(nextKeyframeTick(c),c.transform()));}
    private void removeLastCameraKeyframe(CameraDefinition c){List<CameraKeyframe> keys=c.keyframes();if(!keys.isEmpty())c.removeKeyframe(keys.get(keys.size()-1).tick());}
    private static long nextKeyframeTick(CameraDefinition c){return c.keyframes().isEmpty()?0:c.keyframes().get(c.keyframes().size()-1).tick()+20;}
    private CameraDefinition selectedCamera(StudioSession s){return s.selectedCamera()==null?null:engine.cameraManager().get(s.selectedCamera()).orElse(null);}
    private ActorDefinition selectedActor(StudioSession s){ActorId id=s.selectedActor();return id==null?null:engine.actorManager().get(id).orElse(null);}

    private void renderRoot(Player p){Inventory i=inv(TITLE);item(i,10,Material.PLAYER_HEAD,"Actors","Select and edit virtual actors");item(i,11,Material.COMPASS,"Transform","Position, rotation and scale");item(i,12,Material.LEATHER_BOOTS,"Motion","Playback and motion tools");item(i,13,Material.ARMOR_STAND,"Appearance","Identity, equipment and visuals");item(i,14,Material.SPYGLASS,"Camera","Cinematic camera controls");item(i,15,Material.FIREWORK_ROCKET,"Effects","Particles, sounds and cues");item(i,16,Material.GRASS_BLOCK,"World","World and environment controls");item(i,19,Material.CLOCK,"Playback","Record and take controls");item(i,20,Material.BOOK,"Project","Scenes, recordings and assets");item(i,21,Material.REDSTONE,"Settings","Studio and input settings");p.openInventory(i);}
    private void renderActors(Player p){Inventory i=inv(ChatColor.DARK_AQUA+"Studio / Actors");var a=engine.actorManager().all().stream().toList();for(int n=0;n<Math.min(26,a.size());n++){ActorDefinition x=a.get(n);item(i,n,Material.ARMOR_STAND,x.name(),"Select actor","ID: "+x.id());}item(i,26,Material.ARROW,"Back","Return to Studio");p.openInventory(i);}
    private void renderActor(Player p,ActorDefinition a){if(a==null){safelyRoot(p);return;}Inventory i=inv(ChatColor.DARK_AQUA+"Studio / "+a.name());item(i,10,Material.COMPASS,"Transform",summary(a.transform()));item(i,11,a.visible()?Material.LANTERN:Material.REDSTONE_TORCH,a.visible()?"Hide Actor":"Show Actor","Toggle visibility");item(i,12,Material.LEATHER_BOOTS,"Motion","Speed: "+fmt(a.playbackSpeed()),"Delay: "+a.startDelayTicks()+" ticks","Loop: "+a.loop(),"Reverse: "+a.reverse());item(i,13,Material.NAME_TAG,"Identity","Name: "+a.name(),"Nametag: "+a.nameVisible(),"Height: "+fmt(a.nameHeightOffset()));item(i,14,Material.ARMOR_STAND,"Equipment","Armor and held items");item(i,26,Material.ARROW,"Back","Return to actors");p.openInventory(i);}
    private void renderTransform(Player p,ActorDefinition a){ActorTransform t=a.transform();Inventory i=inv(ChatColor.DARK_AQUA+"Transform / "+a.name());item(i,0,Material.REDSTONE,"X -","Current: "+fmt(t.x()),"Step: 0.10");item(i,1,Material.EMERALD,"X +","Current: "+fmt(t.x()),"Step: 0.10");item(i,3,Material.REDSTONE,"Y -","Current: "+fmt(t.y()),"Step: 0.10");item(i,4,Material.EMERALD,"Y +","Current: "+fmt(t.y()),"Step: 0.10");item(i,6,Material.REDSTONE,"Z -","Current: "+fmt(t.z()),"Step: 0.10");item(i,7,Material.EMERALD,"Z +","Current: "+fmt(t.z()),"Step: 0.10");item(i,9,Material.REDSTONE_TORCH,"Yaw -","Current: "+fmt(t.yaw())+"°","Step: 5°");item(i,10,Material.TORCH,"Yaw +","Current: "+fmt(t.yaw())+"°","Step: 5°");item(i,12,Material.REDSTONE_TORCH,"Pitch -","Current: "+fmt(t.pitch())+"°","Step: 5°");item(i,13,Material.TORCH,"Pitch +","Current: "+fmt(t.pitch())+"°","Step: 5°");item(i,15,Material.REDSTONE_TORCH,"Roll -","Current: "+fmt(t.roll())+"°","Step: 5°");item(i,16,Material.TORCH,"Roll +","Current: "+fmt(t.roll())+"°","Step: 5°");item(i,18,Material.REDSTONE,"Scale -","Current: "+fmt(t.scale()),"Step: 0.10");item(i,19,Material.EMERALD,"Scale +","Current: "+fmt(t.scale()),"Step: 0.10");item(i,22,Material.BOOK,"Current Transform",summary(t));item(i,26,Material.ARROW,"Back","Return to actor");p.openInventory(i);}
    private void renderMotion(Player p,ActorDefinition a){Inventory i=inv(ChatColor.DARK_AQUA+"Motion / "+a.name());item(i,10,Material.SPECTRAL_ARROW,"Speed -","Current: "+fmt(a.playbackSpeed()),"Step: 0.1");item(i,11,Material.ARROW,"Speed +","Current: "+fmt(a.playbackSpeed()),"Step: 0.1");item(i,13,Material.REPEATER,"Loop","Current: "+a.loop(),"Toggle loop playback");item(i,14,Material.CLOCK,"Delay +10","Current: "+a.startDelayTicks()+" ticks");item(i,15,Material.CLOCK,"Delay -10","Current: "+a.startDelayTicks()+" ticks");item(i,16,Material.CLOCK,"Reverse","Current: "+a.reverse(),"Toggle reverse playback");item(i,26,Material.ARROW,"Back","Return to actor");p.openInventory(i);}
    private void renderIdentity(Player p,ActorDefinition a){Inventory i=inv(ChatColor.DARK_AQUA+"Identity / "+a.name());item(i,10,a.nameVisible()?Material.NAME_TAG:Material.BARRIER,a.nameVisible()?"Hide Nametag":"Show Nametag","Current: "+a.nameVisible());item(i,11,Material.ARROW,"Height +0.10","Current: "+fmt(a.nameHeightOffset()));item(i,12,Material.SPECTRAL_ARROW,"Height -0.10","Current: "+fmt(a.nameHeightOffset()));item(i,14,Material.PLAYER_HEAD,"Display Name","Current: "+a.name(),"Use command editor for exact text");item(i,15,Material.NAME_TAG,"Prefix","Current: "+a.namePrefix());item(i,16,Material.NAME_TAG,"Suffix","Current: "+a.nameSuffix());item(i,26,Material.ARROW,"Back","Return to actor");p.openInventory(i);}
    private void renderEquipment(Player p,ActorDefinition a){Inventory i=inv(ChatColor.DARK_AQUA+"Equipment / "+a.name());item(i,10,Material.ARMOR_STAND,"Equipment State","Recording equipment is preserved","Keyframe override system attaches here");item(i,11,Material.DIAMOND_SWORD,"Main Hand","Recording-driven","Override track foundation");item(i,12,Material.SHIELD,"Off Hand","Recording-driven","Override track foundation");item(i,14,Material.NETHERITE_HELMET,"Helmet","Recording-driven","Override track foundation");item(i,15,Material.NETHERITE_CHESTPLATE,"Chestplate","Recording-driven","Override track foundation");item(i,16,Material.NETHERITE_LEGGINGS,"Leggings","Recording-driven","Override track foundation");item(i,17,Material.NETHERITE_BOOTS,"Boots","Recording-driven","Override track foundation");item(i,26,Material.ARROW,"Back","Return to actor");p.openInventory(i);}
    private void renderCategory(Player p,String c){Inventory i=inv(ChatColor.DARK_AQUA+"Studio / "+c);if("Playback".equals(c)){item(i,11,Material.RED_DYE,"Record","Start recording");item(i,12,Material.LIME_DYE,"Stop & Save","Save current recording");}else if("Camera".equals(c)){List<CameraDefinition> cameras=engine.cameraManager().all().stream().sorted(java.util.Comparator.comparing(CameraDefinition::id)).toList();for(int n=0;n<Math.min(20,cameras.size());n++){CameraDefinition x=cameras.get(n);item(i,n,Material.SPYGLASS,x.name(),"ID: "+x.id(),"Keyframes: "+x.keyframes().size());}item(i,22,Material.BOOK,"Camera Library","Registered: "+cameras.size());}else if("Transform".equals(c))item(i,13,Material.PLAYER_HEAD,"Select Actor","Choose an actor to transform");else item(i,13,Material.BOOK,"Tool","Category foundation; shared services are used");item(i,26,Material.ARROW,"Back","Return to Studio");p.openInventory(i);}
    private void renderCamera(Player p,CameraDefinition c){CameraTransform t=c.transform();Inventory i=inv(ChatColor.DARK_AQUA+"Camera / "+c.name());item(i,0,Material.REDSTONE,"X -","Current: "+fmt(t.x()),"Step: 0.10");item(i,1,Material.EMERALD,"X +","Current: "+fmt(t.x()),"Step: 0.10");item(i,3,Material.REDSTONE,"Y -","Current: "+fmt(t.y()),"Step: 0.10");item(i,4,Material.EMERALD,"Y +","Current: "+fmt(t.y()),"Step: 0.10");item(i,6,Material.REDSTONE,"Z -","Current: "+fmt(t.z()),"Step: 0.10");item(i,7,Material.EMERALD,"Z +","Current: "+fmt(t.z()),"Step: 0.10");item(i,9,Material.REDSTONE_TORCH,"Yaw -","Current: "+fmt(t.yaw())+"°");item(i,10,Material.TORCH,"Yaw +","Current: "+fmt(t.yaw())+"°");item(i,12,Material.REDSTONE_TORCH,"Pitch -","Current: "+fmt(t.pitch())+"°");item(i,13,Material.TORCH,"Pitch +","Current: "+fmt(t.pitch())+"°");item(i,15,Material.REDSTONE_TORCH,"Roll -","Current: "+fmt(t.roll())+"°");item(i,16,Material.TORCH,"Roll +","Current: "+fmt(t.roll())+"°");item(i,18,Material.REDSTONE,"FOV -","Current: "+fmt(t.fov())+"°","Step: 5°");item(i,19,Material.EMERALD,"FOV +","Current: "+fmt(t.fov())+"°","Step: 5°");item(i,20,c.headTrack()?Material.ENDER_EYE:Material.ENDER_PEARL,"Head Track","Current: "+c.headTrack());item(i,21,c.followActorId()==null?Material.COMPASS:Material.TARGET,"Follow Actor","Target: "+(c.followActorId()==null?"none":c.followActorId()));item(i,22,Material.LIME_DYE,"Add Keyframe","Next: "+nextKeyframeTick(c)+" ticks","Captures current transform");item(i,23,Material.RED_DYE,"Remove Last Keyframe","Count: "+c.keyframes().size());item(i,24,Material.CAMERA,"Activate","Use this cinematic camera for viewer");item(i,25,Material.BARRIER,"Stop Camera","Restore normal camera");item(i,2,Material.BOOK,"Keyframes","Ticks: "+keyframeTicks(c));item(i,26,Material.ARROW,"Back","Return to camera library");p.openInventory(i);}
    private static String keyframeTicks(CameraDefinition c){return c.keyframes().stream().map(k->Long.toString(k.tick())).reduce((a,b)->a+", "+b).orElse("none");}
    private static String[] summary(ActorTransform t){return new String[]{"X "+fmt(t.x())+"  Y "+fmt(t.y())+"  Z "+fmt(t.z()),"Yaw "+fmt(t.yaw())+"  Pitch "+fmt(t.pitch())+"  Roll "+fmt(t.roll()),"Scale "+fmt(t.scale())};}
    private static String fmt(double v){return String.format(java.util.Locale.ROOT,"%.2f",v);}private static String fmt(float v){return String.format(java.util.Locale.ROOT,"%.1f",v);}
    private static Inventory inv(String title){return Bukkit.createInventory(new Holder(),27,title);}private static void item(Inventory i,int slot,Material m,String name,String... lore){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();meta.setDisplayName(ChatColor.AQUA+name);meta.setLore(Arrays.stream(lore).map(v->ChatColor.GRAY+v).toList());x.setItemMeta(meta);i.setItem(slot,x);}private static final class Holder implements InventoryHolder{public Inventory getInventory(){return null;}}
    private void safelyRoot(Player p){StudioSession s=sessions.get(p.getUniqueId());if(s!=null){s.root();renderRoot(p);}}
}
