package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.core.AuraEngine;
import com.ultraop.aurareplay.director.DirectorExportRecovery;
import com.ultraop.aurareplay.director.DirectorExportSpec;
import com.ultraop.aurareplay.scene.Scene;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        if (args.length >= 3 && args[2].equalsIgnoreCase("cancel")) { boolean cancelled=engine.directorExportManager().cancel(player); player.sendMessage(cancelled?ChatColor.YELLOW+"Director export cancelled and checkpoint discarded.":ChatColor.YELLOW+"No active Director export."); return true; }
        if (args.length >= 3 && args[2].equalsIgnoreCase("pause")) { boolean paused=engine.directorExportManager().pause(player); player.sendMessage(paused?ChatColor.YELLOW+"Director export paused. Checkpoint and temporary manifest preserved; use export resume or export recover to continue.":ChatColor.YELLOW+"No active Director export."); return true; }
        if (args.length >= 3 && args[2].equalsIgnoreCase("status")) { if(!engine.directorExportManager().active(player)) player.sendMessage(ChatColor.YELLOW+"No active Director export."); else player.sendMessage(ChatColor.AQUA+"Director export progress: "+engine.directorExportManager().progress(player)+"/"+engine.directorExportManager().total(player)+" frames."); return true; }
        if (args.length >= 3 && args[2].equalsIgnoreCase("recover")) return recover(engine, player, args);

        boolean resume=args.length>=3&&args[2].equalsIgnoreCase("resume");
        int specOffset=resume?3:2, minimumArgs=resume?7:6, maximumArgs=resume?9:8;
        if(args.length<minimumArgs||args.length>maximumArgs){player.sendMessage(ChatColor.YELLOW+(resume?"Usage: /aurareplay director export resume <scene> <start> <end> <fps> [width] [height]":"Usage: /aurareplay director export <scene> <start> <end> <fps> [width] [height]"));return true;}
        Scene scene=engine.sceneManager().get(args[specOffset]).orElse(null); if(scene==null){player.sendMessage(ChatColor.RED+"Scene not found: "+args[specOffset]);return true;}
        try{
            long start=Long.parseLong(args[specOffset+1]), end=Long.parseLong(args[specOffset+2]); int fps=Integer.parseInt(args[specOffset+3]); int width=args.length>=specOffset+5?Integer.parseInt(args[specOffset+4]):DEFAULT_WIDTH, height=args.length>=specOffset+6?Integer.parseInt(args[specOffset+5]):DEFAULT_HEIGHT;
            DirectorExportSpec spec=new DirectorExportSpec(scene.name(),start,end,fps,width,height); if(spec.frameCount()>MAX_EXPORT_FRAMES){player.sendMessage(ChatColor.RED+"Export is too large: "+spec.frameCount()+" frames (maximum "+MAX_EXPORT_FRAMES+").");return true;}
            Path output=engine.plugin().getDataFolder().toPath().resolve("exports").resolve(safeFileName(scene.name())+"-"+start+"-"+end+"-"+fps+"fps.json"), checkpoint=output.resolveSibling(output.getFileName()+".checkpoint.json");
            if(resume){
                if(!Files.exists(checkpoint)){player.sendMessage(ChatColor.RED+"No Director export checkpoint found for this export.");return true;}
                boolean started=engine.directorExportManager().resume(player,spec,tick->engine.directorController().sample(player,scene,tick),output,checkpoint,written->{engine.directorController().stop(player);player.sendMessage(ChatColor.GREEN+"Director export resumed and written: "+written.toAbsolutePath());},failure->{engine.directorController().stop(player);player.sendMessage(ChatColor.RED+"Director export resume failed: "+failure.getMessage());});
                if(!started){player.sendMessage(ChatColor.RED+"You already have an active Director export. Use export status, export pause, or export cancel.");return true;} player.sendMessage(ChatColor.YELLOW+"Director export resumed from checkpoint. Use export status to check progress.");
            } else {
                boolean started=engine.directorExportManager().start(player,spec,tick->engine.directorController().sample(player,scene,tick),output,written->{engine.directorController().stop(player);player.sendMessage(ChatColor.GREEN+"Director export written: "+written.toAbsolutePath());},failure->{engine.directorController().stop(player);player.sendMessage(ChatColor.RED+"Director export failed: "+failure.getMessage());});
                if(!started){player.sendMessage(ChatColor.RED+"You already have an active Director export. Use export status, export pause, or export cancel.");return true;} player.sendMessage(ChatColor.YELLOW+"Director export started: "+spec.frameCount()+" frames. Use export status to check progress.");
            }
        }catch(NumberFormatException ex){player.sendMessage(ChatColor.RED+"Start, end, FPS, width and height must be numbers.");}catch(IllegalArgumentException ex){player.sendMessage(ChatColor.RED+ex.getMessage());}catch(IOException ex){player.sendMessage(ChatColor.RED+"Could not resume Director export: "+ex.getMessage());}
        return true;
    }

    private static boolean recover(AuraEngine engine, Player player, String[] args) {
        Path dir=engine.plugin().getDataFolder().toPath().resolve("exports");
        try {
            List<DirectorExportRecovery> recoveries=engine.directorExportRecoveryManager().discover(dir);
            if(args.length==3){
                if(recoveries.isEmpty()){player.sendMessage(ChatColor.YELLOW+"No interrupted Director exports are available for recovery.");return true;}
                player.sendMessage(ChatColor.AQUA+"Recoverable Director exports:");
                for(int i=0;i<recoveries.size();i++){DirectorExportRecovery r=recoveries.get(i); player.sendMessage(ChatColor.GRAY+"["+(i+1)+"] "+ChatColor.WHITE+r.spec().sceneName()+ChatColor.GRAY+" "+r.nextFrameIndex()+"/"+r.spec().frameCount()+" frames "+ChatColor.DARK_GRAY+r.output().getFileName());}
                player.sendMessage(ChatColor.YELLOW+"Use /aurareplay director export recover <number> to resume one."); return true;
            }
            if(args.length!=4){player.sendMessage(ChatColor.YELLOW+"Usage: /aurareplay director export recover [number]");return true;}
            int index=Integer.parseInt(args[3])-1; if(index<0||index>=recoveries.size()) throw new IllegalArgumentException("Recovery number is out of range.");
            DirectorExportRecovery recovery=recoveries.get(index); Scene scene=engine.sceneManager().get(recovery.spec().sceneName()).orElse(null);
            if(scene==null){player.sendMessage(ChatColor.RED+"Scene not found for recovery: "+recovery.spec().sceneName());return true;}
            boolean started=engine.directorExportManager().resume(player,recovery,tick->engine.directorController().sample(player,scene,tick),written->{engine.directorController().stop(player);player.sendMessage(ChatColor.GREEN+"Recovered Director export written: "+written.toAbsolutePath());},failure->{engine.directorController().stop(player);player.sendMessage(ChatColor.RED+"Director recovery failed: "+failure.getMessage());});
            player.sendMessage(started?ChatColor.YELLOW+"Director export recovery started from frame "+recovery.nextFrameIndex()+".":ChatColor.RED+"You already have an active Director export. Use export status, pause, or cancel.");
        } catch(NumberFormatException ex){player.sendMessage(ChatColor.RED+"Recovery number must be a number.");} catch(IOException ex){player.sendMessage(ChatColor.RED+"Could not inspect Director recovery state: "+ex.getMessage());} catch(IllegalArgumentException ex){player.sendMessage(ChatColor.RED+ex.getMessage());}
        return true;
    }

    private static String safeFileName(String name){String safe=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+","_");return safe.isBlank()?"scene":safe;}
    public static List<String> complete(AuraEngine engine,String[] args){
        if(args.length==1){String prefix=args[0].toLowerCase(Locale.ROOT);return "director".startsWith(prefix)?List.of("director"):List.of();}
        if(args.length==2&&args[0].equalsIgnoreCase("director")){List<String> values=new ArrayList<>();values.add("export");values.addAll(engine.sceneManager().all().stream().map(Scene::name).toList());return values;}
        if(args.length==3&&args[0].equalsIgnoreCase("director")&&args[1].equalsIgnoreCase("export")){return List.of("cancel","pause","status","resume","recover");}
        if(args.length==4&&args[0].equalsIgnoreCase("director")&&args[1].equalsIgnoreCase("export")&&args[2].equalsIgnoreCase("recover")){
            try{int size=engine.directorExportRecoveryManager().discover(engine.plugin().getDataFolder().toPath().resolve("exports")).size();List<String> values=new ArrayList<>();for(int i=1;i<=size;i++)values.add(Integer.toString(i));return values;}catch(IOException ignored){return List.of();}
        }
        return List.of();
    }
}
