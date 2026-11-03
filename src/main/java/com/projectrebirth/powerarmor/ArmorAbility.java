package com.projectrebirth.powerarmor;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The ability slot of a power armor: hotbar slot 9 holds a phantom icon of the armor's ability while the player
 * pilots it. Selecting that slot uses the ability: combat armor punches harder, mobile armor runs faster, worker
 * armor mines (see {@link WorkerMining}).
 * <p>
 * The server keeps the slot right every tick: the icon appears on getting in, disappears on getting out and is
 * deleted wherever else it turns up. What lay in slot 9 before moves to a free inventory slot, or is kept aside and
 * given back after getting out. The icon can never be dropped, swapped to the off hand or handed to an item frame.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class ArmorAbility {
    /** Hotbar slot 9. */
    public static final int SLOT = 8;

    private ArmorAbility() {
    }

    public static boolean isAbility(ItemStack stack) {
        return stack.getItem() instanceof ArmorAbilityItem;
    }

    /** An empty hand or the ability icon: what bare-hand rules and rendering treat as "nothing in the hand". */
    public static boolean isBareHand(ItemStack stack) {
        return stack.isEmpty() || isAbility(stack);
    }

    /** The variant whose ability this player has selected right now (piloting it, slot 9 selected), or null. */
    @Nullable
    public static ArmorVariant selected(Player player) {
        if (player.getMainHandItem().getItem() instanceof ArmorAbilityItem ability
                && player.getVehicle() instanceof PowerArmorEntity armor && armor.getVariant() == ability.variant()) {
            return ability.variant();
        }
        return null;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    /** Puts the slot in order for this player. Public for tests (mock players do not tick). */
    public static void sync(ServerPlayer player) {
        ArmorVariant expected = player.getVehicle() instanceof PowerArmorEntity armor ? armor.getVariant() : null;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof ArmorAbilityItem ability && (i != SLOT || ability.variant() != expected)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        // Picked up with the mouse, or moved into a chest or a crafting grid.
        if (isAbility(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != inventory && isAbility(slot.getItem())) {
                slot.set(ItemStack.EMPTY);
            }
        }
        if (expected != null) {
            ItemStack previous = inventory.getItem(SLOT);
            if (!isAbility(previous)) {
                // The icon first, so the displaced item cannot land in slot 9 again.
                inventory.setItem(SLOT, new ItemStack(PowerArmorRegistry.abilityItem(expected)));
                if (!previous.isEmpty() && !inventory.add(previous)) {
                    putAside(player, previous);
                }
            }
        } else {
            giveBack(player);
        }
    }

    private static void putAside(ServerPlayer player, ItemStack stack) {
        ItemStack kept = player.getData(PowerArmorRegistry.SET_ASIDE);
        if (kept.isEmpty()) {
            player.setData(PowerArmorRegistry.SET_ASIDE, stack.copy());
        } else {
            // Only one item can be kept aside; anything more goes to the ground rather than being lost.
            player.drop(stack.copy(), false);
        }
    }

    private static void giveBack(ServerPlayer player) {
        ItemStack kept = player.getData(PowerArmorRegistry.SET_ASIDE);
        if (kept.isEmpty()) {
            return;
        }
        player.setData(PowerArmorRegistry.SET_ASIDE, ItemStack.EMPTY);
        ItemStack stack = kept.copy();
        if (player.getInventory().getItem(SLOT).isEmpty()) {
            player.getInventory().setItem(SLOT, stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---------------------------------------------------------------- the icon never leaves the slot

    @SubscribeEvent
    static void onToss(ItemTossEvent event) {
        if (isAbility(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    /** No icon ever lies in the world: not after a death, a destroyed body or anything else. */
    @SubscribeEvent
    static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && isAbility(item.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (isAbility(event.getItemSwappedToMainHand()) || isAbility(event.getItemSwappedToOffHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (isAbility(event.getItemStack()) && takesItems(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onInteractEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isAbility(event.getItemStack()) && takesItems(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    private static boolean takesItems(Entity target) {
        return target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Allay;
    }

    // ---------------------------------------------------------------- abilities

    /** Combat armor: a punch with the ability slot selected hits harder. */
    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof Player player && event.getSource().getEntity() == player
                && selected(player) == ArmorVariant.COMBAT) {
            event.setAmount(event.getAmount() + RebirthConfig.ARMOR_ABILITY_ATTACK_BONUS.get().floatValue());
        }
    }
}
