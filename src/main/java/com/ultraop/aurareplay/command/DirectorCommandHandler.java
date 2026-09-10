package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.director.DirectorExportSpec;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Handles the command entry point for Director Studio and deterministic manifest export. */
public final class DirectorCommandHandler {
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final long MAX_EXPORT_FRAMES = 120_000L;

    private DirectorCommandHandler() { }

    public static boolean handle(AuraEngine engine, CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("director")) return false;
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Director Studio must be opened by a player."); return true; }
        if (!player.hasPermission("aurareplay.camera")) { player.sendMessage(ChatColor.RED + "You do not have Director permission."); return true; }
        if (args.length >= 2 && args[1].equalsIgnoreCase("export")) return export(engine, player, args);
        if (args.length < 2) { player.sendMessage(ChatColor.YELLOW + "/aurareplay director <scene> | export <scene> <start> <end> <fps> [width] [height]"); return true; }
        Scene scene = engine.sceneManager().get(args[1]).orElse(null);
        if (scene == null) { player.sendMessage(ChatColor.RED + "Scene not found: " + args[1]); return true; }
        engine.directorStudioService().open(player, scene.name());
        return true;
    }

    private static boolean export(AuraEngine engine, Player player, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("cancel")) {
            boolean cancelled = engine.directorExportManager().cancel(player);
            player.sendMessage(cancelled ? ChatColor.YELLOW + "Director export cancelled." : ChatColor.YELLOW + "No active Director export.");
            return true;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("status")) {
            if (!engine.directorExportManager().active(player)) {
                player.sendMessage(ChatColor.YELLOW + "No active Director export.");
            } else {
                player.sendMessage(ChatColor.AQUA + "Director export progress: "
                        + engine.directorExportManager().progress(player) + "/"
                        + engine.directorExportManager().total(player) + " frames.");
            }
            return true;
        }

        boolean resume = args.length >= 3 && args[2].equalsIgnoreCase("resume");
        int specOffset = resume ? 3 : 2;
        int minimumArgs = resume ? 7 : 6;
        int maximumArgs = resume ? 9 : 8;
        if (args.length < minimumArgs || args.length > maximumArgs) {
            player.sendMessage(ChatColor.YELLOW + (resume
                    ? "Usage: /aurareplay director export resume <scene> <start> <end> <fps> [width] [height]"
                    : "Usage: /aurareplay director export <scene> <start> <end> <fps> [width] [height]"));
            return true;
        }

        Scene scene = engine.sceneManager().get(args[specOffset]).orElse(null);
        if (scene == null) {
            player.sendMessage(ChatColor.RED + "Scene not found: " + args[specOffset]);
            return true;
        }
        try {
            long start = Long.parseLong(args[specOffset + 1]);
            long end = Long.parseLong(args[specOffset + 2]);
            int fps = Integer.parseInt(args[specOffset + 3]);
            int width = args.length >= specOffset + 5 ? Integer.parseInt(args[specOffset + 4]) : DEFAULT_WIDTH;
            int height = args.length >= specOffset + 6 ? Integer.parseInt(args[specOffset + 5]) : DEFAULT_HEIGHT;
            DirectorExportSpec spec = new DirectorExportSpec(scene.name(), start, end, fps, width, height);
            if (spec.frameCount() > MAX_EXPORT_FRAMES) {
                player.sendMessage(ChatColor.RED + "Export is too large: " + spec.frameCount() + " frames (maximum " + MAX_EXPORT_FRAMES + ").");
                return true;
            }

            Path output = engine.plugin().getDataFolder().toPath()
                    .resolve("exports")
                    .resolve(safeFileName(scene.name()) + "-" + start + "-" + end + "-" + fps + "fps.json");
            Path checkpoint = output.resolveSibling(output.getFileName() + ".checkpoint.json");

            if (resume) {
                if (!java.nio.file.Files.exists(checkpoint)) {
                    player.sendMessage(ChatColor.RED + "No Director export checkpoint found for this export.");
                    return true;
                }
                boolean started = engine.directorExportManager().resume(
                        player,
                        spec,
                        tick -> engine.directorController().sample(player, scene, tick),
                        output,
                        checkpoint,
                        written -> {
                            engine.directorController().stop(player);
                            player.sendMessage(ChatColor.GREEN + "Director export resumed and written: " + written.toAbsolutePath());
                        },
                        failure -> {
                            engine.directorController().stop(player);
                            player.sendMessage(ChatColor.RED + "Director export resume failed: " + failure.getMessage());
                        });
                if (!started) {
                    player.sendMessage(ChatColor.RED + "You already have an active Director export. Use export status or export cancel.");
                    return true;
                }
                player.sendMessage(ChatColor.YELLOW + "Director export resumed from checkpoint. Use export status to check progress.");
            } else {
                boolean started = engine.directorExportManager().start(
                        player,
                        spec,
                        tick -> engine.directorController().sample(player, scene, tick),
                        output,
                        written -> {
                            engine.directorController().stop(player);
                            player.sendMessage(ChatColor.GREEN + "Director export written: " + written.toAbsolutePath());
                        },
                        failure -> {
                            engine.directorController().stop(player);
                            player.sendMessage(ChatColor.RED + "Director export failed: " + failure.getMessage());
                        });
                if (!started) {
                    player.sendMessage(ChatColor.RED + "You already have an active Director export. Use export status or export cancel.");
                    return true;
                }
                player.sendMessage(ChatColor.YELLOW + "Director export started: " + spec.frameCount() + " frames. Use export status to check progress.");
            }
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Start, end, FPS, width and height must be numbers.");
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
        } catch (IOException ex) {
            player.sendMessage(ChatColor.RED + "Could not resume Director export: " + ex.getMessage());
        }
        return true;
    }

    private static String safeFileName(String name) {
        String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        return safe.isBlank() ? "scene" : safe;
    }

    public static List<String> complete(AuraEngine engine, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return "director".startsWith(prefix) ? List.of("director") : List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("director")) {
            List<String> values = new java.util.ArrayList<>();
            values.add("export");
            values.addAll(engine.sceneManager().all().stream().map(Scene::name).toList());
            return values;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("director") && args[1].equalsIgnoreCase("export")) {
            List<String> values = new java.util.ArrayList<>();
            values.add("cancel");
            values.add("status");
            values.add("resume");
            values.addAll(engine.sceneManager().all().stream().map(Scene::name).toList());
            return values;
        }
        return List.of();
    }
}
