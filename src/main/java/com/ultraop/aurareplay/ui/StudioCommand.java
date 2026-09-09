package com.ultraop.aurareplay.ui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Dedicated fallback command for opening the Studio UI. */
public final class StudioCommand implements CommandExecutor {
    private final StudioController controller;
    public StudioCommand(StudioController controller) { this.controller = controller; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Studio must be opened by a player."); return true; }
        if (!player.hasPermission("aurareplay.use")) { player.sendMessage("You do not have permission to use AuraReplay."); return true; }
        controller.open(player);
        return true;
    }
}
