package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.timeline.Timeline;
import com.ultraop.aurareplay.timeline.TimelineEditor;
import com.ultraop.aurareplay.timeline.TimelineKeyframe;
import com.ultraop.aurareplay.timeline.TimelineMarker;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class AuraReplayCommand implements CommandExecutor, TabCompleter {
    private final AuraEngine engine;
    public AuraReplayCommand(AuraEngine engine) { this.engine = engine; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aurareplay.use")) { sender.sendMessage(ChatColor.RED + "You do not have permission to use AuraReplay."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("studio")) { showStudio(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "record" -> handleRecord(sender);
            case "stop" -> handleStop(sender, args);
            case "recordings", "list" -> handleList(sender);
            case "actor" -> handleActor(sender, args);
            case "actors" -> handleActors(sender);
            case "scene" -> handleScene(sender, args);
            case "timeline" -> handleTimeline(sender, args);
            case "camera" -> handleCamera(sender, args);
            case "version" -> sender.sendMessage(ChatColor.AQUA + "AuraReplay 0.1.0-SNAPSHOT | Paper 1.21.11");
            default -> sendUsage(sender);
        }
        return true;
    }

    private void showStudio(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== AuraReplay Studio ===");
        s.sendMessage(ChatColor.GRAY + "Recording: " + (engine.tickRecorder().isRecording() ? "RUNNING" : "IDLE"));
        s.sendMessage(ChatColor.GRAY + "Saved captures: " + engine.recordingManager().all().size());
        s.sendMessage(ChatColor.GRAY + "Actors: " + engine.actorManager().all().size());
        s.sendMessage(ChatColor.GRAY + "Scenes: " + engine.sceneManager().all().size());
        s.sendMessage(ChatColor.GRAY + "Cameras: " + engine.cameraManager().all().size());
        s.sendMessage(ChatColor.YELLOW + "/aurareplay camera <create|list|delete|play|stop|position|rotation|fov|keyframe|follow|headtrack>");
    }

    private void handleRecord(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Recording must be started by a player."); return; }
        if (!p.hasPermission("aurareplay.record")) { s.sendMessage(ChatColor.RED + "You do not have recording permission."); return; }
        if (engine.tickRecorder().isRecording()) { s.sendMessage(ChatColor.RED + "A recording is already active."); return; }
        engine.tickRecorder().track(p); engine.tickRecorder().start(); s.sendMessage(ChatColor.GREEN + "Recording started.");
    }
    private void handleStop(CommandSender s, String[] a) {
        if (!engine.tickRecorder().isRecording()) { s.sendMessage(ChatColor.YELLOW + "No recording is active."); return; }
        var r = engine.tickRecorder().stop(a.length >= 2 ? a[1] : "capture-" + System.currentTimeMillis());
        if (r != null) { engine.recordingManager().register(r); s.sendMessage(ChatColor.GREEN + "Saved recording '" + r.name() + "' with " + r.durationTicks() + " ticks."); }
    }
    private void handleList(CommandSender s) { s.sendMessage(ChatColor.GOLD + "=== AuraReplay Recordings ==="); engine.recordingManager().all().forEach(r -> s.sendMessage(ChatColor.YELLOW + r.name() + ChatColor.GRAY + " — " + r.durationTicks() + " ticks")); }

    private void handleActor(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (!p.hasPermission("aurareplay.actor")) { p.sendMessage(ChatColor.RED + "You do not have actor permission."); return; }
        if (a.length < 2) { p.sendMessage(ChatColor.YELLOW + "/aurareplay actor <spawn|stop>"); return; }
        if (a[1].equalsIgnoreCase("spawn")) {
            if (a.length < 3) { p.sendMessage(ChatColor.YELLOW + "/aurareplay actor spawn <recording>"); return; }
            var r = engine.recordingManager().get(a[2]);
            if (r.isEmpty() || r.get().frames().isEmpty() || r.get().frames().get(0).entities().isEmpty()) { p.sendMessage(ChatColor.RED + "Recording not found or contains no entity data."); return; }
            var source = r.get().frames().get(0).entities().get(0); var l = p.getLocation();
            ActorDefinition actor = engine.actorManager().create(r.get(), ActorTransform.origin(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()), source.uuid(), source.entityId());
            actor.setName(r.get().name()); engine.actorPlaybackController().start(p, actor); p.sendMessage(ChatColor.GREEN + "Actor spawned: " + actor.id());
        } else if (a[1].equalsIgnoreCase("stop")) {
            if (a.length < 3) { p.sendMessage(ChatColor.YELLOW + "/aurareplay actor stop <id>"); return; }
            try { ActorId id = new ActorId(UUID.fromString(a[2])); engine.actorPlaybackController().stop(p, id); engine.actorManager().remove(id); p.sendMessage(ChatColor.GREEN + "Actor stopped."); } catch (IllegalArgumentException e) { p.sendMessage(ChatColor.RED + "Invalid actor UUID."); }
        }
    }
    private void handleActors(CommandSender s) { s.sendMessage(ChatColor.GOLD + "=== AuraReplay Actors ==="); engine.actorManager().all().forEach(a -> s.sendMessage(ChatColor.YELLOW + a.id() + ChatColor.GRAY + " — " + a.name())); }

    private void handleScene(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return; if (!p.hasPermission("aurareplay.scene")) { p.sendMessage(ChatColor.RED + "You do not have scene permission."); return; }
        if (a.length < 2) { p.sendMessage(ChatColor.YELLOW + "/aurareplay scene <create|add|remove|list|play|stop>"); return; }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "create" -> { if (a.length < 3) { p.sendMessage(ChatColor.YELLOW + "/aurareplay scene create <name>"); return; } if (engine.sceneManager().get(a[2]).isPresent()) { p.sendMessage(ChatColor.RED + "Scene already exists: " + a[2]); return; } p.sendMessage(ChatColor.GREEN + "Scene created: " + engine.sceneManager().create(a[2]).name()); }
            case "add", "remove" -> { if (a.length < 4) { p.sendMessage(ChatColor.YELLOW + "/aurareplay scene " + a[1] + " <scene> <actor-id>"); return; } try { ActorId id = new ActorId(UUID.fromString(a[3])); boolean changed = a[1].equalsIgnoreCase("remove") ? engine.sceneManager().removeActor(a[2], id) : engine.sceneManager().addActor(a[2], id); p.sendMessage((changed ? ChatColor.GREEN : ChatColor.YELLOW) + (changed ? "Scene updated." : "No change made.")); } catch (IllegalArgumentException e) { p.sendMessage(ChatColor.RED + "Invalid actor UUID."); } }
            case "list" -> engine.sceneManager().all().forEach(sc -> p.sendMessage(ChatColor.YELLOW + sc.name() + ChatColor.GRAY + " — " + sc.actorIds().size() + " actors"));
            case "play" -> { if (a.length < 3) { p.sendMessage(ChatColor.YELLOW + "/aurareplay scene play <name>"); return; } try { p.sendMessage(ChatColor.GREEN + "Scene playing: " + a[2] + " (" + engine.sceneManager().play(p, a[2]) + " actors)"); } catch (IllegalArgumentException e) { p.sendMessage(ChatColor.RED + e.getMessage()); } }
            case "stop" -> p.sendMessage(engine.sceneManager().stop(p) ? ChatColor.GREEN + "Scene stopped." : ChatColor.YELLOW + "No scene is playing.");
        }
    }

    private void handleCamera(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Camera commands must be run by a player."); return; }
        if (!p.hasPermission("aurareplay.camera")) { p.sendMessage(ChatColor.RED + "You do not have camera permission."); return; }
        if (a.length < 2) { cameraUsage(p); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        try {
            switch (op) {
                case "create" -> { require(a, 3, "/aurareplay camera create <name>"); var l=p.getLocation(); CameraDefinition c=engine.cameraManager().create(a[2],a[2],CameraTransform.origin(l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch())); p.sendMessage(ChatColor.GREEN+"Camera created: "+c.id()); }
                case "list" -> engine.cameraManager().all().forEach(c -> p.sendMessage(ChatColor.YELLOW+c.id()+ChatColor.GRAY+" — "+c.name()+" | "+c.keyframes().size()+" keyframes"));
                case "delete" -> { require(a,3,"/aurareplay camera delete <name>"); p.sendMessage(engine.cameraManager().remove(a[2]) ? ChatColor.GREEN+"Camera deleted." : ChatColor.YELLOW+"Camera not found."); }
                case "play" -> { require(a,3,"/aurareplay camera play <name>"); var c=engine.cameraManager().get(a[2]).orElseThrow(()->new IllegalArgumentException("camera not found: "+a[2])); engine.cameraController().start(p,c); p.sendMessage(ChatColor.GREEN+"Camera playing: "+c.name()); }
                case "stop" -> { engine.cameraController().stop(p); p.sendMessage(ChatColor.GREEN+"Camera stopped."); }
                case "position" -> { require(a,6,"/aurareplay camera position <name> <x> <y> <z>"); var c=getCamera(a[2]); c.setTransform(c.transform().withPosition(Double.parseDouble(a[3]),Double.parseDouble(a[4]),Double.parseDouble(a[5]))); p.sendMessage(ChatColor.GREEN+"Camera position updated."); }
                case "rotation" -> { require(a,5,"/aurareplay camera rotation <name> <yaw> <pitch>"); var c=getCamera(a[2]); c.setTransform(c.transform().withRotation(Float.parseFloat(a[3]),Float.parseFloat(a[4]),c.transform().roll())); p.sendMessage(ChatColor.GREEN+"Camera rotation updated."); }
                case "fov" -> { require(a,4,"/aurareplay camera fov <name> <value>"); var c=getCamera(a[2]); c.setTransform(c.transform().withFov(Float.parseFloat(a[3]))); p.sendMessage(ChatColor.GREEN+"Camera FOV updated."); }
                case "keyframe" -> handleCameraKeyframe(p,a);
                case "follow" -> { require(a,4,"/aurareplay camera follow <name> <actor-id>"); var c=getCamera(a[2]); c.setFollowActorId(a[3]); p.sendMessage(ChatColor.GREEN+"Camera follow target set."); }
                case "headtrack" -> { require(a,4,"/aurareplay camera headtrack <name> <true|false>"); var c=getCamera(a[2]); c.setHeadTrack(parseBoolean(a[3])); p.sendMessage(ChatColor.GREEN+"Camera head tracking updated."); }
                default -> cameraUsage(p);
            }
        } catch (NumberFormatException e) { p.sendMessage(ChatColor.RED+"Invalid number."); } catch (IllegalArgumentException e) { p.sendMessage(ChatColor.RED+e.getMessage()); }
    }
    private void handleCameraKeyframe(Player p,String[] a) {
        require(a,4,"/aurareplay camera keyframe <add|remove|list> <name> [tick]"); var c=getCamera(a[2]); String op=a[3].toLowerCase(Locale.ROOT);
        if(op.equals("list")){ c.keyframes().forEach(k->p.sendMessage(ChatColor.YELLOW+"tick="+k.tick()+ChatColor.GRAY+" | "+k.transform())); return; }
        require(a,5,"/aurareplay camera keyframe "+op+" <name> <tick>"); long tick=Long.parseLong(a[4]);
        if(op.equals("add")){ c.addKeyframe(new com.ultraop.aurareplay.camera.CameraKeyframe(tick,c.transform())); p.sendMessage(ChatColor.GREEN+"Camera keyframe added at "+tick+"."); }
        else if(op.equals("remove")){ p.sendMessage(c.removeKeyframe(tick)?ChatColor.GREEN+"Camera keyframe removed.":ChatColor.YELLOW+"Keyframe not found."); }
        else throw new IllegalArgumentException("unknown keyframe operation: "+op);
    }
    private CameraDefinition getCamera(String id){ return engine.cameraManager().get(id).orElseThrow(()->new IllegalArgumentException("camera not found: "+id)); }
    private void cameraUsage(CommandSender s){ s.sendMessage(ChatColor.YELLOW+"/aurareplay camera create <name>"); s.sendMessage(ChatColor.YELLOW+"/aurareplay camera list|delete|play|stop <name>"); s.sendMessage(ChatColor.YELLOW+"/aurareplay camera position|rotation|fov <name> ..."); s.sendMessage(ChatColor.YELLOW+"/aurareplay camera keyframe <add|remove|list> <name> [tick]"); s.sendMessage(ChatColor.YELLOW+"/aurareplay camera follow <name> <actor-id>"); s.sendMessage(ChatColor.YELLOW+"/aurareplay camera headtrack <name> <true|false>"); }

    private void handleTimeline(CommandSender s,String[] a){ if(!(s instanceof Player p))return; if(!p.hasPermission("aurareplay.edit")){p.sendMessage(ChatColor.RED+"You do not have timeline edit permission.");return;} if(a.length<2){sendTimelineUsage(p);return;} String op=a[1].toLowerCase(Locale.ROOT); if((op.equals("undo")||op.equals("redo"))){require(a,3,"/aurareplay timeline "+op+" <scene>"); Scene sc=engine.sceneManager().get(a[2]).orElseThrow(()->new IllegalArgumentException("Scene not found: "+a[2])); TimelineEditor e=engine.sceneTimelineService().editor(sc); p.sendMessage((op.equals("undo")?e.undo(sc.timeline()):e.redo(sc.timeline()))?ChatColor.GREEN+"Timeline "+op+" applied.":ChatColor.YELLOW+"Nothing to "+op+".");return;} require(a,3,"/aurareplay timeline <operation> <scene> ..."); Scene sc=engine.sceneManager().get(a[2]).orElseThrow(()->new IllegalArgumentException("Scene not found: "+a[2])); Timeline t=sc.timeline(); TimelineEditor e=engine.sceneTimelineService().editor(sc); try{switch(op){case "info"->timelineInfo(p,sc);case "range"-> {require(a,5,"range");e.setRange(t,Long.parseLong(a[3]),Long.parseLong(a[4]));}case "speed"-> {require(a,4,"speed");e.setSpeed(t,Double.parseDouble(a[3]));}case "loop"-> {require(a,4,"loop");e.setLoop(t,parseBoolean(a[3]));}case "reverse"-> {require(a,4,"reverse");e.setReverse(t,parseBoolean(a[3]));}case "offset"-> {require(a,4,"offset");e.offset(t,Long.parseLong(a[3]));}case "stretch"-> {require(a,4,"stretch");e.timeStretch(t,Double.parseDouble(a[3]));}case "marker"-> {require(a,5,"marker"); if(a[3].equalsIgnoreCase("add")){require(a,7,"marker add");e.addMarker(t,new TimelineMarker(a[5],Long.parseLong(a[4]),join(a,6)));}else e.removeMarker(t,a[4]);}case "keyframe"-> {require(a,5,"keyframe");if(a[3].equalsIgnoreCase("add")){require(a,7,"keyframe add");e.addKeyframe(t,new TimelineKeyframe(Long.parseLong(a[4]),a[5],join(a,6)));}else e.removeKeyframe(t,Long.parseLong(a[4]),a[5]);}default->sendTimelineUsage(p);}}catch(IllegalArgumentException x){p.sendMessage(ChatColor.RED+x.getMessage());}}
    private void timelineInfo(Player p,Scene sc){Timeline t=sc.timeline();p.sendMessage(ChatColor.GOLD+"=== Timeline: "+sc.name()+" ===");p.sendMessage(ChatColor.GRAY+"Duration: "+t.durationTicks()+" | Range: "+t.inPoint()+" -> "+t.outPoint());p.sendMessage(ChatColor.GRAY+"Speed: "+t.playbackSpeed()+" | Loop: "+t.loop()+" | Reverse: "+t.reverse());p.sendMessage(ChatColor.GRAY+"Markers: "+t.markers().size()+" | Keyframes: "+t.keyframes().size());}
    private void sendTimelineUsage(CommandSender s){s.sendMessage(ChatColor.YELLOW+"/aurareplay timeline info|range|speed|loop|reverse|offset|stretch|marker|keyframe|undo|redo <scene> ...");}
    private static boolean parseBoolean(String v){if(!v.equalsIgnoreCase("true")&&!v.equalsIgnoreCase("false"))throw new IllegalArgumentException("value must be true or false");return Boolean.parseBoolean(v);}
    private static void require(String[] a,int n,String usage){if(a.length<n)throw new IllegalArgumentException(usage);}
    private static String join(String[] a,int start){return String.join(" ",Arrays.copyOfRange(a,start,a.length));}
    private void sendUsage(CommandSender s){s.sendMessage(ChatColor.YELLOW+"/aurareplay <studio|record|stop|recordings|actor|actors|scene|timeline|camera|version>");}

    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a){
        if(a.length==1)return partial(a[0],List.of("studio","record","stop","recordings","list","actor","actors","scene","timeline","camera","version"));
        if(a.length==2&&a[0].equalsIgnoreCase("actor"))return partial(a[1],List.of("spawn","stop"));
        if(a.length==3&&a[0].equalsIgnoreCase("actor")&&a[1].equalsIgnoreCase("spawn"))return engine.recordingManager().all().stream().map(r->r.name()).toList();
        if(a.length==2&&a[0].equalsIgnoreCase("scene"))return partial(a[1],List.of("create","add","remove","list","play","stop"));
        if(a.length==3&&a[0].equalsIgnoreCase("scene")&&List.of("add","remove","play").contains(a[1].toLowerCase(Locale.ROOT)))return engine.sceneManager().all().stream().map(Scene::name).toList();
        if(a.length==4&&a[0].equalsIgnoreCase("scene")&&List.of("add","remove").contains(a[1].toLowerCase(Locale.ROOT)))return engine.actorManager().all().stream().map(x->x.id().toString()).toList();
        if(a.length==2&&a[0].equalsIgnoreCase("timeline"))return partial(a[1],List.of("info","range","speed","loop","reverse","offset","stretch","marker","keyframe","undo","redo"));
        if(a.length==3&&a[0].equalsIgnoreCase("timeline")&&!List.of("marker","keyframe").contains(a[1].toLowerCase(Locale.ROOT)))return engine.sceneManager().all().stream().map(Scene::name).toList();
        if(a.length==3&&a[0].equalsIgnoreCase("timeline")&&List.of("marker","keyframe").contains(a[1].toLowerCase(Locale.ROOT)))return partial(a[2],List.of("add","remove"));
        if(a.length==4&&a[0].equalsIgnoreCase("timeline")&&List.of("marker","keyframe").contains(a[1].toLowerCase(Locale.ROOT)))return engine.sceneManager().all().stream().map(Scene::name).toList();
        if(a.length==2&&a[0].equalsIgnoreCase("camera"))return partial(a[1],List.of("create","list","delete","play","stop","position","rotation","fov","keyframe","follow","headtrack"));
        if(a.length==3&&a[0].equalsIgnoreCase("camera")&&List.of("delete","play","position","rotation","fov","keyframe","follow","headtrack").contains(a[1].toLowerCase(Locale.ROOT)))return engine.cameraManager().all().stream().map(CameraDefinition::id).toList();
        if(a.length==4&&a[0].equalsIgnoreCase("camera")&&a[1].equalsIgnoreCase("keyframe"))return partial(a[3],List.of("add","remove","list"));
        return List.of();
    }
    private List<String> partial(String in,List<String> values){return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(in.toLowerCase(Locale.ROOT))).toList();}
}
