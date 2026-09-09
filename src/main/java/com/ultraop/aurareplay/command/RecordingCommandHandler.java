package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Persistent recording command surface layered on top of the main AuraReplay command. */
public final class RecordingCommandHandler {
    private RecordingCommandHandler() { }

    public static boolean handle(AuraEngine engine, CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("stop")) return stop(engine, sender, args);
        if (root.equals("list") || root.equals("recordings")) return list(engine, sender);
        if (!root.equals("recording")) return false;
        if (!sender.hasPermission("aurareplay.record")) {
            sender.sendMessage(ChatColor.RED + "You do not have recording permission.");
            return true;
        }
        if (args.length < 2) {
            usage(sender);
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> list(engine, sender);
            case "save" -> save(engine, sender, args);
            case "load" -> load(engine, sender, args);
            case "delete" -> delete(engine, sender, args);
            default -> { usage(sender); yield true; }
        };
    }

    private static boolean stop(AuraEngine engine, CommandSender sender, String[] args) {
        if (!engine.tickRecorder().isRecording()) {
            sender.sendMessage(ChatColor.YELLOW + "No recording is active.");
            return true;
        }
        String name = args.length >= 2 ? args[1] : "capture-" + System.currentTimeMillis();
        var recording = engine.tickRecorder().stop(name);
        if (recording == null) return true;
        engine.recordingManager().register(recording);
        sender.sendMessage(ChatColor.YELLOW + "Recording captured. Persisting '" + recording.name() + "' asynchronously...");
        engine.recordingManager().saveAsync(recording.name()).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(engine.plugin(), () -> {
                    if (error == null) sender.sendMessage(ChatColor.GREEN + "Recording saved: " + recording.name());
                    else sender.sendMessage(ChatColor.RED + "Recording save failed: " + rootMessage(error));
                }));
        return true;
    }

    private static boolean list(AuraEngine engine, CommandSender sender) {
        engine.recordingManager().all().stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .forEach(r -> sender.sendMessage(ChatColor.YELLOW + r.name() + ChatColor.GRAY + " — " + r.durationTicks() + " ticks, " + r.frames().size() + " frames"));
        return true;
    }

    private static boolean save(AuraEngine engine, CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender); return true; }
        String name = args[2];
        if (engine.recordingManager().get(name).isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Recording not loaded: " + name);
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Saving recording '" + name + "'...");
        engine.recordingManager().saveAsync(name).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(engine.plugin(), () ->
                        sender.sendMessage(error == null ? ChatColor.GREEN + "Recording saved: " + name
                                : ChatColor.RED + "Recording save failed: " + rootMessage(error))));
        return true;
    }

    private static boolean load(AuraEngine engine, CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender); return true; }
        String name = args[2];
        sender.sendMessage(ChatColor.YELLOW + "Loading recording '" + name + "'...");
        engine.recordingManager().loadAsync(name).whenComplete((recording, error) ->
                Bukkit.getScheduler().runTask(engine.plugin(), () -> {
                    if (error == null) sender.sendMessage(ChatColor.GREEN + "Recording loaded: " + recording.name() + " (" + recording.frames().size() + " frames)");
                    else sender.sendMessage(ChatColor.RED + "Recording load failed: " + rootMessage(error));
                }));
        return true;
    }

    private static boolean delete(AuraEngine engine, CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender); return true; }
        String name = args[2];
        sender.sendMessage(ChatColor.YELLOW + "Deleting recording '" + name + "'...");
        engine.recordingManager().deletePersistentAsync(name).whenComplete((deleted, error) ->
                Bukkit.getScheduler().runTask(engine.plugin(), () -> {
                    if (error != null) sender.sendMessage(ChatColor.RED + "Recording delete failed: " + rootMessage(error));
                    else if (deleted) sender.sendMessage(ChatColor.GREEN + "Recording deleted: " + name);
                    else sender.sendMessage(ChatColor.YELLOW + "Recording not found: " + name);
                }));
        return true;
    }

    public static List<String> complete(String[] args) {
        if (args.length == 1) return partial(args[0], "recording", "recordings", "list", "stop");
        if (args[0].equalsIgnoreCase("recording") && args.length == 2) return partial(args[1], "list", "save", "load", "delete");
        return List.of();
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay recording <list|save|load|delete> <name>");
    }

    private static List<String> partial(String value, String... options) {
        return Arrays.stream(options).filter(option -> option.startsWith(value.toLowerCase(Locale.ROOT))).toList();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
