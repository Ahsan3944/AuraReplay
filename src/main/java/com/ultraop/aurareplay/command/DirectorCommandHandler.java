package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Handles the command entry point for Director Studio. */
public final class DirectorCommandHandler {
    private DirectorCommandHandler() { }

    public static boolean handle(AuraEngine engine, CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("director")) return false;
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Director Studio must be opened by a player."); return true; }
        if (!player.hasPermission("aurareplay.camera")) { player.sendMessage(ChatColor.RED + "You do not have Director permission."); return true; }
        if (args.length < 2) { player.sendMessage(ChatColor.YELLOW + "/aurareplay director <scene>"); return true; }
        Scene scene = engine.sceneManager().get(args[1]).orElse(null);
        if (scene == null) { player.sendMessage(ChatColor.RED + "Scene not found: " + args[1]); return true; }
        engine.directorStudioService().open(player, scene.name());
        return true;
    }

    public static List<String> complete(AuraEngine engine, String[] args) {
        if (args.length == 1) return List.of("director");
        if (args.length == 2 && args[0].equalsIgnoreCase("director")) return engine.sceneManager().all().stream().map(Scene::name).toList();
        return List.of();
    }
}
