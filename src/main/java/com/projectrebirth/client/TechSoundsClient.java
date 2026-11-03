package com.projectrebirth.client;

import java.util.HashMap;
import java.util.Map;

import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.CombustionGeneratorBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Looping machine sounds on the client: at most one loop per machine; it fades in, and fades out once the machine
 * stops working or is gone.
 */
final class TechSoundsClient {
    private static final float FADE_PER_TICK = 0.1f;
    private static final Map<BlockPos, MachineLoop> PLAYING = new HashMap<>();

    private TechSoundsClient() {
    }

    static void register() {
        TechSounds.loops = TechSoundsClient::keepPlaying;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PLAYING.clear());
    }

    private static void keepPlaying(Level level, BlockPos pos, SoundEvent sound) {
        MachineLoop loop = PLAYING.get(pos);
        if (loop == null || loop.isStopped() || loop.level != level) {
            loop = new MachineLoop(level, pos.immutable(), sound);
            PLAYING.put(loop.pos, loop);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
    }

    private static final class MachineLoop extends AbstractTickableSoundInstance {
        private final Level level;
        private final BlockPos pos;
        private float fade;

        MachineLoop(Level level, BlockPos pos, SoundEvent sound) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.level = level;
            this.pos = pos;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0f;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            BlockState state = level.getBlockState(pos);
            boolean working = state.hasProperty(TechSounds.WORKING) ? state.getValue(TechSounds.WORKING)
                    : state.hasProperty(CombustionGeneratorBlock.LIT) && state.getValue(CombustionGeneratorBlock.LIT);
            fade = Math.max(0.0f, Math.min(1.0f, fade + (working ? FADE_PER_TICK : -FADE_PER_TICK)));
            volume = fade;
            if (!working && fade <= 0.0f) {
                PLAYING.remove(pos, this);
                stop();
            }
        }
    }
}
