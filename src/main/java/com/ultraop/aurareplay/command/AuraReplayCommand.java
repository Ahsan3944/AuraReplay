package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraKeyframe;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AuraReplayCommand implements CommandExecutor, TabCompleter {
    private final AuraEngine engine;

    public AuraReplayCommand(AuraEngine engine) { this.engine = engine; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aurareplay.use")) { sender.sendMessage(ChatColor.RED + "You do not have permission to use AuraReplay."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("studio")) { showStudio(sender); return true; }
        try {
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
        } catch (IllegalArgumentException e) { sender.sendMessage(ChatColor.RED + e.getMessage()); }
        return true;
    }

    private void showStudio(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== AuraReplay Studio ===");
        s.sendMessage(ChatColor.GRAY + "Recording: " + (engine.tickRecorder().isRecording() ? "RUNNING" : "IDLE"));
        s.sendMessage(ChatColor.GRAY + "Saved captures: " + engine.recordingManager().all().size());
        s.sendMessage(ChatColor.GRAY + "Actors: " + engine.actorManager().all().size());
        s.sendMessage(ChatColor.GRAY + "Scenes: " + engine.sceneManager().all().size());
        s.sendMessage(ChatColor.GRAY + "Cameras: " + engine.cameraManager().all().size());
        s.sendMessage(ChatColor.YELLOW + "/aurareplay scene <create|add|remove|list|play|stop|pause|resume|seek|step|status|camera>");
        s.sendMessage(ChatColor.YELLOW + "/aurareplay camera <create|list|delete|play|stop|position|rotation|fov|keyframe|follow|headtrack>");
    }

    private void handleRecord(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Recording must be started by a player."); return; }
        if (!p.hasPermission("aurareplay.record")) { p.sendMessage(ChatColor.RED + "You do not have recording permission."); return; }
        if (engine.tickRecorder().isRecording()) { p.sendMessage(ChatColor.RED + "A recording is already active."); return; }
        engine.tickRecorder().track(p); engine.tickRecorder().start(); p.sendMessage(ChatColor.GREEN + "Recording started.");
    }

    private void handleStop(CommandSender s, String[] a) {
        if (!engine.tickRecorder().isRecording()) { s.sendMessage(ChatColor.YELLOW + "No recording is active."); return; }
        var r = engine.tickRecorder().stop(a.length >= 2 ? a[1] : "capture-" + System.currentTimeMillis());
        if (r != null) { engine.recordingManager().register(r); s.sendMessage(ChatColor.GREEN + "Saved recording '" + r.name() + "' with " + r.durationTicks() + " ticks."); }
    }

    private void handleList(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== AuraReplay Recordings ===");
        engine.recordingManager().all().forEach(r -> s.sendMessage(ChatColor.YELLOW + r.name() + ChatColor.GRAY + " — " + r.durationTicks() + " ticks"));
    }

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
            try { ActorId id = new ActorId(UUID.fromString(a[2])); engine.actorPlaybackController().stop(p, id); engine.actorManager().remove(id); p.sendMessage(ChatColor.GREEN + "Actor stopped."); }
            catch (IllegalArgumentException e) { p.sendMessage(ChatColor.RED + "Invalid actor UUID."); }
        }
    }

    private void handleActors(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== AuraReplay Actors ===");
        engine.actorManager().all().forEach(a -> s.sendMessage(ChatColor.YELLOW.toString() + a.id() + ChatColor.GRAY + " — " + a.name()));
    }

    private void handleScene(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (!p.hasPermission("aurareplay.scene")) { p.sendMessage(ChatColor.RED + "You do not have scene permission."); return; }
        if (a.length < 2) { sceneUsage(p); return; }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                require(a, 3, "/aurareplay scene create <name>");
                p.sendMessage(ChatColor.GREEN + "Scene created: " + engine.sceneManager().create(a[2]).name());
            }
            case "add", "remove" -> {
                require(a, 4, "/aurareplay scene " + a[1] + " <scene> <actor-id>");
                try {
                    ActorId id = new ActorId(UUID.fromString(a[3]));
                    boolean changed = a[1].equalsIgnoreCase("remove") ? engine.sceneManager().removeActor(a[2], id) : engine.sceneManager().addActor(a[2], id);
                    p.sendMessage((changed ? ChatColor.GREEN : ChatColor.YELLOW) + (changed ? "Scene updated." : "No change made."));
                } catch (IllegalArgumentException e) { p.sendMessage(ChatColor.RED + "Invalid actor UUID."); }
            }
            case "camera" -> handleSceneCamera(p, a);
            case "list" -> engine.sceneManager().all().forEach(sc -> p.sendMessage(ChatColor.YELLOW + sc.name() + ChatColor.GRAY + " — " + sc.actorIds().size() + " actors | camera: " + engine.sceneManager().cameraId(sc.name()).orElse("none")));
            case "play" -> { require(a, 3, "/aurareplay scene play <name>"); p.sendMessage(ChatColor.GREEN + "Scene playing: " + a[2] + " (" + engine.sceneManager().play(p, a[2]) + " actors)"); }
            case "stop" -> p.sendMessage(engine.sceneManager().stop(p) ? ChatColor.GREEN + "Scene stopped." : ChatColor.YELLOW + "No scene is playing.");
            case "pause" -> p.sendMessage(engine.sceneManager().pause(p) ? ChatColor.YELLOW + "Scene paused at tick " + tick(p) + "." : ChatColor.RED + "No scene is playing.");
            case "resume" -> p.sendMessage(engine.sceneManager().resume(p) ? ChatColor.GREEN + "Scene resumed." : ChatColor.RED + "No scene is playing.");
            case "seek" -> { require(a, 3, "/aurareplay scene seek <tick>"); double target = Double.parseDouble(a[2]); p.sendMessage(engine.sceneManager().seek(p, target) ? ChatColor.GREEN + "Scene seeked to tick " + tick(p) + "." : ChatColor.RED + "No scene is playing."); }
            case "step" -> { require(a, 3, "/aurareplay scene step <ticks>"); double delta = Double.parseDouble(a[2]); p.sendMessage(engine.sceneManager().step(p, delta) ? ChatColor.GREEN + "Scene stepped to tick " + tick(p) + "." : ChatColor.RED + "No scene is playing."); }
            case "status" -> {
                var active = engine.sceneManager().activeScene(p);
                if (active.isEmpty()) p.sendMessage(ChatColor.YELLOW + "No scene is playing.");
                else p.sendMessage(ChatColor.AQUA + "Scene: " + active.get() + ChatColor.GRAY + " | tick: " + tick(p) + " | " + (engine.sceneManager().paused(p) ? "PAUSED" : "PLAYING"));
            }
            default -> sceneUsage(p);
        }
    }

    private double tick(Player p) { return engine.sceneManager().currentTick(p).orElse(0.0); }

    private void handleSceneCamera(Player p, String[] a) {
        if (a.length < 3) { p.sendMessage(ChatColor.YELLOW + "/aurareplay scene camera <bind|unbind|show> <scene> [camera]"); return; }
        String op = a[2].toLowerCase(Locale.ROOT);
        switch (op) {
            case "bind" -> { require(a, 5, "/aurareplay scene camera bind <scene> <camera>"); if (engine.sceneManager().bindCamera(a[3], a[4])) p.sendMessage(ChatColor.GREEN + "Camera '" + a[4] + "' bound to scene '" + a[3] + "'."); else p.sendMessage(ChatColor.RED + "Scene or camera not found."); }
            case "unbind" -> { require(a, 4, "/aurareplay scene camera unbind <scene>"); p.sendMessage(engine.sceneManager().unbindCamera(a[3]) ? ChatColor.GREEN + "Scene camera unbound." : ChatColor.YELLOW + "No camera binding found."); }
            case "show" -> { require(a, 4, "/aurareplay scene camera show <scene>"); p.sendMessage(ChatColor.YELLOW + "Scene camera: " + engine.sceneManager().cameraId(a[3]).orElse("none")); }
            default -> p.sendMessage(ChatColor.YELLOW + "/aurareplay scene camera <bind|unbind|show> <scene> [camera]");
        }
    }

    private void handleCamera(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Camera commands must be run by a player."); return; }
        if (!p.hasPermission("aurareplay.camera")) { p.sendMessage(ChatColor.RED + "You do not have camera permission."); return; }
        if (a.length < 2) { cameraUsage(p); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        try {
            switch (op) {
                case "create" -> { require(a, 3, "/aurareplay camera create <name>"); var l = p.getLocation(); CameraDefinition c = engine.cameraManager().create(a[2], a[2], CameraTransform.origin(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch())); p.sendMessage(ChatColor.GREEN + "Camera created: " + c.id()); }
                case "list" -> engine.cameraManager().all().forEach(c -> p.sendMessage(ChatColor.YELLOW + c.id() + ChatColor.GRAY + " — " + c.name() + " | " + c.keyframes().size() + " keyframes"));
                case "delete" -> { require(a, 3, "/aurareplay camera delete <name>"); p.sendMessage(engine.cameraManager().remove(a[2]) ? ChatColor.GREEN + "Camera deleted." : ChatColor.YELLOW + "Camera not found."); }
                case "play" -> { require(a, 3, "/aurareplay camera play <name>"); var c = engine.cameraManager().get(a[2]).orElseThrow(() -> new IllegalArgumentException("camera not found: " + a[2])); engine.cameraController().start(p, c); p.sendMessage(ChatColor.GREEN + "Camera playing: " + c.name()); }
                case "stop" -> { engine.cameraController().stop(p); p.sendMessage(ChatColor.GREEN + "Camera stopped."); }
                case "position" -> { require(a, 6, "/aurareplay camera position <name> <x> <y> <z>"); var c = getCamera(a[2]); c.setTransform(c.transform().withPosition(Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5]))); p.sendMessage(ChatColor.GREEN + "Camera position updated."); }
                case "rotation" -> { require(a, 5, "/aurareplay camera rotation <name> <yaw> <pitch>"); var c = getCamera(a[2]); c.setTransform(c.transform().withRotation(Float.parseFloat(a[3]), Float.parseFloat(a[4]), c.transform().roll())); p.sendMessage(ChatColor.GREEN + "Camera rotation updated."); }
                case "fov" -> { require(a, 4, "/aurareplay camera fov <name> <value>"); var c = getCamera(a[2]); c.setTransform(c.transform().withFov(Float.parseFloat(a[3]))); p.sendMessage(ChatColor.GREEN + "Camera FOV updated."); }
                case "keyframe" -> handleCameraKeyframe(p, a);
                case "follow" -> { require(a, 4, "/aurareplay camera follow <name> <actor-id>"); var c = getCamera(a[2]); c.setFollowActorId(a[3]); p.sendMessage(ChatColor.GREEN + "Camera follow target set."); }
                case "headtrack" -> { require(a, 3, "/aurareplay camera headtrack <name> [true|false]"); var c = getCamera(a[2]); boolean enabled = a.length < 4 || Boolean.parseBoolean(a[3]); c.setHeadTrack(enabled); p.sendMessage(ChatColor.GREEN + "Camera head tracking: " + enabled); }
                default -> cameraUsage(p);
            }
        } catch (NumberFormatException e) { p.sendMessage(ChatColor.RED + "Invalid numeric value."); }
    }

    private void handleCameraKeyframe(Player p, String[] a) {
        require(a, 5, "/aurareplay camera keyframe <add|remove> <name> <tick>");
        CameraDefinition c = getCamera(a[3]); long tick = Long.parseLong(a[4]);
        if (a[2].equalsIgnoreCase("add")) { c.addKeyframe(new CameraKeyframe(tick, c.transform())); p.sendMessage(ChatColor.GREEN + "Camera keyframe added at tick " + tick + "."); }
        else if (a[2].equalsIgnoreCase("remove")) p.sendMessage(c.removeKeyframe(tick) ? ChatColor.GREEN + "Camera keyframe removed." : ChatColor.YELLOW + "Keyframe not found.");
        else p.sendMessage(ChatColor.YELLOW + "/aurareplay camera keyframe <add|remove> <name> <tick>");
    }

    private CameraDefinition getCamera(String id) { return engine.cameraManager().get(id).orElseThrow(() -> new IllegalArgumentException("camera not found: " + id)); }
    private void cameraUsage(Player p) { p.sendMessage(ChatColor.YELLOW + "/aurareplay camera <create|list|delete|play|stop|position|rotation|fov|keyframe|follow|headtrack>"); }
    private void sceneUsage(Player p) { p.sendMessage(ChatColor.YELLOW + "/aurareplay scene <create|add|remove|list|play|stop|pause|resume|seek|step|status|camera>"); }

    private void handleTimeline(CommandSender s, String[] a) { s.sendMessage(ChatColor.GRAY + "Timeline editor foundation is available through the Studio API."); }
    private void sendUsage(CommandSender s) { s.sendMessage(ChatColor.YELLOW + "/aurareplay <record|stop|list|actor|actors|scene|timeline|camera|studio>"); }
    private static void require(String[] a, int length, String usage) { if (a.length < length) throw new IllegalArgumentException(usage); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], "record", "stop", "list", "recordings", "actor", "actors", "scene", "timeline", "camera", "studio", "version");
        if (args[0].equalsIgnoreCase("scene") && args.length == 3) return partial(args[2], "create", "add", "remove", "list", "play", "stop", "pause", "resume", "seek", "step", "status", "camera");
        if (args[0].equalsIgnoreCase("scene") && args.length == 4 && args[2].equalsIgnoreCase("camera")) return partial(args[3], "bind", "unbind", "show");
        if (args[0].equalsIgnoreCase("camera") && args.length == 3) return partial(args[2], "create", "list", "delete", "play", "stop", "position", "rotation", "fov", "keyframe", "follow", "headtrack");
        return Collections.emptyList();
    }

    private static List<String> partial(String value, String... options) { return Arrays.stream(options).filter(v -> v.toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT))).toList(); }
}
