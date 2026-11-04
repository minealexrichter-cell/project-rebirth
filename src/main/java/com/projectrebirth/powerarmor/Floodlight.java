package com.projectrebirth.powerarmor;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The floodlight of a power armor. Light in Minecraft comes only from blocks, so an invisible block stands where the
 * pilot looks and moves along. It cannot stay behind: the block itself checks every two seconds that an armor is using
 * it, and that list lives only in memory, so after a restart, a crash or an unloaded armor it removes itself.
 */
public final class Floodlight {
    private static final int UPDATE_TICKS = 4;
    private static final int CHECK_TICKS = 40;
    /** How many armors use each light; per level object, so a level that is gone takes its list along. */
    private static final Map<Level, Map<BlockPos, Integer>> IN_USE = new WeakHashMap<>();

    private Floodlight() {
    }

    public static boolean isInUse(Level level, BlockPos pos) {
        Map<BlockPos, Integer> lights = IN_USE.get(level);
        return lights != null && lights.containsKey(pos);
    }

    private static void acquire(Level level, BlockPos pos) {
        IN_USE.computeIfAbsent(level, key -> new HashMap<>()).merge(pos.immutable(), 1, Integer::sum);
    }

    private static void release(Level level, BlockPos pos) {
        Map<BlockPos, Integer> lights = IN_USE.get(level);
        if (lights == null) {
            return;
        }
        lights.computeIfPresent(pos, (key, count) -> count > 1 ? count - 1 : null);
        // An unloaded chunk cannot be changed; there the block removes itself when the chunk comes back.
        if (!lights.containsKey(pos) && level.isLoaded(pos) && level.getBlockState(pos).is(PowerArmorRegistry.FLOODLIGHT_LIGHT.get())) {
            level.removeBlock(pos, false);
        }
    }

    /** Where the armor should have its light now, or null for none. Public for tests. */
    @Nullable
    public static BlockPos target(PowerArmorEntity armor) {
        Player pilot = armor.getPilot();
        if (pilot == null || !armor.isFloodlightOn() || !armor.isActive() || !RebirthConfig.FLOODLIGHT_ENABLED.get()
                || !ArmorUpgradeEffects.hasFloodlight(armor)) {
            return null;
        }
        Level level = armor.level();
        Vec3 from = pilot.getEyePosition();
        Vec3 to = from.add(pilot.getLookAngle().scale(RebirthConfig.FLOODLIGHT_RANGE.get()));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pilot));
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().relative(hit.getDirection()) : BlockPos.containing(to);
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(PowerArmorRegistry.FLOODLIGHT_LIGHT.get()) ? pos : null;
    }

    /** Moves the armor's light; returns where it is now. */
    @Nullable
    public static BlockPos update(PowerArmorEntity armor, @Nullable BlockPos current) {
        BlockPos wanted = target(armor);
        if (wanted == null ? current == null : wanted.equals(current)) {
            return current;
        }
        Level level = armor.level();
        if (wanted != null) {
            acquire(level, wanted);
            if (!level.getBlockState(wanted).is(PowerArmorRegistry.FLOODLIGHT_LIGHT.get())) {
                level.setBlockAndUpdate(wanted, PowerArmorRegistry.FLOODLIGHT_LIGHT.get().defaultBlockState());
            }
        }
        if (current != null) {
            release(level, current);
        }
        return wanted;
    }

    /** How often an armor looks for a new place for its light. */
    public static boolean timeToUpdate(PowerArmorEntity armor) {
        return armor.tickCount % UPDATE_TICKS == 0;
    }

    /** The armor is gone or its light went out. */
    public static void clear(Level level, @Nullable BlockPos current) {
        if (current != null) {
            release(level, current);
        }
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Floodlight::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TogglePayload.TYPE, TogglePayload.STREAM_CODEC, Floodlight::handle);
    }

    private static void handle(TogglePayload payload, IPayloadContext context) {
        if (context.player().getVehicle() instanceof PowerArmorEntity armor && armor.getPilot() == context.player()) {
            toggle(armor, context.player());
        }
    }

    /** The pilot's key. Public for tests. */
    public static void toggle(PowerArmorEntity armor, Player pilot) {
        if (!RebirthConfig.FLOODLIGHT_ENABLED.get()) {
            pilot.displayClientMessage(Component.translatable("message.projectrebirth.armor.floodlight_disabled"), true);
        } else if (!ArmorUpgradeEffects.hasFloodlight(armor)) {
            pilot.displayClientMessage(Component.translatable("message.projectrebirth.armor.no_floodlight"), true);
        } else {
            armor.setFloodlightOn(!armor.isFloodlightOn());
            armor.level().playSound(null, armor.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.NEUTRAL, 0.6f, armor.isFloodlightOn() ? 1.2f : 0.8f);
            pilot.displayClientMessage(Component.translatable(armor.isFloodlightOn()
                    ? "message.projectrebirth.armor.floodlight_on" : "message.projectrebirth.armor.floodlight_off"), true);
        }
    }

    /** Client to server: the pilot pressed the floodlight key. */
    public record TogglePayload() implements CustomPacketPayload {
        public static final TogglePayload INSTANCE = new TogglePayload();
        public static final Type<TogglePayload> TYPE = new Type<>(ProjectRebirth.id("floodlight_toggle"));
        public static final StreamCodec<ByteBuf, TogglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The light itself: nothing to see, touch or break, and it removes itself when no armor uses it. */
    public static final class LightBlock extends Block {
        public static final MapCodec<LightBlock> CODEC = simpleCodec(LightBlock::new);

        public LightBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.INVISIBLE;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            if (!level.isClientSide) {
                level.scheduleTick(pos, this, CHECK_TICKS);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (isInUse(level, pos)) {
                level.scheduleTick(pos, this, CHECK_TICKS);
            } else {
                level.removeBlock(pos, false);
            }
        }
    }
}
