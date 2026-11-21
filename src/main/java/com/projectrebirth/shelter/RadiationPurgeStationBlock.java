package com.projectrebirth.shelter;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The radiation purge station: a low platform to stand on. Right-click tells your dose and whether it works, and why not. */
public class RadiationPurgeStationBlock extends BaseEntityBlock {
    public static final MapCodec<RadiationPurgeStationBlock> CODEC = simpleCodec(RadiationPurgeStationBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public RadiationPurgeStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TechSounds.WORKING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TechSounds.WORKING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RadiationPurgeStationBlockEntity station) {
            // 1.8.0: a window instead of the line in the action bar
            com.projectrebirth.technology.energy.EnergyPanelMenu.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadiationPurgeStationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ShelterRegistry.RADIATION_PURGE_STATION_ENTITY.get(),
                (tickLevel, pos, tickState, station) -> station.serverTick());
    }
}
