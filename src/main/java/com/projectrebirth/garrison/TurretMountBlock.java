package com.projectrebirth.garrison;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The turret mount block (1.7.0, block G): a defence turret is set up on top of it. Right-click with assault rifle rounds or
 * rockets to load them; with an empty hand it tells its energy and ammunition. Broken, it drops what it holds.
 */
public class TurretMountBlock extends BaseEntityBlock {
    public static final MapCodec<TurretMountBlock> CODEC = simpleCodec(TurretMountBlock::new);

    public TurretMountBlock(Properties properties) {
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
        return new TurretMountBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.getItem() instanceof TurretItem) {
            // 1.7.3: let the turret item set itself up on the mount (it used to show the mount's state and take the click)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!TurretMountBlockEntity.isAmmo(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretMountBlockEntity mount) {
            ItemStack left = ItemHandlerHelper.insertItem(mount.ammo(), stack.copy(), false);
            int put = stack.getCount() - left.getCount();
            if (!player.isCreative()) {
                stack.shrink(put);
            }
            tell(player, mount);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretMountBlockEntity mount
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 1.8.0: the mount's window (ammunition, energy, the turret on it)
            serverPlayer.openMenu(TurretMountMenu.provider(mount), buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void tell(Player player, TurretMountBlockEntity mount) {
        player.displayClientMessage(Component.translatable("message.projectrebirth.turret_mount.status", mount.energy().getEnergyStored(),
                mount.energy().getMaxEnergyStored(), mount.count(WeaponRegistry.ASSAULT_RIFLE_AMMO.get()), mount.count(GarrisonRegistry.ROCKET.get())), true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TurretMountBlockEntity mount) {
            for (int i = 0; i < TurretMountBlockEntity.SLOTS; i++) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), mount.ammo().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
