package com.ultraop.aurareplay.actor;

import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Editable appearance overrides owned by an actor instance, never by the source recording. */
public final class ActorAppearance {
    public enum EquipmentSlot { MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET }

    private final EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final EnumMap<EquipmentSlot, Boolean> equipmentOverrides = new EnumMap<>(EquipmentSlot.class);
    private Boolean fire;
    private Boolean glowing;
    private Boolean invisible;
    private Pose pose;

    public ActorAppearance() {
        for (EquipmentSlot slot : EquipmentSlot.values()) equipmentOverrides.put(slot, false);
    }

    public boolean hasEquipmentOverride(EquipmentSlot slot) { return Boolean.TRUE.equals(equipmentOverrides.get(slot)); }
    public ItemStack equipment(EquipmentSlot slot) {
        ItemStack item = equipment.get(slot);
        return item == null ? null : item.clone();
    }
    public void setEquipment(EquipmentSlot slot, ItemStack item) {
        Objects.requireNonNull(slot, "slot");
        equipmentOverrides.put(slot, true);
        if (item == null || item.getType().isAir()) equipment.remove(slot); else equipment.put(slot, item.clone());
    }
    public void clearEquipmentOverride(EquipmentSlot slot) {
        equipmentOverrides.put(Objects.requireNonNull(slot, "slot"), false);
        equipment.remove(slot);
    }
    public Map<EquipmentSlot, ItemStack> equipmentSnapshot() {
        EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) if (hasEquipmentOverride(slot)) copy.put(slot, equipment(slot));
        return Map.copyOf(copy);
    }

    public Boolean fire() { return fire; }
    public Boolean glowing() { return glowing; }
    public Boolean invisible() { return invisible; }
    public Pose pose() { return pose; }
    public void setFire(Boolean value) { fire = value; }
    public void setGlowing(Boolean value) { glowing = value; }
    public void setInvisible(Boolean value) { invisible = value; }
    public void setPose(Pose value) { pose = value; }
    public void clearStateOverrides() { fire = null; glowing = null; invisible = null; pose = null; }
}
