package com.projectrebirth.multiblock;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;

/**
 * 1.8.0: a controller with a plan, whichever kind (an assembler, the storage): the hologram of the wrench and the Codex draws its
 * construction from this.
 */
public interface SchemaHolder {
    @Nullable
    MultiblockSchema schema();

    Direction facing();
}
