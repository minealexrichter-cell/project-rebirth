package com.projectrebirth.multiblock;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A manipulator's arm. It knows the controller it serves (set by the controller when the construction is whole, sent to the
 * clients); the arm has no keyframe animation: {@link ManipulatorPose} bends it every frame toward the machine under assembly.
 */
public class ManipulatorBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private BlockPos controller;
    /** On the client: the pose drawn last, for the part in the gripper. */
    @Nullable
    private ManipulatorPose.Pose lastPose;

    public ManipulatorBlockEntity(BlockPos pos, BlockState state) {
        super(MultiblockRegistry.MANIPULATOR_ENTITY.get(), pos, state);
    }

    public ManipulatorBlock.Tool tool() {
        return getBlockState().getBlock() instanceof ManipulatorBlock block ? block.tool() : ManipulatorBlock.Tool.WELDER;
    }

    @Nullable
    public BlockPos controller() {
        return controller;
    }

    /** Server: the controller whose construction this arm is part of now (null: none). */
    public void setController(@Nullable BlockPos controller) {
        if (java.util.Objects.equals(this.controller, controller)) {
            return;
        }
        this.controller = controller == null ? null : controller.immutable();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    public ManipulatorPose.Pose lastPose() {
        return lastPose;
    }

    public void setLastPose(ManipulatorPose.Pose pose) {
        this.lastPose = pose;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controller != null) {
            tag.put("controller", NbtUtils.writeBlockPos(controller));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controller = NbtUtils.readBlockPos(tag, "controller").orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (controller != null) {
            tag.put("controller", NbtUtils.writeBlockPos(controller));
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
