package com.ultraop.aurareplay.recording.snapshot;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/** Immutable equipment state captured from the main thread. */
public record EquipmentSnapshot(
        ItemStack mainHand,
        ItemStack offHand,
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots
) {
    public static EquipmentSnapshot capture(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }

        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) {
            return null;
        }

        return new EquipmentSnapshot(
                copy(equipment.getItemInMainHand()),
                copy(equipment.getItemInOffHand()),
                copy(equipment.getHelmet()),
                copy(equipment.getChestplate()),
                copy(equipment.getLeggings()),
                copy(equipment.getBoots())
        );
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
