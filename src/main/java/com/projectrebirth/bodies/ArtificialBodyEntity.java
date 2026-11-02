package com.projectrebirth.bodies;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * An inactive artificial body standing in the world. Shared by all body classes;
 * per-class stats come from {@link RebirthConfig}.
 */
public class ArtificialBodyEntity extends PathfinderMob implements GeoEntity {
    public static final String MAIN_CONTROLLER = "main";
    public static final String INTERACT_ANIMATION = "interact";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.body.idle");
    private static final RawAnimation INTERACT = RawAnimation.begin().thenPlay("animation.body.interact");

    private final BodyClass bodyClass;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public ArtificialBodyEntity(EntityType<? extends ArtificialBodyEntity> type, Level level, BodyClass bodyClass) {
        super(type, level);
        this.bodyClass = bodyClass;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    public BodyClass getBodyClass() {
        return bodyClass;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) {
            applyClassStats();
        }
    }

    /** Re-applied on every load so that config changes reach existing bodies. */
    private void applyClassStats() {
        RebirthConfig.BodyStats stats = RebirthConfig.bodyStats(bodyClass);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.maxHealth().get());
        getAttribute(Attributes.ARMOR).setBaseValue(stats.armor().get());
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            packUp(player);
        } else {
            player.displayClientMessage(Component.translatable("message.projectrebirth.body.status",
                    getDisplayName(), Mth.ceil(getHealth()), Mth.ceil(getMaxHealth())), true);
            triggerAnim(MAIN_CONTROLLER, INTERACT_ANIMATION);
        }
        return InteractionResult.CONSUME;
    }

    /** Turns the body back into an item. The entity is removed first, so it can never be packed twice. */
    private void packUp(Player player) {
        if (!isAlive()) {
            return;
        }
        ItemStack stack = new ItemStack(BodyItems.item(bodyClass));
        if (getHealth() < getMaxHealth()) {
            stack.set(BodyComponents.STORED_INTEGRITY.get(), getHealth());
        }
        if (hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, getCustomName());
        }
        discard();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(BodyItems.item(bodyClass));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, MAIN_CONTROLLER, 5, state -> state.setAndContinue(IDLE))
                .triggerableAnim(INTERACT_ANIMATION, INTERACT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
