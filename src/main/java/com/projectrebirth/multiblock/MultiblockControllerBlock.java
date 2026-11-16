package com.projectrebirth.multiblock;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.codex.CodexRegistry;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.WorkingMachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The controller of a multiblock (1.7.0, block A): opens its window, remembers who placed it, and shows the hologram of its
 * construction to whoever right-clicks it with the wrench or the Codex. A controller block names its plan; the generic one of the
 * automated tests has none and gets it set.
 */
public class MultiblockControllerBlock extends WorkingMachineBlock {
    /** Set by the client side: shows or hides the hologram of the construction at that place. */
    public static Consumer<BlockPos> hologramToggle = pos -> {
    };

    @Nullable
    private final ResourceLocation schema;

    public MultiblockControllerBlock(@Nullable ResourceLocation schema, Properties properties) {
        super(properties);
        this.schema = schema;
    }

    @Nullable
    public ResourceLocation schema() {
        return schema;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new MultiblockControllerBlock(schema, properties));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.setOwner(player.getUUID());
        }
    }

    public static boolean showsHologram(ItemStack stack) {
        return stack.is(PowerArmorRegistry.REPAIR_WRENCH.get()) || stack.is(CodexRegistry.REBIRTH_CODEX.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!showsHologram(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            hologramToggle.accept(pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            // the land of a faction: only those who may use its machines open the window
            if (!FactionService.mayUse(serverPlayer, pos, FactionService.Use.CONTAINER)) {
                serverPlayer.displayClientMessage(Component.translatable("message.projectrebirth.multiblock.not_yours"), true);
                return InteractionResult.CONSUME;
            }
            serverPlayer.openMenu(controller, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != MultiblockRegistry.CONTROLLER_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((MultiblockControllerBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
