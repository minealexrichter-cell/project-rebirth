package com.projectrebirth.machinemind;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** The computer of a node or a module of its factory (see {@link MindModule}); the rest of its box is filled by {@link MindPartBlock}. */
public class MindModuleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<MindModuleBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(MindModule::values).fieldOf("module").forGetter(MindModuleBlock::module),
            propertiesCodec()).apply(instance, MindModuleBlock::new));

    private final MindModule module;

    public MindModuleBlock(MindModule module, Properties properties) {
        super(properties);
        this.module = module;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public MindModule module() {
        return module;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return module == MindModule.COMPUTER ? new MindComputerBlockEntity(pos, state) : new MindModuleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return (l, pos, s, entity) -> ((MindModuleBlockEntity) entity).clientTick(l, pos);
        }
        return (l, pos, s, entity) -> ((MindModuleBlockEntity) entity).serverTick((ServerLevel) l, pos);
    }

    /** Before any drops: a living module is hurt and stays. */
    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, BlockPos> drops) {
        if (level.isClientSide || !MindDamage.explosion((ServerLevel) level, pos)) {
            super.onExplosionHit(state, level, pos, explosion, drops);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) {
            if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof MindModuleBlockEntity entity) {
                entity.removed(server);
            }
            for (BlockPos part : module.box(pos, 3)) {
                if (!part.equals(pos) && level.getBlockState(part).is(MindRegistry.PART.get()) && MindPartBlock.owner(level, part, pos) == null) {
                    level.removeBlock(part, false);
                }
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
