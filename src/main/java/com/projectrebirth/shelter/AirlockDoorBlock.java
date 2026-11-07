package com.projectrebirth.shelter;

import com.projectrebirth.core.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/** The airlock door: a door that hisses as its seal lets go or closes. */
public class AirlockDoorBlock extends DoorBlock {
    public AirlockDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hitResult);
        if (!level.isClientSide && level.getBlockState(pos).getValue(OPEN) != state.getValue(OPEN)) {
            level.playSound(null, pos, ModSounds.AIRLOCK_HISS.get(), SoundSource.BLOCKS, 0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        }
        return result;
    }
}
