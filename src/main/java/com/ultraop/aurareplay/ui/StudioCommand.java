package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Dedicated command entry point for the Studio UI and its source browser. */
public final class StudioCommand implements CommandExecutor {
    private final StudioController controller;
    private final ActorSourceBrowser sourceBrowser;

    public StudioCommand(StudioController controller) {
        this(controller, null);
    }

    public StudioCommand(AuraEngine engine) {
        this(engine.studioController(), new ActorSourceBrowser(engine));
    }

    public StudioCommand(StudioController controller, ActorSourceBrowser sourceBrowser) {
        this.controller = controller;
        this.sourceBrowser = sourceBrowser;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Studio must be opened by a player."); return true; }
        if (!player.hasPermission("aurareplay.use")) { player.sendMessage("You do not have permission to use AuraReplay."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("sources") && sourceBrowser != null) {
            sourceBrowser.openRecordings(player);
            return true;
        }
        controller.open(player);
        return true;
    }
}
