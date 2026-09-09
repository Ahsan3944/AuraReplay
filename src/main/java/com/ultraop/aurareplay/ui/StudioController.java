package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Viewer-local in-game Studio navigation. */
public final class StudioController {
    private static final String TITLE = ChatColor.DARK_AQUA + "AuraReplay Studio";
    private final AuraEngine engine;
    private final Map<UUID, StudioSession> sessions = new ConcurrentHashMap<>();
    public StudioController(AuraEngine engine) { this.engine = engine; }
    public void open(Player p) { StudioSession s=sessions.computeIfAbsent(p.getUniqueId(),StudioSession::new); s.root(); renderRoot(p); }
    public void close(Player p) { sessions.remove(p.getUniqueId()); }
    public StudioSession session(Player p) { return sessions.get(p.getUniqueId()); }
    public void click(Player p,int slot) {
        StudioSession s=sessions.get(p.getUniqueId()); if(s==null)return;
        if(s.page()==StudioSession.Page.ROOT){ if(slot==10){s.actors();renderActors(p);return;} String c=switch(slot){case 11->"Transform";case 12->"Motion";case 13->"Appearance";case 14->"Camera";case 15->"Effects";case 16->"World";case 19->"Playback";case 20->"Project";case 21->"Settings";default->null;}; if(c!=null){s.category(c);renderCategory(p,c);} return; }
        if(slot==26){if(s.page()==StudioSession.Page.ACTORS){s.root();renderRoot(p);}else{s.actors();renderActors(p);}return;}
        if(s.page()==StudioSession.Page.ACTORS){var a=engine.actorManager().all().stream().toList();if(slot>=0&&slot<a.size()&&slot<26){ActorDefinition actor=a.get(slot);s.selectActor(actor.id());renderActor(p,actor);}return;}
        if(s.category()!=null&&s.category().startsWith("Actors / ")){ActorId id=s.selectedActor();ActorDefinition a=id==null?null:engine.actorManager().get(id).orElse(null);if(a==null){s.actors();renderActors(p);return;}if(slot==11){a.setVisible(!a.visible());p.sendMessage(ChatColor.GREEN+"Actor visibility: "+(a.visible()?"shown":"hidden"));renderActor(p,a);}return;}
        if("Playback".equals(s.category())){if(slot==11){if(!engine.tickRecorder().isRecording()){engine.tickRecorder().track(p);engine.tickRecorder().start();p.sendMessage(ChatColor.GREEN+"Recording started.");}else p.sendMessage(ChatColor.YELLOW+"A recording is already active.");}else if(slot==12){if(engine.tickRecorder().isRecording()){var r=engine.tickRecorder().stop("capture-"+System.currentTimeMillis());if(r!=null){engine.recordingManager().register(r);p.sendMessage(ChatColor.GREEN+"Recording saved: "+r.name());}}else p.sendMessage(ChatColor.YELLOW+"No recording is active.");}}
        else if("Camera".equals(s.category())&&slot==11)p.sendMessage(ChatColor.GRAY+"Registered cameras: "+engine.cameraManager().all().size());
    }
    private void renderRoot(Player p){Inventory i=inv(TITLE);item(i,10,Material.PLAYER_HEAD,"Actors","Select and edit virtual actors");item(i,11,Material.COMPASS,"Transform","Position, rotation and scale");item(i,12,Material.LEATHER_BOOTS,"Motion","Playback and motion tools");item(i,13,Material.ARMOR_STAND,"Appearance","Identity, equipment and visuals");item(i,14,Material.SPYGLASS,"Camera","Cinematic camera controls");item(i,15,Material.FIREWORK_ROCKET,"Effects","Particles, sounds and cues");item(i,16,Material.GRASS_BLOCK,"World","World and environment controls");item(i,19,Material.CLOCK,"Playback","Record and take controls");item(i,20,Material.BOOK,"Project","Scenes, recordings and assets");item(i,21,Material.REDSTONE,"Settings","Studio and input settings");p.openInventory(i);}
    private void renderActors(Player p){Inventory i=inv(ChatColor.DARK_AQUA+"Studio / Actors");var a=engine.actorManager().all().stream().toList();for(int n=0;n<Math.min(26,a.size());n++)item(i,n,Material.ARMOR_STAND,a.get(n).name(),"Select actor","ID: "+a.get(n).id());item(i,26,Material.ARROW,"Back","Return to Studio");p.openInventory(i);}
    private void renderActor(Player p,ActorDefinition a){Inventory i=inv(ChatColor.DARK_AQUA+"Studio / "+a.name());item(i,10,Material.COMPASS,"Transform","Position, rotation and scale");item(i,11,a.visible()?Material.LANTERN:Material.REDSTONE_TORCH,a.visible()?"Hide Actor":"Show Actor","Toggle visibility");item(i,12,Material.LEATHER_BOOTS,"Motion","Speed, delay, loop and reverse");item(i,13,Material.NAME_TAG,"Identity","Name and nametag");item(i,14,Material.ARMOR_STAND,"Equipment","Armor and held items");item(i,26,Material.ARROW,"Back","Return to actors");p.openInventory(i);}
    private void renderCategory(Player p,String c){Inventory i=inv(ChatColor.DARK_AQUA+"Studio / "+c);if("Playback".equals(c)){item(i,11,Material.RED_DYE,"Record","Start recording");item(i,12,Material.LIME_DYE,"Stop & Save","Save current recording");}else if("Camera".equals(c))item(i,11,Material.SPYGLASS,"Camera Status","Show registered cameras");else item(i,13,Material.BOOK,"Tool","Category foundation; shared services are used");item(i,26,Material.ARROW,"Back","Return to Studio");p.openInventory(i);}
    private static Inventory inv(String title){return Bukkit.createInventory(new Holder(),27,title);}
    private static void item(Inventory i,int slot,Material m,String name,String... lore){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();meta.setDisplayName(ChatColor.AQUA+name);meta.setLore(Arrays.stream(lore).map(v->ChatColor.GRAY+v).toList());x.setItemMeta(meta);i.setItem(slot,x);}
    private static final class Holder implements InventoryHolder{public Inventory getInventory(){return null;}}
}
