package com.projectrebirth.factions;

import java.util.Optional;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class FactionEvents {
    private FactionEvents() {
    }

    /**
     * Friendly fire. Runs first, before the power armor takes over its pilot's damage. The attacker is the player
     * behind the damage (also through arrows and other projectiles, while piloting armor or controlling a body);
     * the victim side is a player, a piloted or owned power armor, an owned artificial body or a human body.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (FactionService.blocksDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FactionService.rememberName(player);
            FactionSync.sendTo(player);
        }
    }

    /** "[Faction] Name" above the head and in chat, in the faction colour. Works on both sides. */
    @SubscribeEvent
    static void onNameFormat(PlayerEvent.NameFormat event) {
        tag(event.getEntity()).ifPresent(tag -> event.setDisplayname(tag.append(event.getDisplayname())));
    }

    @SubscribeEvent
    static void onTabListName(PlayerEvent.TabListNameFormat event) {
        Player player = event.getEntity();
        tag(player).ifPresent(tag -> event.setDisplayName(tag.append(
                event.getDisplayName() != null ? event.getDisplayName() : Component.literal(player.getGameProfile().getName()))));
    }

    private static Optional<MutableComponent> tag(Player player) {
        String name;
        ChatFormatting color;
        if (player.level().isClientSide) {
            Optional<FactionView> view = ClientFactionCache.of(player.getUUID());
            if (view.isEmpty()) {
                return Optional.empty();
            }
            name = view.get().name();
            color = view.get().color();
        } else {
            MinecraftServer server = player.getServer();
            Optional<Faction> faction = server == null ? Optional.empty() : FactionData.get(server).of(player.getUUID());
            if (faction.isEmpty()) {
                return Optional.empty();
            }
            name = faction.get().name();
            color = faction.get().color();
        }
        return Optional.of(Component.literal("[" + name + "] ").withStyle(color));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        FactionCommands.register(event.getDispatcher());
    }
}
