package com.projectrebirth.technology;

import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.bodies.BodyComponents;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorItem;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Repair rules of the maintenance station for packed power armor and packed bodies. A missing integrity component
 * means "undamaged", so a fully repaired item loses its components and stacks with new ones again.
 */
public final class Maintenance {
    public enum Kind {
        NONE, ARMOR, ROBOT_BODY, SYNTHETIC_BODY
    }

    private Maintenance() {
    }

    public static Kind kind(ItemStack stack) {
        if (stack.getItem() instanceof PowerArmorItem) {
            return Kind.ARMOR;
        }
        if (stack.getItem() instanceof ArtificialBodyItem body) {
            return body.bodyClass().synthetic() ? Kind.SYNTHETIC_BODY : Kind.ROBOT_BODY;
        }
        return Kind.NONE;
    }

    public static float armorStructural(ItemStack stack) {
        Float value = stack.get(PowerArmorRegistry.STORED_STRUCTURAL.get());
        return value != null ? value : maxStructural(stack);
    }

    public static float armorCritical(ItemStack stack) {
        Float value = stack.get(PowerArmorRegistry.STORED_CRITICAL.get());
        return value != null ? value : maxCritical(stack);
    }

    /** Maximum structural integrity of a packed armor, by its variant. */
    public static float maxStructural(ItemStack stack) {
        return RebirthConfig.armorStats(variant(stack)).maxStructural().get().floatValue()
                + stack.getOrDefault(PowerArmorRegistry.PLATING_STRUCTURAL.get(), 0.0f);
    }

    public static float maxCritical(ItemStack stack) {
        return RebirthConfig.armorStats(variant(stack)).maxCritical().get().floatValue()
                + stack.getOrDefault(PowerArmorRegistry.PLATING_CRITICAL.get(), 0.0f);
    }

    private static ArmorVariant variant(ItemStack stack) {
        return stack.getItem() instanceof PowerArmorItem armor ? armor.variant() : ArmorVariant.COMBAT;
    }

    public static float bodyIntegrity(ItemStack stack) {
        Float value = stack.get(BodyComponents.STORED_INTEGRITY.get());
        return value != null ? value : maxBodyIntegrity(stack);
    }

    public static float maxBodyIntegrity(ItemStack stack) {
        return stack.getItem() instanceof ArtificialBodyItem body
                ? RebirthConfig.bodyStats(body.bodyClass()).maxHealth().get().floatValue() : 0.0f;
    }

    /** Wear of a packed body, in percent (1.5.0). */
    public static float bodyWear(ItemStack stack) {
        return stack.getOrDefault(com.projectrebirth.bodies.power.BodyPowerRegistry.STORED_WEAR.get(), 0.0f);
    }

    /** What takes wear off a body: actuators for robots, circuit boards for synthetic bodies. */
    public static Item wearMaterial(ItemStack stack) {
        return kind(stack) == Kind.SYNTHETIC_BODY ? TechItems.CIRCUIT_BOARD.get() : TechItems.ACTUATOR.get();
    }

    public static boolean needsRepair(ItemStack stack) {
        return switch (kind(stack)) {
            case ARMOR -> armorCritical(stack) < maxCritical(stack) || armorStructural(stack) < maxStructural(stack);
            case ROBOT_BODY, SYNTHETIC_BODY -> bodyIntegrity(stack) < maxBodyIntegrity(stack) || bodyWear(stack) > 0.0f;
            case NONE -> false;
        };
    }

    /**
     * The material the next repair step of this item uses, chosen from what is available: for armor, plates go to
     * critical integrity first, then iron to structural. Returns null when nothing usable is available.
     */
    public static Item materialFor(ItemStack stack, MaterialSource materials) {
        switch (kind(stack)) {
            case ARMOR -> {
                if (armorCritical(stack) < maxCritical(stack) && materials.has(PowerArmorRegistry.ARMOR_PLATE.get())) {
                    return PowerArmorRegistry.ARMOR_PLATE.get();
                }
                if (armorStructural(stack) < maxStructural(stack) && materials.has(Items.IRON_INGOT)) {
                    return Items.IRON_INGOT;
                }
                return null;
            }
            case ROBOT_BODY, SYNTHETIC_BODY -> {
                // integrity first, then wear: robots take iron and actuators, synthetic bodies fiber and circuit boards
                boolean synthetic = kind(stack) == Kind.SYNTHETIC_BODY;
                Item integrityMaterial = synthetic ? TechItems.SYNTHETIC_FIBER.get() : Items.IRON_INGOT;
                Item wearMaterial = wearMaterial(stack);
                if (bodyIntegrity(stack) < maxBodyIntegrity(stack) && materials.has(integrityMaterial)) {
                    return integrityMaterial;
                }
                if (bodyWear(stack) > 0.0f && materials.has(wearMaterial)) {
                    return wearMaterial;
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** Applies one unit of the material (as chosen by {@link #materialFor}) to the item. */
    public static void apply(ItemStack stack, Item material) {
        if (kind(stack) == Kind.ARMOR) {
            float structural = armorStructural(stack);
            float critical = armorCritical(stack);
            if (material == PowerArmorRegistry.ARMOR_PLATE.get()) {
                critical = Math.min(maxCritical(stack), critical + RebirthConfig.ARMOR_REPAIR_CRITICAL_PER_PLATE.get().floatValue());
            } else {
                structural = Math.min(maxStructural(stack), structural + RebirthConfig.ARMOR_REPAIR_STRUCTURAL_PER_INGOT.get().floatValue());
            }
            if (structural >= maxStructural(stack) && critical >= maxCritical(stack)) {
                stack.remove(PowerArmorRegistry.STORED_STRUCTURAL.get());
                stack.remove(PowerArmorRegistry.STORED_CRITICAL.get());
            } else {
                stack.set(PowerArmorRegistry.STORED_STRUCTURAL.get(), structural);
                stack.set(PowerArmorRegistry.STORED_CRITICAL.get(), critical);
            }
            return;
        }
        if (material == wearMaterial(stack)) {
            float wear = Math.max(0.0f, bodyWear(stack) - RebirthConfig.MAINTENANCE_BODY_WEAR_PER_ITEM.get().floatValue());
            if (wear <= 0.0f) {
                stack.remove(com.projectrebirth.bodies.power.BodyPowerRegistry.STORED_WEAR.get());
            } else {
                stack.set(com.projectrebirth.bodies.power.BodyPowerRegistry.STORED_WEAR.get(), wear);
            }
            return;
        }
        float max = maxBodyIntegrity(stack);
        float integrity = Math.min(max, bodyIntegrity(stack) + RebirthConfig.MAINTENANCE_BODY_REPAIR_PER_ITEM.get().floatValue());
        if (integrity >= max) {
            stack.remove(BodyComponents.STORED_INTEGRITY.get());
        } else {
            stack.set(BodyComponents.STORED_INTEGRITY.get(), integrity);
        }
    }

    /** Which material items are at hand. */
    @FunctionalInterface
    public interface MaterialSource {
        boolean has(Item item);
    }
}
