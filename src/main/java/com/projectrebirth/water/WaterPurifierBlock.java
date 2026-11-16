package com.projectrebirth.water;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.WorkingMachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The water purifier (1.6.0, block C): opens its window, shows when it works. */
public class WaterPurifierBlock extends WorkingMachineBlock {
    public static final MapCodec<WaterPurifierBlock> CODEC = simpleCodec(WaterPurifierBlock::new);

    public WaterPurifierBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof WaterPurifierBlockEntity purifier) {
            serverPlayer.openMenu(purifier, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterPurifierBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != WaterRegistry.WATER_PURIFIER_ENTITY.get()) {
            return null;
        }
        if (level.isClientSide) {
            return TechSounds.clientLoop(TechSounds.WORKING, ModSounds.FILTRATION_HUM.get());
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((WaterPurifierBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WaterPurifierBlockEntity purifier) {
            purifier.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
