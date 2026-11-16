package com.projectrebirth.garrison;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.technology.WorkingMachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The console block (1.7.0, block D): opens its window for those who may command it; gives a redstone signal while an alarm is
 * on and a comparator signal by the share of its machines in working order. Its placer owns it.
 */
public class ConsoleBlock extends WorkingMachineBlock {
    public static final MapCodec<ConsoleBlock> CODEC = simpleCodec(ConsoleBlock::new);

    public ConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof ConsoleBlockEntity console) {
            console.setOwner(player.getUUID());
        }
    }

    /** With the tactical tablet in hand the click goes to the tablet (it links itself), not to the window. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                                 net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof TabletItem) {
            return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ConsoleBlockEntity console) {
            if (!console.mayCommand(player)) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.console.not_yours"), true);
            } else {
                serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, who) -> new ConsoleMenu(id, inventory, pos),
                        Component.translatable("block.projectrebirth.console")), buf -> buf.writeBlockPos(pos));
                ConsoleNetwork.sendView(serverPlayer, console);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConsoleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != GarrisonRegistry.CONSOLE_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((ConsoleBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ConsoleBlockEntity console ? console.signal() : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ConsoleBlockEntity console ? console.comparator() : 0;
    }
}
