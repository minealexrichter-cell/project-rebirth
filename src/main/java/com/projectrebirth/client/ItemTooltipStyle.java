package com.projectrebirth.client;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModCreativeTabs;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.Protection;
import com.projectrebirth.weapons.WeaponItem;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The tooltips of the mod's items in the style "Industrial terminal" (1.8.0, block G): a dark screen with a frame in the colour
 * of the item's section (the creative tab it is in: resources, machines, bodies, power armor, weapons, garrison, survival, world),
 * and for weapons, bodies and protective gear their numbers as short lines with signs; Shift shows the rest. The numbers come from
 * the server config, so they match the game. Client only.
 */
public final class ItemTooltipStyle {
    private static final int BACKGROUND = 0xF00E1311;
    @Nullable
    private static Map<Item, ModCreativeTabs.Kind> sections;

    private ItemTooltipStyle() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ItemTooltipStyle::onColor);
        NeoForge.EVENT_BUS.addListener(ItemTooltipStyle::onTooltip);
    }

    /** The colour of a section's frame. */
    public static int color(ModCreativeTabs.Kind kind) {
        return switch (kind) {
            case RESOURCES -> 0xA3AAB0;
            case MACHINES -> 0xFFB648;
            case BODIES -> 0x4FD1C5;
            case POWER_ARMOR -> 0x4FC3FF;
            case WEAPONS -> 0xFF8A7A;
            case GARRISON -> 0xC792EA;
            case SURVIVAL -> 0x7BD88F;
            case WORLD -> 0xC7A46A;
        };
    }

    /** The section of an item of the mod (by its creative tab), or null for other items. */
    @Nullable
    public static ModCreativeTabs.Kind section(ItemStack stack) {
        if (stack.isEmpty() || !ProjectRebirth.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return null;
        }
        if (sections == null) {
            Map<Item, ModCreativeTabs.Kind> map = new HashMap<>();
            for (ModCreativeTabs.Kind kind : ModCreativeTabs.Kind.values()) {
                for (ItemStack item : ModCreativeTabs.contents(kind)) {
                    map.putIfAbsent(item.getItem(), kind);
                }
            }
            sections = map;
        }
        return sections.getOrDefault(stack.getItem(), ModCreativeTabs.Kind.RESOURCES);
    }

    private static void onColor(RenderTooltipEvent.Color event) {
        ModCreativeTabs.Kind kind = section(event.getItemStack());
        if (kind == null) {
            return;
        }
        int color = color(kind);
        event.setBackground(BACKGROUND);
        event.setBorderStart(0xF0000000 | color);
        event.setBorderEnd(0xF0000000 | (color >> 1 & 0x7F7F7F));
    }

    private static MutableComponent stat(String sign, String key, int color, Object... args) {
        return Component.literal(sign + " ").append(Component.translatable("tooltip.projectrebirth.stat." + key, args))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(float share) {
        return Math.round(share * 100) + "%";
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (section(stack) == null || !RebirthConfig.SERVER_SPEC.isLoaded()) {
            return;
        }
        List<Component> lines = event.getToolTip();
        boolean details = Screen.hasShiftDown();
        int at = Math.min(1, lines.size());
        List<Component> stats = new java.util.ArrayList<>();
        List<Component> more = new java.util.ArrayList<>();
        if (stack.getItem() instanceof WeaponItem) {
            RebirthConfig.WeaponStats weapon = RebirthConfig.weapon(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            if (weapon != null) {
                double perSecond = 20.0 / Math.max(1, weapon.shotIntervalTicks().get());
                stats.add(stat("⚔", "damage", 0xFF8A7A, number(weapon.damage().get() * Math.max(1, weapon.pellets().get()))));
                stats.add(stat("⏱", "rate", 0xFFCF85, number(perSecond)));
                // the magazine is told by the weapon itself (rounds in it of the magazine)
                more.add(stat("➶", "range", 0xC9D0D6, number(weapon.range().get())));
                more.add(stat("↻", "reload", 0xC9D0D6, number(weapon.reloadTicks().get() / 20.0)));
                if (weapon.pellets().get() > 1) {
                    more.add(stat("✦", "pellets", 0xC9D0D6, weapon.pellets().get()));
                }
            }
        } else if (stack.getItem() instanceof ArtificialBodyItem body) {
            RebirthConfig.BodyStats statsOf = RebirthConfig.bodyStats(body.bodyClass());
            stats.add(stat("♥", "integrity", 0x7BD88F, number(statsOf.maxHealth().get())));
            stats.add(stat("⛨", "armor", 0xC9D0D6, number(statsOf.armor().get())));
            stats.add(stat("⚙", "slots", 0xFFCF85, statsOf.upgradeSlots().get()));
            more.add(stat("»", "speed", 0xC9D0D6, number(statsOf.speedMultiplier().get())));
            more.add(stat("⚡", "battery", 0x9FDCFF, statsOf.energyCapacity().get()));
            if (statsOf.selfRepairPerSecond().get() > 0) {
                more.add(stat("✚", "repair", 0xC9D0D6, number(statsOf.selfRepairPerSecond().get())));
            }
        } else {
            Protection.Kit kit = Protection.kitOf(stack);
            if (kit != null) {
                RebirthConfig.ProtectionStats protection = kit.stats();
                stats.add(stat("☢", "radiation", 0xE6D23A, percent(protection.of(Hazard.RADIATION))));
                stats.add(stat("☣", "toxins", 0x7BD88F, percent(protection.of(Hazard.POLLUTION))));
                more.add(stat("≋", "acid", 0xC9D0D6, percent(protection.of(Hazard.ACID))));
                more.add(stat("♨", "heat", 0xFFB27A, percent(protection.heat().get().floatValue())));
                more.add(stat("❄", "cold", 0x9FDCFF, percent(protection.cold().get().floatValue())));
            }
        }
        if (stats.isEmpty()) {
            return;
        }
        if (details) {
            stats.addAll(more);
        } else if (!more.isEmpty()) {
            stats.add(Component.translatable("tooltip.projectrebirth.stat.shift").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x6E767C))));
        }
        lines.addAll(at, stats);
    }
}
