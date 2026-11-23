package com.projectrebirth.client.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.powerarmor.ArmorUpgradeEffects;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeDefinition;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * Which parts of a model show its upgrades. They are bones of the model, hidden unless the upgrade is installed, so they
 * move with the bones they sit on. All plates are painted light grey and drawn in the colour of their material.
 */
public final class UpgradeVisuals {
    public static final String BODY_PLATING_PREFIX = "upgrade_plating";
    private static final List<String> BODY_PLATING = List.of("upgrade_plating_torso", "upgrade_plating_right_arm", "upgrade_plating_left_arm",
            "upgrade_plating_right_leg", "upgrade_plating_left_leg");
    private static final String BODY_SEAL = "upgrade_seal";
    private static final String BODY_JETPACK = "upgrade_jetpack";
    // 1.5.0
    private static final String BODY_BATTERY = "upgrade_battery";
    private static final String BODY_COOLING = "upgrade_cooling";
    private static final String BODY_SHIELDING = "upgrade_shielding";
    // 1.6.0, block F
    private static final String BODY_SENSOR = "upgrade_sensor";
    private static final List<String> ARMOR_BONES = List.of("slot_head_floodlight", "slot_chest_plating", "slot_chest_floodlight", "slot_back_air",
            "slot_right_arm_plating", "slot_right_arm_floodlight", "slot_left_arm_plating", "slot_left_arm_floodlight",
            "slot_right_leg_plating", "slot_right_leg_air", "slot_left_leg_plating", "slot_left_leg_air", "slot_chest_emi", "slot_back_emi", "slot_head_sensor");
    private static final Map<String, Integer> MATERIAL_COLORS = Map.of("copper", 0xFFD98A5A, "iron", 0xFFC9CED3, "titanium", 0xFF8FA6C8, "chitin", 0xFF6E5A3C);
    public static final int NO_TINT = 0xFFFFFFFF;

    private UpgradeVisuals() {
    }

    /** The colour of a plate's material; white if it has none. */
    public static int colorOf(RegistryAccess access, ItemStack stack) {
        return Upgrades.definition(access, stack).flatMap(UpgradeDefinition::material).map(MATERIAL_COLORS::get).orElse(NO_TINT);
    }

    private static void show(GeoModel<? extends GeoAnimatable> model, String bone, boolean visible) {
        model.getBone(bone).ifPresent(found -> found.setHidden(!visible));
    }

    /** The visor and the core antenna of a body run by an AI core (1.7.0): hidden on every other body. */
    public static final String ANDROID_VISOR = "android_visor";
    public static final String ANDROID_CORE = "android_core";
    public static final String ANDROID_CORE_TIP = "android_core_tip";

    public static void showAndroid(GeoModel<? extends GeoAnimatable> model, boolean visible) {
        show(model, ANDROID_VISOR, visible);
        show(model, ANDROID_CORE, visible);
    }

    /** Shows the parts of a body's gadgets; returns the colour of its plates. */
    public static int applyToBody(GeoModel<? extends GeoAnimatable> model, RegistryAccess access, InstalledUpgrades upgrades) {
        int plating = NO_TINT;
        boolean plated = false;
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            if (Upgrades.definition(access, entry.stack()).filter(definition -> definition.kind().equals(BodyGadgets.PLATING)).isPresent()) {
                plated = true;
                plating = colorOf(access, entry.stack());
            }
        }
        for (String bone : BODY_PLATING) {
            show(model, bone, plated);
        }
        show(model, BODY_SEAL, BodyGadgets.has(access, upgrades, BodyGadgets.SEALING));
        show(model, BODY_JETPACK, BodyGadgets.has(access, upgrades, BodyGadgets.JETPACK));
        show(model, BODY_BATTERY, BodyGadgets.has(access, upgrades, com.projectrebirth.bodies.power.BodyPower.BATTERY));
        show(model, BODY_COOLING, BodyGadgets.has(access, upgrades, com.projectrebirth.bodies.power.BodyHeat.COOLING));
        show(model, BODY_SHIELDING, BodyGadgets.has(access, upgrades, com.projectrebirth.environment.Emi.SHIELDING));
        showAndroid(model, false);
        show(model, BODY_SENSOR, BodyGadgets.has(access, upgrades, com.projectrebirth.scouting.Scouting.SENSOR));
        return plating;
    }

    /** The name part of a hull slot in the armor's bones. */
    private static String prefix(ArmorSlot slot) {
        return "slot_" + switch (slot) {
            case HEAD -> "head";
            case CHEST_FRONT -> "chest";
            case CHEST_BACK -> "back";
            case RIGHT_ARM -> "right_arm";
            case LEFT_ARM -> "left_arm";
            case RIGHT_LEG -> "right_leg";
            case LEFT_LEG -> "left_leg";
        };
    }

    /** Shows the parts of an armor's upgrades; returns the colour of the plates by bone name. */
    public static Map<String, Integer> applyToArmor(GeoModel<? extends GeoAnimatable> model, RegistryAccess access, InstalledUpgrades upgrades) {
        for (String bone : ARMOR_BONES) {
            show(model, bone, false);
        }
        Map<String, Integer> colors = new HashMap<>();
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            if (entry.slot().isEmpty()) {
                continue;
            }
            String kind = Upgrades.definition(access, entry.stack()).map(UpgradeDefinition::kind).orElse("");
            String bone = prefix(entry.slot().get()) + switch (kind) {
                case ArmorUpgradeEffects.PLATING -> "_plating";
                case ArmorUpgradeEffects.FLOODLIGHT -> "_floodlight";
                case ArmorUpgradeEffects.COMPRESSED_AIR -> "_air";
                case com.projectrebirth.environment.Emi.SCREEN -> "_emi";
                case com.projectrebirth.scouting.Scouting.SENSOR -> "_sensor";
                default -> "_none";
            };
            show(model, bone, true);
            if (kind.equals(ArmorUpgradeEffects.PLATING)) {
                colors.put(bone, colorOf(access, entry.stack()));
            }
        }
        return colors;
    }
}
