package com.projectrebirth.multiblock;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A port of a multiblock (1.7.0, block A): the energy port takes FE from the mod's cable and from other mods, the cargo port
 * lets hoppers and pipes bring materials in and take the finished items out. A port serves the controller whose construction it
 * is part of; on its own it does nothing.
 */
public class PortBlock extends BaseEntityBlock {
    public static final String ENERGY = "energy";
    public static final String CARGO = "cargo";

    private final String kind;

    public PortBlock(String kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new PortBlock(kind, properties));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortBlockEntity(pos, state);
    }
}
