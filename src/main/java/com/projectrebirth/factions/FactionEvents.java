package com.projectrebirth.factions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.TransferCapsuleBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class FactionEvents {
    private static final UUID NO_TERRITORY = new UUID(0L, 0L);
    /** Territory each online player was in at the last check. */
    private static final Map<UUID, UUID> lastTerritory = new HashMap<>();

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
            // this client's enemies are shown in red, everyone else in their faction colour
            boolean enemy = ClientFactionCache.local().map(own -> own.isEnemyOf(view.get())).orElse(false);
            color = enemy ? ChatFormatting.RED : view.get().color();
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

    /**
     * Territory protection: outsiders cannot open containers and machines or use doors, allies only use doors,
     * recruits use doors but not containers. Only using the block is refused, so blocks can still be placed
     * against it. Checked on the server.
     */
    @SubscribeEvent
    static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        FactionService.Use use = useOf(player.level(), pos);
        if (!FactionService.mayUse(player, pos, use)) {
            event.setUseBlock(TriState.FALSE);
            FactionService.territoryAt(player.server, player.level(), pos).ifPresent(faction -> player.displayClientMessage(
                    Component.translatable("message.projectrebirth.faction.protected",
                            Component.literal(faction.name()).withStyle(faction.color())), true));
        }
    }

    static FactionService.Use useOf(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock) {
            return FactionService.Use.DOOR;
        }
        if (level.getBlockEntity(pos) != null || block instanceof TransferCapsuleBlock) {
            return FactionService.Use.CONTAINER;
        }
        return FactionService.Use.FREE;
    }

    /** Tells players when they cross into or out of a faction's territory (checked once a second). */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<Faction> here = FactionService.territoryAt(server, player.level(), player.blockPosition());
            UUID id = here.map(Faction::id).orElse(NO_TERRITORY);
            UUID before = lastTerritory.put(player.getUUID(), id);
            if (before != null && !before.equals(id)) {
                player.displayClientMessage(here
                        .map(faction -> Component.translatable("message.projectrebirth.faction.entered",
                                Component.literal(faction.name()).withStyle(faction.color())))
                        .orElse(Component.translatable("message.projectrebirth.faction.wilderness")), true);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastTerritory.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        FactionCommands.register(event.getDispatcher());
    }
}
