package com.projectrebirth.environment;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Protective gear. Which items belong to which kit is data (item tags projectrebirth:protection/&lt;kit&gt;), so other mods'
 * armor can be given protection by a data pack; how much a kit protects is config. Ordinary armor protects from nothing.
 * <p>
 * Breathing protection comes only from the head piece, and only while its filter lasts. Acid and radiation are kept off by
 * the whole body, so each worn piece counts by its share.
 */
public final class Protection {
    private static final List<EquipmentSlot> BODY = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    /** A kind of protective gear. */
    public record Kit(String id, TagKey<Item> items) {
        public RebirthConfig.ProtectionStats stats() {
            return RebirthConfig.protectionKit(id);
        }
    }

    public static final Kit RESPIRATOR = kit("respirator");
    public static final Kit HAZMAT = kit("hazmat");
    public static final Kit HEAVY_SUIT = kit("heavy_suit");
    public static final List<Kit> KITS = List.of(RESPIRATOR, HAZMAT, HEAVY_SUIT);

    private Protection() {
    }

    private static Kit kit(String id) {
        return new Kit(id, TagKey.create(Registries.ITEM, ProjectRebirth.id("protection/" + id)));
    }

    @Nullable
    public static Kit kitOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Kit kit : KITS) {
            if (stack.is(kit.items())) {
                return kit;
            }
        }
        return null;
    }

    private static float share(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> RebirthConfig.PROTECTION_HEAD_SHARE.get().floatValue();
            case CHEST -> RebirthConfig.PROTECTION_CHEST_SHARE.get().floatValue();
            case LEGS -> RebirthConfig.PROTECTION_LEGS_SHARE.get().floatValue();
            default -> RebirthConfig.PROTECTION_FEET_SHARE.get().floatValue();
        };
    }

    /** Whether a head piece filters the air: it is in a kit that does, whatever its filter. */
    public static boolean breathes(ItemStack head) {
        Kit kit = kitOf(head);
        return kit != null && kit.stats().pollution().get() > 0.0;
    }

    /** Seconds' worth of filter left; a fresh filter item has the full life. */
    public static float filterLeft(ItemStack stack) {
        Float left = stack.get(EnvironmentRegistry.FILTER_LIFE.get());
        if (left != null) {
            return left;
        }
        return stack.getItem() instanceof FilterItem ? RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue() : 0.0f;
    }

    /** The share of a hazard the gear of this person keeps off, 0 to 1. */
    public static float ofGear(Player player, Hazard hazard) {
        float total = 0.0f;
        for (EquipmentSlot slot : BODY) {
            ItemStack stack = player.getItemBySlot(slot);
            Kit kit = kitOf(stack);
            if (kit == null) {
                continue;
            }
            RebirthConfig.ProtectionStats stats = kit.stats();
            if (hazard == Hazard.POLLUTION) {
                if (slot == EquipmentSlot.HEAD && filterLeft(stack) > 0.0f) {
                    total += stats.pollution().get().floatValue();
                }
            } else {
                total += share(slot) * stats.of(hazard);
            }
        }
        return Math.min(1.0f, total);
    }

    /**
     * Uses the filter for one second of this air: more in worse air, nothing in air no worse than the ordinary background. Tells the person once when it
     * runs out. Public for tests.
     */
    public static void useFilter(ServerPlayer player, float pollution) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (pollution <= RebirthConfig.PROTECTION_FILTER_SAFE_POLLUTION.get() || !breathes(head)) {
            return;
        }
        float left = filterLeft(head);
        if (left <= 0.0f) {
            return;
        }
        float after = Math.max(0.0f, left - pollution * RebirthConfig.PROTECTION_FILTER_USE.get().floatValue());
        head.set(EnvironmentRegistry.FILTER_LIFE.get(), after);
        if (after <= 0.0f) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.environment.filter_spent"), true);
        }
    }

    /** Acid eats into the gear that keeps it off. Public for tests. */
    public static void wearByAcid(ServerPlayer player) {
        for (EquipmentSlot slot : BODY) {
            ItemStack stack = player.getItemBySlot(slot);
            Kit kit = kitOf(stack);
            if (kit != null && kit.stats().of(Hazard.ACID) > 0.0f && stack.isDamageableItem()) {
                stack.hurtAndBreak(RebirthConfig.PROTECTION_ACID_WEAR.get(), player, slot);
            }
        }
    }

    /**
     * Puts a filter into a head piece and hands back what was left of the old one, so that nothing is lost.
     *
     * @return the old filter, or an empty stack
     */
    public static ItemStack insertFilter(ItemStack head, ItemStack filter) {
        float old = head.has(EnvironmentRegistry.FILTER_LIFE.get()) ? filterLeft(head) : 0.0f;
        head.set(EnvironmentRegistry.FILTER_LIFE.get(), filterLeft(filter));
        filter.shrink(1);
        if (old <= 0.0f) {
            return ItemStack.EMPTY;
        }
        ItemStack back = new ItemStack(EnvironmentRegistry.FILTER.get());
        back.set(EnvironmentRegistry.FILTER_LIFE.get(), old);
        return back;
    }
}
