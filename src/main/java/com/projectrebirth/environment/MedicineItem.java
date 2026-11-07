package com.projectrebirth.environment;

import java.util.function.Consumer;

import com.projectrebirth.bodies.TransferService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** A medicine for the human body, taken like a potion. An artificial body has nothing to treat. */
public class MedicineItem extends Item {
    private static final int USE_TICKS = 32;

    private final Consumer<Player> effect;

    public MedicineItem(Properties properties, Consumer<Player> effect) {
        super(properties);
        this.effect = effect;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (TransferService.isInArtificialBody(player)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.environment.medicine_no_biology"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            take(stack, player);
        }
        return stack;
    }

    /** Public for tests. */
    public void take(ItemStack stack, ServerPlayer player) {
        effect.accept(player);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }
}
