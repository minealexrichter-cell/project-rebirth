package com.projectrebirth.multiblock;

import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** The manipulator's arm: an idle sway, and welding while its controller builds (the WORKING state). */
public class ManipulatorBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.manipulator.idle");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("animation.manipulator.work");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ManipulatorBlockEntity(BlockPos pos, BlockState state) {
        super(MultiblockRegistry.MANIPULATOR_ENTITY.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8,
                state -> state.setAndContinue(getBlockState().getValue(TechSounds.WORKING) ? WORK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
