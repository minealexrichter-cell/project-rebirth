package com.projectrebirth.bodies.power;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/**
 * The charging pad: a low platform. A body standing on it, empty or controlled, is charged from the energy network.
 * Right-click with a body battery puts it in to charge; right-click with an empty hand takes it out, or tells the state.
 */
public class ChargingPadBlock extends BaseEntityBlock {
    public static final MapCodec<ChargingPadBlock> CODEC = simpleCodec(ChargingPadBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

    public ChargingPadBlock(Properties properties) {
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof BodyBatteryItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChargingPadBlockEntity pad) {
            if (pad.battery().isEmpty()) {
                pad.setBattery(stack.split(1));
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projectrebirth.charging_pad.occupied"), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ChargingPadBlockEntity pad) {
            if (!pad.battery().isEmpty()) {
                ItemStack battery = pad.battery();
                pad.setBattery(ItemStack.EMPTY);
                if (!player.getInventory().add(battery)) {
                    player.drop(battery, false);
                }
            } else {
                serverPlayer.displayClientMessage(pad.status(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ChargingPadBlockEntity pad && !pad.battery().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, pad.battery());
            pad.setBattery(ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingPadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BodyPowerRegistry.CHARGING_PAD_ENTITY.get(),
                (tickLevel, pos, tickState, pad) -> pad.serverTick());
    }
}
