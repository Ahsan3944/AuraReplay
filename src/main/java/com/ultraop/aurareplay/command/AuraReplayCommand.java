package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuraReplayCommand implements CommandExecutor, TabCompleter {
    private final AuraEngine engine;

    public AuraReplayCommand(AuraEngine engine) { this.engine = engine; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aurareplay.use")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use AuraReplay.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("studio")) {
            showStudio(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "record" -> handleRecord(sender);
            case "stop" -> handleStop(sender, args);
            case "recordings", "list" -> handleList(sender);
            case "actor" -> handleActor(sender, args);
            case "actors" -> handleActors(sender);
            case "scene" -> handleScene(sender, args);
            case "version" -> sender.sendMessage(ChatColor.AQUA + "AuraReplay 0.1.0-SNAPSHOT | Paper 1.21.11");
            default -> sendUsage(sender);
        }
        return true;
    }

    private void showStudio(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AuraReplay Studio ===");
        sender.sendMessage(ChatColor.GRAY + "Recording: " + (engine.tickRecorder().isRecording() ? "RUNNING" : "IDLE"));
        sender.sendMessage(ChatColor.GRAY + "Saved captures: " + engine.recordingManager().all().size());
        sender.sendMessage(ChatColor.GRAY + "Actors: " + engine.actorManager().all().size());
        sender.sendMessage(ChatColor.GRAY + "Scenes: " + engine.sceneManager().all().size());
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay record");
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay stop [name]");
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay recordings");
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay actor spawn <recording>");
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay scene <create|add|list|play|stop>");
    }

    private void handleRecord(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Recording must be started by a player.");
            return;
        }
        if (!player.hasPermission("aurareplay.record")) {
            sender.sendMessage(ChatColor.RED + "You do not have recording permission.");
            return;
        }
        if (engine.tickRecorder().isRecording()) {
            sender.sendMessage(ChatColor.RED + "A recording is already active.");
            return;
        }
        engine.tickRecorder().track(player);
        engine.tickRecorder().start();
        sender.sendMessage(ChatColor.GREEN + "Recording started.");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!engine.tickRecorder().isRecording()) {
            sender.sendMessage(ChatColor.YELLOW + "No recording is active.");
            return;
        }
        String name = args.length >= 2 ? args[1] : "capture-" + System.currentTimeMillis();
        var recording = engine.tickRecorder().stop(name);
        if (recording != null) {
            engine.recordingManager().register(recording);
            sender.sendMessage(ChatColor.GREEN + "Saved recording '" + recording.name() + "' with " + recording.durationTicks() + " ticks.");
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AuraReplay Recordings ===");
        if (engine.recordingManager().all().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No recordings available.");
            return;
        }
        engine.recordingManager().all().forEach(recording -> sender.sendMessage(
                ChatColor.YELLOW + recording.name() + ChatColor.GRAY + " — " + recording.durationTicks() + " ticks"));
    }

    private void handleActor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Actor commands must be run by a player.");
            return;
        }
        if (!player.hasPermission("aurareplay.actor")) {
            sender.sendMessage(ChatColor.RED + "You do not have actor permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/aurareplay actor <spawn|stop>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "spawn" -> spawnActor(player, args);
            case "stop" -> stopActor(player, args);
            default -> sender.sendMessage(ChatColor.YELLOW + "/aurareplay actor <spawn|stop>");
        }
    }

    private void spawnActor(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "/aurareplay actor spawn <recording>");
            return;
        }
        var recording = engine.recordingManager().get(args[2]);
        if (recording.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Recording not found: " + args[2]);
            return;
        }
        var frames = recording.get().frames();
        if (frames.isEmpty() || frames.get(0).entities().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Recording contains no entity data.");
            return;
        }
        var source = frames.get(0).entities().get(0);
        var location = player.getLocation();
        ActorTransform transform = ActorTransform.origin(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        ActorDefinition actor = engine.actorManager().create(recording.get(), transform, source.uuid(), source.entityId());
        actor.setName(recording.get().name());
        engine.actorPlaybackController().start(player, actor);
        player.sendMessage(ChatColor.GREEN + "Actor spawned: " + actor.id());
    }

    private void stopActor(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "/aurareplay actor stop <id>");
            return;
        }
        try {
            ActorId id = new ActorId(UUID.fromString(args[2]));
            var actor = engine.actorManager().get(id);
            if (actor.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Actor not found: " + args[2]);
                return;
            }
            engine.actorPlaybackController().stop(player, id);
            engine.actorManager().remove(id);
            player.sendMessage(ChatColor.GREEN + "Actor stopped.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + "Invalid actor UUID.");
        }
    }

    private void handleActors(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AuraReplay Actors ===");
        if (engine.actorManager().all().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No actors created.");
            return;
        }
        engine.actorManager().all().forEach(actor -> sender.sendMessage(
                ChatColor.YELLOW + actor.id().toString() + ChatColor.GRAY + " — " + actor.name()));
    }

    private void handleScene(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Scene commands must be run by a player.");
            return;
        }
        if (!player.hasPermission("aurareplay.scene")) {
            sender.sendMessage(ChatColor.RED + "You do not have scene permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/aurareplay scene <create|add|remove|list|play|stop>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> createScene(player, args);
            case "add" -> addSceneActor(player, args, false);
            case "remove" -> addSceneActor(player, args, true);
            case "list" -> listScenes(player);
            case "play" -> playScene(player, args);
            case "stop" -> stopScene(player);
            default -> player.sendMessage(ChatColor.YELLOW + "/aurareplay scene <create|add|remove|list|play|stop>");
        }
    }

    private void createScene(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "/aurareplay scene create <name>");
            return;
        }
        if (engine.sceneManager().get(args[2]).isPresent()) {
            player.sendMessage(ChatColor.RED + "Scene already exists: " + args[2]);
            return;
        }
        Scene scene = engine.sceneManager().create(args[2]);
        player.sendMessage(ChatColor.GREEN + "Scene created: " + scene.name());
    }

    private void addSceneActor(Player player, String[] args, boolean remove) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.YELLOW + "/aurareplay scene " + (remove ? "remove" : "add") + " <scene> <actor-id>");
            return;
        }
        try {
            ActorId actorId = new ActorId(UUID.fromString(args[3]));
            if (engine.actorManager().get(actorId).isEmpty()) {
                player.sendMessage(ChatColor.RED + "Actor not found: " + args[3]);
                return;
            }
            boolean changed = remove
                    ? engine.sceneManager().removeActor(args[2], actorId)
                    : engine.sceneManager().addActor(args[2], actorId);
            player.sendMessage((changed ? ChatColor.GREEN : ChatColor.YELLOW) +
                    (changed ? "Scene updated." : "No change made."));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + "Invalid actor UUID.");
        }
    }

    private void listScenes(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== AuraReplay Scenes ===");
        if (engine.sceneManager().all().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No scenes available.");
            return;
        }
        engine.sceneManager().all().forEach(scene -> player.sendMessage(
                ChatColor.YELLOW + scene.name() + ChatColor.GRAY + " — " + scene.actorIds().size() + " actors"));
    }

    private void playScene(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "/aurareplay scene play <name>");
            return;
        }
        try {
            int count = engine.sceneManager().play(player, args[2]);
            player.sendMessage(ChatColor.GREEN + "Scene playing: " + args[2] + ChatColor.GRAY + " (" + count + " actors)");
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private void stopScene(Player player) {
        if (engine.sceneManager().stop(player)) player.sendMessage(ChatColor.GREEN + "Scene stopped.");
        else player.sendMessage(ChatColor.YELLOW + "No scene is playing.");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay <studio|record|stop|recordings|actor|actors|scene|version>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], List.of("studio", "record", "stop", "recordings", "list", "actor", "actors", "scene", "version"));
        if (args.length == 2 && args[0].equalsIgnoreCase("actor")) return partial(args[1], List.of("spawn", "stop"));
        if (args.length == 3 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("spawn")) return engine.recordingManager().all().stream().map(r -> r.name()).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("stop")) return engine.actorManager().all().stream().map(a -> a.id().toString()).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("scene")) return partial(args[1], List.of("create", "add", "remove", "list", "play", "stop"));
        if (args.length == 3 && args[0].equalsIgnoreCase("scene") && List.of("add", "remove").contains(args[1].toLowerCase())) return engine.sceneManager().all().stream().map(Scene::name).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("scene") && List.of("play").contains(args[1].toLowerCase())) return engine.sceneManager().all().stream().map(Scene::name).toList();
        if (args.length == 4 && args[0].equalsIgnoreCase("scene") && List.of("add", "remove").contains(args[1].toLowerCase())) return engine.actorManager().all().stream().map(a -> a.id().toString()).toList();
        return List.of();
    }

    private List<String> partial(String input, List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase().startsWith(input.toLowerCase())) result.add(value);
        return result;
    }
}
