package com.projectrebirth.powerarmor;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/** A packed-up power armor. Right-clicking a block places it there, keeping its stored damage. */
public class PowerArmorItem extends Item {
    public PowerArmorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(context.getClickedFace());
        PowerArmorEntity armor = PowerArmorRegistry.POWER_ARMOR.get().create(level);
        if (armor == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? Mth.wrapDegrees(player.getYRot() + 180.0f) : 0.0f;
        armor.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        armor.setYHeadRot(yaw);
        armor.setYBodyRot(yaw);
        if (!level.noCollision(armor)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.armor.no_space"), true);
            }
            return InteractionResult.FAIL;
        }
        if (player != null) {
            armor.setOwner(player.getUUID());
        }
        ItemStack stack = context.getItemInHand();
        Float structural = stack.get(PowerArmorRegistry.STORED_STRUCTURAL.get());
        Float critical = stack.get(PowerArmorRegistry.STORED_CRITICAL.get());
        level.addFreshEntity(armor);
        // Maximums come from the config and are applied when the armor joins the level.
        armor.setIntegrity(structural != null ? structural : armor.getMaxStructural(),
                critical != null ? critical : armor.getMaxCritical());
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Float structural = stack.get(PowerArmorRegistry.STORED_STRUCTURAL.get());
        Float critical = stack.get(PowerArmorRegistry.STORED_CRITICAL.get());
        if (structural != null) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.armor.structural", Mth.ceil(structural)).withStyle(ChatFormatting.RED));
        }
        if (critical != null) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.armor.critical", Mth.ceil(critical)).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.armor.hint").withStyle(ChatFormatting.GRAY));
    }
}
