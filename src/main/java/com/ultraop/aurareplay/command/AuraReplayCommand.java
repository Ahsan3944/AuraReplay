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
            sender.sendMessage(ChatColor.GOLD + "AuraReplay Studio foundation is active.");
            sender.sendMessage(ChatColor.GRAY + "Recording engine: " + (engine.tickRecorder().isRecording() ? "RUNNING" : "IDLE"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "record" -> handleRecord(sender, args);
            case "stop" -> {
                engine.tickRecorder().stop();
                sender.sendMessage(ChatColor.YELLOW + "AuraReplay recording stopped.");
            }
            case "version" -> sender.sendMessage(ChatColor.AQUA + "AuraReplay 0.1.0-SNAPSHOT | Paper 1.21.11");
            default -> sender.sendMessage(ChatColor.YELLOW + "/aurareplay <studio|record|stop|version>");
        }
        return true;
    }

    private void handleRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Recording must be started by a player in phase 1.");
            return;
        }
        if (!player.hasPermission("aurareplay.record")) {
            sender.sendMessage(ChatColor.RED + "You do not have recording permission.");
            return;
        }

        engine.tickRecorder().track(player);
        engine.tickRecorder().start();
        sender.sendMessage(ChatColor.GREEN + "AuraReplay recording started. Your actor is now being captured.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("studio", "record", "stop", "version"));
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
