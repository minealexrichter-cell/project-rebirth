package com.projectrebirth.monitoring;

import com.mojang.serialization.MapCodec;
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

/** The monitoring station (1.6.0, block E): opens its window, a comparator reads the contamination near it. */
public class MonitoringStationBlock extends WorkingMachineBlock {
    public static final MapCodec<MonitoringStationBlock> CODEC = simpleCodec(MonitoringStationBlock::new);

    public MonitoringStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MonitoringStationBlockEntity station) {
            serverPlayer.openMenu(station, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitoringStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != MonitoringRegistry.MONITORING_STATION_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((MonitoringStationBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MonitoringStationBlockEntity station ? station.comparator() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MonitoringStationBlockEntity station) {
            station.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
