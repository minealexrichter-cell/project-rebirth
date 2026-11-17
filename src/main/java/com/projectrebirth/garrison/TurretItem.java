package com.projectrebirth.garrison;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A packed defence turret (1.7.0, block G): it is set up only on top of a turret mount, which feeds it. Its bars are its type's
 * (the rocket turret's are its own in the config).
 */
public class TurretItem extends PackedMachineItem {
    private final TurretType turret;

    public TurretItem(TurretType turret, Supplier<? extends EntityType<? extends MachineEntity>> type, Properties properties) {
        super(MachineKind.TURRET, type, properties);
        this.turret = turret;
    }

    public TurretType turret() {
        return turret;
    }

    @Override
    public MachineCore.Packed fresh() {
        return new MachineCore.Packed(MachineKind.TURRET, turret.maxStructural(), turret.maxCritical(), 0.0f, Optional.empty(), "", "");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        if (!(level.getBlockEntity(clicked) instanceof TurretMountBlockEntity) || context.getClickedFace() != Direction.UP) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.machine.needs_mount"), true);
            return InteractionResult.FAIL;
        }
        MachineEntity machine = place(level, player, context.getItemInHand(), clicked.above(), Mth.wrapDegrees(player.getYRot() + 180.0f));
        return machine != null ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.projectrebirth.turret.hint").withStyle(ChatFormatting.GRAY));
    }
}
