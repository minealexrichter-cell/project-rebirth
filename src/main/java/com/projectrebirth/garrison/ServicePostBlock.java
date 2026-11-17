package com.projectrebirth.garrison;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The console of a service post (1.7.0, block H): set it next to a square of four post plates and a chest; cables bring it energy.
 * Right-click tells whether the plates are in place, its energy and how many machines it serves.
 */
public class ServicePostBlock extends BaseEntityBlock {
    public static final MapCodec<ServicePostBlock> CODEC = simpleCodec(ServicePostBlock::new);

    public ServicePostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServicePostBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != GarrisonRegistry.SERVICE_POST_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> ((ServicePostBlockEntity) entity).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServicePostBlockEntity post) {
            Component message = post.plates() == null ? Component.translatable("message.projectrebirth.service_post.no_plates")
                    : Component.translatable("message.projectrebirth.service_post.status", post.energy().getEnergyStored(),
                    post.energy().getMaxEnergyStored(), post.served().size());
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
