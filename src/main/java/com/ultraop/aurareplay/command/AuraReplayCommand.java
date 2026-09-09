package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AuraReplayCommand implements CommandExecutor, TabCompleter {

    private final AuraEngine engine;

    public AuraReplayCommand(AuraEngine engine) {
        this.engine = engine;
    }

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
            case "record" -> handleRecord(sender, args);
            case "stop" -> handleStop(sender, args);
            case "recordings", "list" -> handleList(sender);
            case "version" -> sender.sendMessage(ChatColor.AQUA + "AuraReplay 0.1.0-SNAPSHOT | Paper 1.21.11");
            default -> sendUsage(sender);
        }
        return true;
    }

    private void showStudio(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AuraReplay Studio ===");
        sender.sendMessage(ChatColor.GRAY + "Recording: " + (engine.tickRecorder().isRecording() ? "RUNNING" : "IDLE"));
        sender.sendMessage(ChatColor.GRAY + "Saved captures: " + engine.recordingManager().all().size());
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay record <name>");
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay stop [name]");
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay recordings");
    }

    private void handleRecord(CommandSender sender, String[] args) {
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
            sender.sendMessage(ChatColor.GREEN + "Saved recording '" + recording.name() + "' with "
                    + recording.durationTicks() + " ticks.");
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AuraReplay Recordings ===");
        if (engine.recordingManager().all().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No recordings available.");
            return;
        }
        engine.recordingManager().all().forEach(recording ->
                sender.sendMessage(ChatColor.YELLOW + recording.name()
                        + ChatColor.GRAY + " — " + recording.durationTicks() + " ticks"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/aurareplay <studio|record <name>|stop [name]|recordings|version>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("studio", "record", "stop", "recordings", "list", "version"));
        }
        return List.of();
    }

    private List<String> partial(String input, List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(input.toLowerCase())) result.add(value);
        }
        return result;
    }
}
