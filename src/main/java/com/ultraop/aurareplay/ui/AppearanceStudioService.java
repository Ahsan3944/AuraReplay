package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.AuraReplayPlugin;
import com.ultraop.aurareplay.actor.ActorAppearance;
import com.ultraop.aurareplay.actor.ActorDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Compact actor appearance/equipment editor. Uses the player's held item as an equipment override source. */
public final class AppearanceStudioService {
    private final StudioController controller;
    public AppearanceStudioService(StudioController controller) { this.controller = controller; }
    public void open(Player player) {
        ActorDefinition actor=selected(player);if(actor==null)return;Inventory inv=player.getOpenInventory().getTopInventory();inv.clear();put(inv,0,Material.ARROW,"Back","Return to actor");ActorAppearance a=actor.appearance();ActorAppearance.EquipmentSlot[] slots=ActorAppearance.EquipmentSlot.values();
        for(int i=0;i<slots.length;i++){ActorAppearance.EquipmentSlot slot=slots[i];ItemStack item=a.hasEquipmentOverride(slot)?a.equipment(slot):new ItemStack(Material.BARRIER);put(inv,2+i,item.getType().isAir()?Material.BARRIER:item.getType(),slot.name().replace('_',' '),a.hasEquipmentOverride(slot)?"Override: ON":"Override: OFF","Left-click: use held item","Right-click: clear override");}
        put(inv,10,Material.FLINT_AND_STEEL,"Fire",state(a.fire()),"Click to cycle");put(inv,11,Material.GLOWSTONE,"Glowing",state(a.glowing()),"Click to cycle");put(inv,12,Material.POTION,"Invisible",state(a.invisible()),"Click to cycle");put(inv,13,Material.ARMOR_STAND,"Pose",a.pose()==null?"Source pose":a.pose().name(),"Click to cycle");put(inv,26,Material.ARROW,"Back","Return to actor");
    }
    public void click(Player player,int slot,boolean rightClick){ActorDefinition actor=selected(player);if(actor==null)return;ActorAppearance a=actor.appearance();if(slot==0||slot==26){controller.click(player,26);return;}if(slot>=2&&slot<8){ActorAppearance.EquipmentSlot s=ActorAppearance.EquipmentSlot.values()[slot-2];if(rightClick)a.clearEquipmentOverride(s);else a.setEquipment(s,player.getInventory().getItemInMainHand());}else switch(slot){case 10->a.setFire(next(a.fire()));case 11->a.setGlowing(next(a.glowing()));case 12->a.setInvisible(next(a.invisible()));case 13->a.setPose(nextPose(a.pose()));default->{return;}}open(player);}
    private static Boolean next(Boolean value){return value==null?Boolean.TRUE:value?Boolean.FALSE:null;}
    private static Pose nextPose(Pose value){Pose[] p=Pose.values();return value==null?p[0]:p[(value.ordinal()+1)%p.length];}
    private ActorDefinition selected(Player p){StudioSession s=controller.session(p);return s==null||s.selectedActor()==null?null:AuraReplayPlugin.getPlugin(AuraReplayPlugin.class).engine().actorManager().get(s.selectedActor()).orElse(null);}
    private static String state(Boolean value){return value==null?"Source":value?"ON":"OFF";}
    private static void put(Inventory inv,int slot,Material material,String name,String... lore){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(ChatColor.AQUA+name);java.util.List<String> lines=new java.util.ArrayList<>();for(String line:lore)lines.add(ChatColor.GRAY+line);meta.setLore(lines);item.setItemMeta(meta);inv.setItem(slot,item);}
}
