package com.projectrebirth.bodies;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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

/** A packed-up artificial body. Right-clicking a block places the body there. */
public class ArtificialBodyItem extends Item {
    private final BodyClass bodyClass;

    public ArtificialBodyItem(BodyClass bodyClass, Properties properties) {
        super(properties);
        this.bodyClass = bodyClass;
    }

    public BodyClass bodyClass() {
        return bodyClass;
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

        ArtificialBodyEntity body = BodyEntities.type(bodyClass).create(level);
        if (body == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? Mth.wrapDegrees(player.getYRot() + 180.0f) : 0.0f;
        body.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        if (!level.noCollision(body)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.body.no_space"), true);
            }
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            body.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }
        if (player != null) {
            body.setOwner(player.getUUID());
        }
        Float storedIntegrity = stack.get(BodyComponents.STORED_INTEGRITY.get());
        level.addFreshEntity(body);
        // Max health comes from the config and is applied when the body joins the level.
        body.setHealth(storedIntegrity != null ? storedIntegrity : body.getMaxHealth());
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Float storedIntegrity = stack.get(BodyComponents.STORED_INTEGRITY.get());
        if (storedIntegrity != null) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.body.damaged", Mth.ceil(storedIntegrity))
                    .withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.body.hint").withStyle(ChatFormatting.GRAY));
    }
}
