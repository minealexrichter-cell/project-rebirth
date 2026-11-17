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
 * Repair rules of the maintenance station for packed power armor, packed bodies and packed machines (1.7.0). A missing integrity component
 * means "undamaged", so a fully repaired item loses its components and stacks with new ones again.
 */
public final class Maintenance {
    public enum Kind {
        NONE, ARMOR, ROBOT_BODY, SYNTHETIC_BODY, MACHINE
    }

    private Maintenance() {
    }

    public static Kind kind(ItemStack stack) {
        if (stack.getItem() instanceof com.projectrebirth.garrison.PackedMachineItem) {
            return Kind.MACHINE;
        }
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

    // ---------- packed androids (1.7.0): the body item carries the AI core; plates mend its critical bar

    /** Critical integrity of a packed android, or its maximum when the body is no android. */
    public static float androidCritical(ItemStack stack) {
        var packed = stack.get(com.projectrebirth.garrison.GarrisonRegistry.PACKED_MACHINE.get());
        return packed != null && stack.has(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get())
                ? packed.critical() : maxAndroidCritical(stack);
    }

    public static float maxAndroidCritical(ItemStack stack) {
        return maxBodyIntegrity(stack) * RebirthConfig.ANDROID_CRITICAL_SHARE.get().floatValue();
    }

    // ---------- packed machines (1.7.0): plates mend critical, iron structural, actuators take wear off

    private static com.projectrebirth.garrison.MachineCore.Packed machine(ItemStack stack) {
        return com.projectrebirth.garrison.PackedMachineItem.packed(stack);
    }

    private static boolean machineNeeds(ItemStack stack) {
        var packed = machine(stack);
        return packed.critical() < packed.kind().maxCritical() || packed.structural() < packed.kind().maxStructural() || packed.wear() > 0.0f;
    }

    public static boolean needsRepair(ItemStack stack) {
        return switch (kind(stack)) {
            case MACHINE -> machineNeeds(stack);
            case ARMOR -> armorCritical(stack) < maxCritical(stack) || armorStructural(stack) < maxStructural(stack);
            case ROBOT_BODY, SYNTHETIC_BODY -> bodyIntegrity(stack) < maxBodyIntegrity(stack) || bodyWear(stack) > 0.0f
                    || androidCritical(stack) < maxAndroidCritical(stack);
            case NONE -> false;
        };
    }

    /**
     * The material the next repair step of this item uses, chosen from what is available: for armor, plates go to
     * critical integrity first, then iron to structural. Returns null when nothing usable is available.
     */
    public static Item materialFor(ItemStack stack, MaterialSource materials) {
        switch (kind(stack)) {
            case MACHINE -> {
                var packed = machine(stack);
                if (packed.critical() < packed.kind().maxCritical() && materials.has(PowerArmorRegistry.ARMOR_PLATE.get())) {
                    return PowerArmorRegistry.ARMOR_PLATE.get();
                }
                if (packed.structural() < packed.kind().maxStructural() && materials.has(Items.IRON_INGOT)) {
                    return Items.IRON_INGOT;
                }
                if (packed.wear() > 0.0f && materials.has(TechItems.ACTUATOR.get())) {
                    return TechItems.ACTUATOR.get();
                }
                return null;
            }
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
                if (androidCritical(stack) < maxAndroidCritical(stack) && materials.has(PowerArmorRegistry.ARMOR_PLATE.get())) {
                    return PowerArmorRegistry.ARMOR_PLATE.get();
                }
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
        if (kind(stack) == Kind.MACHINE) {
            var packed = machine(stack);
            float structural = packed.structural(), critical = packed.critical(), wear = packed.wear();
            if (material == PowerArmorRegistry.ARMOR_PLATE.get()) {
                critical = Math.min(packed.kind().maxCritical(), critical + RebirthConfig.MACHINE_REPAIR_CRITICAL_PER_PLATE.get().floatValue());
            } else if (material == Items.IRON_INGOT) {
                structural = Math.min(packed.kind().maxStructural(), structural + RebirthConfig.MACHINE_REPAIR_PER_INGOT.get().floatValue());
            } else {
                wear = Math.max(0.0f, wear - RebirthConfig.MACHINE_WEAR_PER_ACTUATOR.get().floatValue());
            }
            stack.set(com.projectrebirth.garrison.GarrisonRegistry.PACKED_MACHINE.get(), new com.projectrebirth.garrison.MachineCore.Packed(
                    packed.kind(), structural, critical, wear, packed.owner(), packed.ownerName(), packed.name()));
            return;
        }
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
        if (material == PowerArmorRegistry.ARMOR_PLATE.get()) {
            var packed = stack.get(com.projectrebirth.garrison.GarrisonRegistry.PACKED_MACHINE.get());
            if (packed != null) {
                float critical = Math.min(maxAndroidCritical(stack),
                        packed.critical() + RebirthConfig.MACHINE_REPAIR_CRITICAL_PER_PLATE.get().floatValue());
                stack.set(com.projectrebirth.garrison.GarrisonRegistry.PACKED_MACHINE.get(), new com.projectrebirth.garrison.MachineCore.Packed(
                        packed.kind(), packed.structural(), critical, packed.wear(), packed.owner(), packed.ownerName(), packed.name()));
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
