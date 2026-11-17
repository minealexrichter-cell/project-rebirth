package com.projectrebirth.bodies;

import java.util.List;

import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.upgrades.Upgrades;

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

        Player placer = context.getPlayer();
        if (com.projectrebirth.garrison.Androids.isAndroid(context.getItemInHand())) {
            // 1.7.0: an AI core in it: it stands up an android
            if (!(placer instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return InteractionResult.FAIL;
            }
            float androidYaw = Mth.wrapDegrees(placer.getYRot() + 180.0f);
            return com.projectrebirth.garrison.Androids.place(level, serverPlayer, context.getItemInHand(), bodyClass, pos, androidYaw) != null
                    ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
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
        Float storedCharge = stack.get(BodyPowerRegistry.STORED_CHARGE.get());
        body.setUpgrades(Upgrades.ofStack(stack));
        if (storedCharge != null) {
            body.setCharge(storedCharge);
        }
        Float storedWear = stack.get(BodyPowerRegistry.STORED_WEAR.get());
        if (storedWear != null) {
            body.setWear(storedWear);
        }
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
        Float storedCharge = stack.get(BodyPowerRegistry.STORED_CHARGE.get());
        if (storedCharge != null && BodyPower.enabled() && context.registries() instanceof net.minecraft.core.RegistryAccess access) {
            float capacity = BodyPower.capacity(access, bodyClass, Upgrades.ofStack(stack));
            tooltip.add(Component.translatable("tooltip.projectrebirth.body.charge", Math.round(Math.min(storedCharge, capacity) * 100.0f / capacity))
                    .withStyle(ChatFormatting.YELLOW));
        }
        Float storedWear = stack.get(BodyPowerRegistry.STORED_WEAR.get());
        if (storedWear != null && storedWear > 0.0f && com.projectrebirth.bodies.power.BodyWear.enabled()) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.body.wear", Math.round(storedWear))
                    .withStyle(storedWear >= com.projectrebirth.bodies.power.BodyWear.MAX ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        Upgrades.addTooltip(stack, tooltip);
        // 1.7.0: a body with an AI core is a combat android, a machine of its owner
        var parts = stack.get(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get());
        if (parts != null) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.android.parts", parts.module().getHoverName()).withStyle(ChatFormatting.AQUA));
            var packed = stack.get(com.projectrebirth.garrison.GarrisonRegistry.PACKED_MACHINE.get());
            if (packed != null) {
                if (!packed.ownerName().isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.projectrebirth.android.owner", packed.ownerName()).withStyle(ChatFormatting.GRAY));
                }
                float maxCritical = com.projectrebirth.technology.Maintenance.maxAndroidCritical(stack);
                if (packed.critical() < maxCritical) {
                    tooltip.add(Component.translatable("tooltip.projectrebirth.android.critical", Mth.ceil(packed.critical()), Mth.ceil(maxCritical))
                            .withStyle(ChatFormatting.RED));
                }
            }
            tooltip.add(Component.translatable("tooltip.projectrebirth.android.hint").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.body.hint").withStyle(ChatFormatting.GRAY));
    }
}
