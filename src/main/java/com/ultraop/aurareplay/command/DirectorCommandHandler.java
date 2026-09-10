package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.director.DirectorExportService;
import com.ultraop.aurareplay.director.DirectorExportSpec;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        if (args.length < 6 || args.length > 8) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /aurareplay director export <scene> <start> <end> <fps> [width] [height]");
            return true;
        }
        Scene scene = engine.sceneManager().get(args[2]).orElse(null);
        if (scene == null) {
            player.sendMessage(ChatColor.RED + "Scene not found: " + args[2]);
            return true;
        }
        try {
            long start = Long.parseLong(args[3]);
            long end = Long.parseLong(args[4]);
            int fps = Integer.parseInt(args[5]);
            int width = args.length >= 7 ? Integer.parseInt(args[6]) : DEFAULT_WIDTH;
            int height = args.length >= 8 ? Integer.parseInt(args[7]) : DEFAULT_HEIGHT;
            DirectorExportSpec spec = new DirectorExportSpec(scene.name(), start, end, fps, width, height);
            if (spec.frameCount() > MAX_EXPORT_FRAMES) {
                player.sendMessage(ChatColor.RED + "Export is too large: " + spec.frameCount() + " frames (maximum " + MAX_EXPORT_FRAMES + ").");
                return true;
            }

            DirectorExportService service = new DirectorExportService();
            Path output = engine.plugin().getDataFolder().toPath()
                    .resolve("exports")
                    .resolve(safeFileName(scene.name()) + "-" + start + "-" + end + "-" + fps + "fps.json");
            player.sendMessage(ChatColor.YELLOW + "Rendering " + spec.frameCount() + " Director frames...");
            service.export(output, spec, tick -> engine.directorController().sample(player, scene, tick));
            engine.directorController().stop(player);
            player.sendMessage(ChatColor.GREEN + "Director export written: " + output.toAbsolutePath());
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Start, end, FPS, width and height must be numbers.");
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
        } catch (Exception ex) {
            engine.directorController().stop(player);
            player.sendMessage(ChatColor.RED + "Director export failed: " + ex.getMessage());
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
            return engine.sceneManager().all().stream().map(Scene::name).toList();
        }
        return List.of();
    }
}
