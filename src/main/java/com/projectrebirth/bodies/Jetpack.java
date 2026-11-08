package com.projectrebirth.bodies;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.upgrades.UpgradeRegistry;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The jetpack of a body. A player's movement is decided by their own client, so the client lifts the body while the
 * jump key is held in the air; the server keeps the thrust reserve, decides whether the flight is allowed, and only
 * then shows the exhaust and keeps the flight from counting as illegal flying. The same rules in every dimension.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Jetpack {
    /** How far above the limit a flight still counts as legal: position updates arrive late. */
    private static final double HEIGHT_TOLERANCE = 2.0;
    /** The thrust sound is one short sound repeated by the server, so that everybody nearby hears it and it ends with the flight. */
    private static final int THRUST_SOUND_TICKS = 6;
    private static final Set<UUID> WANTS_THRUST = new HashSet<>();
    /**
     * Where a body was when its thrust stopped. A client with the jump key held can land and rise again between two
     * checks of the game, and then the game forgets the fall; so the fall after a flight is also counted here.
     */
    private static final Map<UUID, Double> FALLING_FROM = new HashMap<>();

    private Jetpack() {
    }

    public static boolean equipped(Player player) {
        return BodyGadgets.has(player.level().registryAccess(), BodyGadgets.of(player), BodyGadgets.JETPACK);
    }

    private static int capacityTicks() {
        return (int) Math.round(RebirthConfig.JETPACK_THRUST_SECONDS.get() * 20);
    }

    /** Part of the thrust reserve that is left, from 0 to 1. */
    public static float reserve(Player player) {
        return Mth.clamp(1.0f - player.getData(UpgradeRegistry.JETPACK_USED) / capacityTicks(), 0.0f, 1.0f);
    }

    /** Height of the feet above the nearest solid block or liquid under them; {@code max + 1} if none within {@code max} blocks. */
    public static double heightAboveSurface(Level level, Vec3 feet, int max) {
        BlockPos start = BlockPos.containing(feet);
        for (int down = 0; down <= max + 1; down++) {
            BlockPos pos = start.below(down);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return feet.y - (pos.getY() + 1);
            }
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty() && pos.getY() + shape.max(Direction.Axis.Y) <= feet.y) {
                return feet.y - (pos.getY() + shape.max(Direction.Axis.Y));
            }
        }
        return max + 1;
    }

    /** Whether the body may fly right now, apart from the key and the reserve. */
    public static boolean inFlightConditions(Player player) {
        return !player.onGround() && !player.isPassenger() && !player.isInWater() && !player.isInLava()
                && !player.getAbilities().flying && !player.isDeadOrDying();
    }

    /** One tick of the server's side. Public for tests; called for every player in a body with a jetpack. */
    public static void serverTick(ServerPlayer player, boolean wantsThrust) {
        float used = player.getData(UpgradeRegistry.JETPACK_USED);
        int capacity = capacityTicks();
        int limit = RebirthConfig.JETPACK_MAX_HEIGHT.get();
        boolean flying = wantsThrust && used < capacity && inFlightConditions(player) && !BodyPower.isEmpty(player)
                && heightAboveSurface(player.level(), player.position(), limit + (int) HEIGHT_TOLERANCE) <= limit + HEIGHT_TOLERANCE;
        if (flying) {
            player.setData(UpgradeRegistry.JETPACK_USED, Math.min(capacity, used + 1.0f));
            BodyPower.spend(player, RebirthConfig.BODY_ENERGY_JETPACK.get() / 20.0);
            player.fallDistance = 0.0f;
            player.connection.aboveGroundTickCount = 0;
            // The server hears of a fall late: while it still counts as a flight, a falling body keeps its height.
            Double from = FALLING_FROM.get(player.getUUID());
            boolean rising = from == null || player.getY() >= player.yo;
            FALLING_FROM.put(player.getUUID(), rising ? player.getY() : Math.max(from, player.getY()));
            exhaust(player);
            return;
        }
        landIfOnGround(player);
        if (used > 0.0f && player.onGround()) {
            float recharge = capacity / (float) (RebirthConfig.JETPACK_RECHARGE_SECONDS.get() * 20);
            player.setData(UpgradeRegistry.JETPACK_USED, Math.max(0.0f, used - recharge));
        }
    }

    /** The fall after a flight. The game's own landing clears this first if it comes first; only one of the two counts. */
    private static void landIfOnGround(ServerPlayer player) {
        Double from = FALLING_FROM.get(player.getUUID());
        if (from != null && player.onGround()) {
            FALLING_FROM.remove(player.getUUID());
            float distance = (float) (from - player.getY());
            player.fallDistance = 0.0f;
            if (distance > 0.0f) {
                player.causeFallDamage(distance, 1.0f, player.damageSources().fall());
            }
        }
    }

    /** Whether a fall after a flight is still being watched. Public for tests. */
    public static boolean watchingFall(Player player) {
        return FALLING_FROM.containsKey(player.getUUID());
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        FALLING_FROM.remove(event.getEntity().getUUID());
    }

    private static void exhaust(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double angle = Math.toRadians(player.yBodyRot);
        double x = player.getX() + Math.sin(angle) * 0.35;
        double z = player.getZ() - Math.cos(angle) * 0.35;
        level.sendParticles(ParticleTypes.FLAME, x, player.getY() + 0.7, z, 1, 0.05, 0.0, 0.05, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, x, player.getY() + 0.6, z, 1, 0.05, 0.0, 0.05, 0.01);
        if (player.tickCount % THRUST_SOUND_TICKS == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.JETPACK_THRUST.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && TransferService.isInArtificialBody(player) && equipped(player)) {
            serverTick(player, WANTS_THRUST.contains(player.getUUID()));
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WANTS_THRUST.remove(event.getEntity().getUUID());
        FALLING_FROM.remove(event.getEntity().getUUID());
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Jetpack::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ThrustPayload.TYPE, ThrustPayload.STREAM_CODEC, Jetpack::handle);
    }

    private static void handle(ThrustPayload payload, IPayloadContext context) {
        if (payload.wanted()) {
            WANTS_THRUST.add(context.player().getUUID());
        } else {
            WANTS_THRUST.remove(context.player().getUUID());
        }
    }

    /** Client to server: the jump key is held (or no longer held) in the air. The server decides what it means. */
    public record ThrustPayload(boolean wanted) implements CustomPacketPayload {
        public static final Type<ThrustPayload> TYPE = new Type<>(ProjectRebirth.id("jetpack_thrust"));
        public static final StreamCodec<ByteBuf, ThrustPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(ThrustPayload::new, ThrustPayload::wanted);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
