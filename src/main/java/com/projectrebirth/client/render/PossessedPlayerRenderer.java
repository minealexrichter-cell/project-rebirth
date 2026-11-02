package com.projectrebirth.client.render;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Draws a player who controls an artificial body with that body's model instead of the player model. */
public class PossessedPlayerRenderer extends GeoReplacedEntityRenderer<Player, PossessedPlayerRenderer.BodyAnimatable> {
    public PossessedPlayerRenderer(EntityRendererProvider.Context context, BodyClass bodyClass) {
        super(context, BodyModels.create(bodyClass), new BodyAnimatable());
        this.shadowRadius = 0.4f;
    }

    /** One shared animatable per body class; GeckoLib keeps separate animation state for each player. */
    public static class BodyAnimatable implements GeoReplacedEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        @Override
        public EntityType<?> getReplacingEntityType() {
            return EntityType.PLAYER;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 3,
                    state -> state.setAndContinue(state.isMoving() ? ArtificialBodyEntity.WALK : ArtificialBodyEntity.IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }
}
