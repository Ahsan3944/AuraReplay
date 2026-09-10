package com.ultraop.aurareplay.listener;

import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.director.DirectorExportRecovery;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Notifies authorized players about validated interrupted Director exports after startup/join. */
public final class DirectorExportRecoveryListener implements Listener {
    private final JavaPlugin plugin;
    private final AuraEngine engine;

    public DirectorExportRecoveryListener(JavaPlugin plugin, AuraEngine engine) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPermission("aurareplay.camera")) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path directory = plugin.getDataFolder().toPath().resolve("exports");
            try {
                List<DirectorExportRecovery> recoveries = engine.directorExportRecoveryManager().discover(directory);
                if (recoveries.isEmpty()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!event.getPlayer().isOnline()) return;
                    String noun = recoveries.size() == 1 ? "export" : "exports";
                    event.getPlayer().sendMessage(ChatColor.YELLOW + "AuraReplay: " + recoveries.size()
                            + " interrupted Director " + noun + " can be recovered.");
                    event.getPlayer().sendMessage(ChatColor.AQUA + "Use /aurareplay director export recover to view and resume them.");
                });
            } catch (IOException ex) {
                plugin.getLogger().warning("Director recovery scan failed on player join: " + ex.getMessage());
            }
        });
    }
}
