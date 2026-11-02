package com.projectrebirth.bodies;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game events that keep the transfer state consistent: death, respawn, login, and artificial body limitations. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class BodyEvents {
    /** Artificial bodies do not starve and do not regenerate from food (natural regeneration needs 18+). */
    private static final int ARTIFICIAL_FOOD_LEVEL = 17;

    private BodyEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TransferService.handleControlledBodyDeath(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        // The possession attachment is not copied on death; make sure no body stats survive either.
        if (event.isWasDeath()) {
            TransferService.removeBodyModifiers(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Re-apply so that config changes made while the player was offline take effect.
            TransferService.state(player).body().ifPresent(bodyClass -> TransferService.applyBodyModifiers(player, bodyClass));
            player.syncData(BodyAttachments.POSSESSION);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0
                && TransferService.isInArtificialBody(player)) {
            player.getFoodData().setFoodLevel(ARTIFICIAL_FOOD_LEVEL);
            player.getFoodData().setSaturation(0.0f);
        }
    }

    @SubscribeEvent
    static void onStartUsingItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && TransferService.isInArtificialBody(player)
                && event.getItem().has(DataComponents.FOOD)) {
            event.setCanceled(true);
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.body.cannot_eat"), true);
            }
        }
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        BodyCommands.register(event.getDispatcher());
    }
}
