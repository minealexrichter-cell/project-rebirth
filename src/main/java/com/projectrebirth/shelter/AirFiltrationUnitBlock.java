package com.projectrebirth.shelter;

import java.util.Locale;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.Hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The air filtration unit: the start of a sealed room. Placed inside, it finds the room by the air around it and cleans that
 * air while it has energy and a filter cartridge. Right-click opens its window.
 */
public class AirFiltrationUnitBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<AirFiltrationUnitBlock> CODEC = simpleCodec(AirFiltrationUnitBlock::new);

    public AirFiltrationUnitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer,
            net.minecraft.world.item.ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof AirFiltrationUnitBlockEntity unit) {
            unit.setOwner(player.getUUID());
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirFiltrationUnitBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AirFiltrationUnitBlockEntity unit) {
            serverPlayer.openMenu(unit, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AirFiltrationUnitBlockEntity unit) {
            unit.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ShelterRegistry.AIR_FILTRATION_UNIT_ENTITY.get(),
                (tickLevel, pos, tickState, unit) -> unit.serverTick());
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** One line about the room of a unit. */
    public static Component status(ServerLevel level, BlockPos pos) {
        Shelters shelters = Shelters.find(level);
        Room room = shelters == null ? null : shelters.roomOf(pos);
        if (room == null) {
            return Component.translatable("message.projectrebirth.shelter.scanning");
        }
        if (!room.isSealed()) {
            BlockPos at = room.leakPos();
            return Component.translatable("message.projectrebirth.shelter.leak",
                    Component.translatable("shelter.projectrebirth.leak." + room.leak().id()), at.getX(), at.getY(), at.getZ());
        }
        float outside = Environment.ordinaryExposureAt(level, pos).get(Hazard.POLLUTION);
        return Component.translatable("message.projectrebirth.shelter.sealed", room.volume(), room.units().size(),
                number(room.pollution()), number(outside), room.partialLeaks());
    }
}
