package com.projectrebirth.environment;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block that radiates or pollutes around itself (1.6.0): radioactive debris and the leaking waste barrel. The strength is in
 * {@link SourceKind}; the block only holds the entity that keeps it in the index. It drops nothing, so that no one carries it
 * to another person's land (the structures place it, concept 15.4).
 */
public class SourceBlock extends BaseEntityBlock {
    public static final MapCodec<SourceBlock> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            com.mojang.serialization.Codec.STRING.xmap(SourceKind::valueOf, SourceKind::name).fieldOf("kind").forGetter(SourceBlock::kind))
            .apply(instance, SourceBlock::new));
    private static final VoxelShape BARREL_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
    private static final VoxelShape SLUDGE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

    private final SourceKind kind;

    public SourceBlock(Properties properties, SourceKind kind) {
        super(properties);
        this.kind = kind;
    }

    public SourceKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case WASTE_BARREL -> BARREL_SHAPE;
            case SLUDGE -> SLUDGE_SHAPE;
            default -> super.getShape(state, level, pos, context);
        };
    }

    /** A block just placed is in the index at once (the entity's own load may come a tick later); a loaded chunk is brought back by the entity. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            RadiationSources.register(server, pos, kind);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SourceBlockEntity(pos, state);
    }

    /** Client only: now and then a drip from the barrel and a flake from the debris. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind == SourceKind.WASTE_BARREL && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.FALLING_SPORE_BLOSSOM, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.05,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
        } else if (kind == SourceKind.SLUDGE && random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + random.nextDouble(), pos.getY() + 0.15, pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
        } else if (kind == SourceKind.DEBRIS && random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.ASH, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
        }
    }
}
