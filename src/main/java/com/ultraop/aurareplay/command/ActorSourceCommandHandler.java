package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.RecordingSource;
import com.ultraop.aurareplay.recording.RecordingSourceCatalog;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Command-side source browser and actor factory. */
public final class ActorSourceCommandHandler {
    private static final RecordingSourceCatalog CATALOG = new RecordingSourceCatalog();

    private ActorSourceCommandHandler() {}

    public static boolean handle(AuraEngine engine, CommandSender sender, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("actor")) return false;
        if (!sender.hasPermission("aurareplay.actor")) {
            sender.sendMessage(ChatColor.RED + "You do not have actor permission.");
            return true;
        }
        if (!args[1].equalsIgnoreCase("sources") && !args[1].equalsIgnoreCase("source")) return false;

        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "/aurareplay actor sources <recording>");
            sender.sendMessage(ChatColor.YELLOW + "/aurareplay actor source <recording> spawn <source-key>");
            return true;
        }

        Recording recording = engine.recordingManager().get(args[2]).orElse(null);
        if (recording == null) {
            sender.sendMessage(ChatColor.RED + "Recording not found: " + args[2]);
            return true;
        }

        if (args[1].equalsIgnoreCase("sources")) {
            listSources(sender, recording);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Source actors must be spawned by a player.");
            return true;
        }
        if (args.length < 5 || !args[3].equalsIgnoreCase("spawn")) {
            player.sendMessage(ChatColor.YELLOW + "/aurareplay actor source <recording> spawn <source-key>");
            return true;
        }

        RecordingSource source = CATALOG.find(recording, args[4]).orElse(null);
        if (source == null) {
            player.sendMessage(ChatColor.RED + "Recording source not found: " + args[4]);
            return true;
        }

        var location = player.getLocation();
        ActorDefinition actor = engine.actorManager().createFromSource(
                recording,
                source,
                ActorTransform.origin(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())
        );
        actor.setName(source.displayName());
        engine.actorPlaybackController().start(player, actor);
        player.sendMessage(ChatColor.GREEN + "Actor created from source " + source.key() + ": " + actor.id());
        return true;
    }

    public static List<String> complete(AuraEngine engine, String[] args) {
        if (args.length == 2) return List.of("sources", "source");
        if (args.length == 3 && (args[1].equalsIgnoreCase("sources") || args[1].equalsIgnoreCase("source"))) {
            return engine.recordingManager().all().stream().map(Recording::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("source")) return List.of("spawn");
        if (args.length == 5 && args[1].equalsIgnoreCase("source") && args[3].equalsIgnoreCase("spawn")) {
            Recording recording = engine.recordingManager().get(args[2]).orElse(null);
            if (recording == null) return List.of();
            return CATALOG.sources(recording).stream().map(RecordingSource::key).toList();
        }
        return new ArrayList<>();
    }

    private static void listSources(CommandSender sender, Recording recording) {
        List<RecordingSource> sources = CATALOG.sources(recording);
        sender.sendMessage(ChatColor.GOLD + "=== Sources: " + recording.name() + " ===");
        if (sources.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No entity sources found.");
            return;
        }
        for (int i = 0; i < sources.size(); i++) {
            RecordingSource source = sources.get(i);
            sender.sendMessage(ChatColor.YELLOW + "[" + i + "] " + source.displayName()
                    + ChatColor.GRAY + " — " + source.type().name() + " — " + source.key());
        }
    }
}
