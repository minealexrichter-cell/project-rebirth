package com.projectrebirth.multiblock;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A port remembers the controller it serves; the capabilities of the port are those of that controller. */
public class PortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controller;

    public PortBlockEntity(BlockPos pos, BlockState state) {
        super(MultiblockRegistry.PORT_ENTITY.get(), pos, state);
    }

    @Nullable
    public BlockPos controllerPos() {
        return controller;
    }

    /** Links the port to a controller, or unlinks it (null); the cables and hoppers around are told. */
    public void link(@Nullable BlockPos pos) {
        if (pos == null ? controller == null : pos.equals(controller)) {
            return;
        }
        controller = pos == null ? null : pos.immutable();
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    /** The controller it serves (an assembler, 1.8.0: or a storage), if that is loaded, whole and still names this port. */
    @Nullable
    public PortHost controller() {
        if (controller == null || level == null || !level.isLoaded(controller)) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof PortHost found && found.serves(worldPosition) ? found : null;
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
}
