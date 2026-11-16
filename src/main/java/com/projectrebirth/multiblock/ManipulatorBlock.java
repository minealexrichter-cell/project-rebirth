package com.projectrebirth.multiblock;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.Vec3;

/**
 * A manipulator of an assembly hall (1.7.0, block B): a robot arm on a mount, in three kinds by its tool. Its controller links
 * it, turns the block toward the machine under assembly and sets it working while it builds; the arm then reaches the machine
 * every frame (see {@link ManipulatorPose}): the welder runs its torch over it in sparks, the gripper carries parts to it, the
 * screwdriver drives screws into it. Any kind fits any manipulator place of a plan.
 */
public class ManipulatorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ManipulatorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(Tool::values).fieldOf("tool").forGetter(ManipulatorBlock::tool),
            propertiesCodec()).apply(instance, ManipulatorBlock::new));

    /** The tool on the arm; its tip is that many pixels in front of the wrist. */
    public enum Tool implements StringRepresentable {
        WELDER("welder", "welding_manipulator", 6.75f),
        GRIPPER("gripper", "gripper_manipulator", 6.5f),
        SCREWDRIVER("screwdriver", "screwdriver_manipulator", 8.0f);

        private final String id;
        private final String blockId;
        private final float reach;

        Tool(String id, String blockId, float reach) {
            this.id = id;
            this.blockId = blockId;
            this.reach = reach;
        }

        /** The block's name, also the name of its model and texture. */
        public String blockId() {
            return blockId;
        }

        public float reach() {
            return reach;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    private final Tool tool;

    public ManipulatorBlock(Tool tool, Properties properties) {
        super(properties);
        this.tool = tool;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TechSounds.WORKING, false));
    }

    public Tool tool() {
        return tool;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TechSounds.WORKING);
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
        return new ManipulatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != MultiblockRegistry.MANIPULATOR_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> clientTick(tickLevel, (ManipulatorBlockEntity) entity);
    }

    /** Client: the sparks of the torch where it touches, the hum of the screwdriver, the click of the gripper letting go. */
    private static void clientTick(Level level, ManipulatorBlockEntity manipulator) {
        ManipulatorPose.Pose pose = ManipulatorPose.compute(manipulator, 0.0f);
        if (!pose.working()) {
            return;
        }
        Vec3 tip = pose.tip();
        var random = level.random;
        switch (manipulator.tool()) {
            case WELDER -> {
                if (pose.touching()) {
                    for (int i = 0; i < 2; i++) {
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z,
                                (random.nextDouble() - 0.5) * 0.25, random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.25);
                    }
                    if (random.nextInt(3) == 0) {
                        level.addParticle(ParticleTypes.SMALL_FLAME, tip.x, tip.y, tip.z, 0.0, 0.005, 0.0);
                    }
                    if (random.nextInt(25) == 0) {
                        level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.12f, 1.9f, false);
                    }
                }
            }
            case SCREWDRIVER -> {
                if (pose.touching() && random.nextInt(10) == 0) {
                    level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.35f, 2.0f, false);
                    level.addParticle(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 0.0, 0.02, 0.0);
                }
            }
            case GRIPPER -> {
                if (pose.released()) {
                    level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.4f, 1.4f, false);
                }
            }
        }
    }
}
