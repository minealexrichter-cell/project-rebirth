package com.projectrebirth.factions;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Faction core: placed by a leader or officer, it makes a square around itself the faction's territory.
 * Anyone can break it (slowly; explosions do not), and the territory disappears with it. Which faction a core
 * belongs to is stored in the faction data by position, so the block itself carries no data.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public class FactionCoreBlock extends Block {
    public static final MapCodec<FactionCoreBlock> CODEC = simpleCodec(FactionCoreBlock::new);

    public FactionCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Placement is decided before the block stays: without permission or with overlapping territory it is undone. */
    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getPlacedBlock().getBlock() instanceof FactionCoreBlock)
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof Level level)) {
            return;
        }
        FactionService.Result result = FactionService.canPlaceCore(player, level, event.getPos());
        player.displayClientMessage(result.message(), true);
        if (result.success()) {
            FactionService.addCore(player, level, event.getPos());
        } else {
            event.setCanceled(true);
        }
    }

    /** In peace only the own faction may break its core; enemies may during an active war while a defender is online. */
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof FactionCoreBlock) || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof Level level)) {
            return;
        }
        FactionService.Result result = FactionService.mayBreakCore(player, level, event.getPos());
        if (!result.success()) {
            event.setCanceled(true);
            player.displayClientMessage(result.message(), true);
        }
    }

    /** Right click shows whose core this is. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            Optional<Faction> owner = FactionData.get(serverPlayer.server).all().stream()
                    .filter(faction -> faction.cores().stream().anyMatch(core -> core.dimension().equals(level.dimension())
                            && core.pos().equals(pos)))
                    .findFirst();
            serverPlayer.displayClientMessage(owner
                    .map(faction -> Component.translatable("message.projectrebirth.faction.core_info",
                            Component.literal(faction.name()).withStyle(faction.color()), FactionService.coreRadius()))
                    .orElse(Component.translatable("message.projectrebirth.faction.core_inactive")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        MinecraftServer server = level.getServer();
        if (server != null && !state.is(newState.getBlock())) {
            FactionService.removeCore(server, level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
