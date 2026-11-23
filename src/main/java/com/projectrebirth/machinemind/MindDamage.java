package com.projectrebirth.machinemind;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.WastelandConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Damage to the modules of a node: tools break off a piece, bullets and explosions hurt. Nothing breaks a living one at once. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class MindDamage {
    private MindDamage() {
    }

    /** The module at a place (its own block or its box). */
    @Nullable
    public static MindModuleBlockEntity moduleAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MindModuleBlockEntity entity) {
            return entity;
        }
        return level.getBlockState(pos).is(MindRegistry.PART.get()) ? MindPartBlock.owner(level, pos, null) : null;
    }

    /** A hit on a block; false if it is no living module. */
    public static boolean hit(Level level, BlockPos pos, float amount) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        MindModuleBlockEntity module = moduleAt(server, pos);
        if (module == null || !module.active()) {
            return false;
        }
        module.damage(server, amount);
        return true;
    }

    /** One explosion hurts a module once, however many blocks of it it reaches. */
    static boolean explosion(ServerLevel level, BlockPos pos) {
        MindModuleBlockEntity module = moduleAt(level, pos);
        if (module == null || !module.active()) {
            return false;
        }
        if (module.lastExplosion != level.getGameTime()) {
            module.lastExplosion = level.getGameTime();
            module.damage(level, WastelandConfig.MIND_EXPLOSION_DAMAGE.get().floatValue());
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer().isCreative()) {
            return;
        }
        if (hit(level, event.getPos(), WastelandConfig.MIND_BREAK_DAMAGE.get().floatValue())) {
            event.setCanceled(true);
        }
    }
}
