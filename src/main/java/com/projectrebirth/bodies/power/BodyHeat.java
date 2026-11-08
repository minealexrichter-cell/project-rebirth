package com.projectrebirth.bodies.power;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Heat of artificial bodies: a scale from 0 to 100, not degrees. Running, blows, mining, the jetpack, hot biomes, the Nether,
 * fire around and burning heat a body; rest, water, rain, cold biomes, shade and the cooling module cool it, and better
 * classes cool faster. Above the warning level the body warns; above the throttle level it throttles (slower, weaker blows,
 * no jetpack). At 100 it is overheated until it cools below the throttle level again (what that wears down comes with the
 * wear of block B2). Overheating never destroys a body and never takes control away. Kept like the charge: in the standing
 * body, in the player's data while controlled; a packed body has cooled down. A power armor never heats up: a body inside one
 * only cools. Server side, once a second per body; the client reads the synced heat for the HUD and its jetpack.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class BodyHeat {
    /** Upgrade kind of the cooling module gadget (upgrade/cooling_module.json). */
    public static final String COOLING = "cooling";
    public static final float MAX = 100.0f;

    private static final ResourceLocation SPEED_MODIFIER = ProjectRebirth.id("body_heat_speed");
    private static final ResourceLocation ATTACK_MODIFIER = ProjectRebirth.id("body_heat_attack");
    private static final double MOVING_DISTANCE = 0.3;

    public enum State {
        NORMAL, WARM, THROTTLED, OVERHEATED
    }

    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();
    private static final Set<UUID> WORKED = new HashSet<>();
    /** Reached 100 and has not yet cooled below the throttle level. Lost on a restart, like the other per-second memories. */
    private static final Set<UUID> OVERHEATED = new HashSet<>();
    private static final Map<UUID, State> LAST_STATE = new HashMap<>();

    private BodyHeat() {
    }

    public static boolean enabled() {
        return RebirthConfig.BODY_HEAT_ENABLED.get();
    }

    public static float heat(Player player) {
        return TransferService.isInArtificialBody(player) ? Mth.clamp(player.getData(BodyPowerRegistry.CONTROLLED_HEAT), 0.0f, MAX) : 0.0f;
    }

    public static void setHeat(Player player, float heat) {
        player.setData(BodyPowerRegistry.CONTROLLED_HEAT, Mth.clamp(heat, 0.0f, MAX));
    }

    private static boolean inArmor(LivingEntity entity) {
        return entity.getVehicle() instanceof PowerArmorEntity;
    }

    /** Heat from work (blows, mining, the jetpack); nothing for a human, in creative, in a power armor or with the system off. */
    public static void add(Player player, double amount) {
        if (!enabled() || amount <= 0.0 || !TransferService.isInArtificialBody(player) || player.getAbilities().instabuild
                || player.isSpectator() || inArmor(player)) {
            return;
        }
        setHeat(player, heat(player) + (float) amount);
        WORKED.add(player.getUUID());
    }

    public static State state(Player player) {
        if (!enabled() || !TransferService.isInArtificialBody(player)) {
            return State.NORMAL;
        }
        float heat = heat(player);
        if (OVERHEATED.contains(player.getUUID()) || heat >= MAX) {
            return State.OVERHEATED;
        }
        if (heat > RebirthConfig.HEAT_THROTTLE.get()) {
            return State.THROTTLED;
        }
        return heat >= RebirthConfig.HEAT_WARNING.get() ? State.WARM : State.NORMAL;
    }

    /** Slower, weaker and without a jetpack. Works on both sides (the client only knows the heat, which is enough). */
    public static boolean isThrottled(Player player) {
        return enabled() && TransferService.isInArtificialBody(player) && heat(player) > RebirthConfig.HEAT_THROTTLE.get();
    }

    /** Whether the body has reached 100 and not yet cooled below the throttle level (for the wear of block B2). */
    public static boolean isOverheated(Player player) {
        return state(player) == State.OVERHEATED;
    }

    private static double coolingMultiplier(BodyClass bodyClass) {
        return RebirthConfig.bodyStats(bodyClass).coolingMultiplier().get();
    }

    // ---------------------------------------------------------------- once a second

    /** The heat of one second for a player in a body. Public for tests. */
    public static void tickSecond(ServerPlayer player) {
        BodyClass bodyClass = TransferService.state(player).body().orElse(null);
        if (bodyClass == null) {
            return;
        }
        boolean worked = WORKED.remove(player.getUUID());
        Vec3 now = player.position();
        Vec3 before = LAST_POSITION.put(player.getUUID(), now);
        if (!enabled()) {
            applyLimits(player);
            return;
        }
        boolean moved = before != null && Math.sqrt(Mth.square(now.x - before.x) + Mth.square(now.z - before.z)) > MOVING_DISTANCE;
        boolean exempt = player.getAbilities().instabuild || player.isSpectator() || inArmor(player);
        double gain = 0.0;
        if (!exempt) {
            if (moved && player.isSprinting()) {
                gain += RebirthConfig.HEAT_SPRINT.get();
            }
            gain += climateHeat(player.level(), player.blockPosition());
            gain += fireHeat(player.level(), player.blockPosition());
            if (player.isOnFire()) {
                gain += RebirthConfig.HEAT_BURNING.get();
            }
        }
        double cooling = cooling(player, bodyClass, !moved && !worked);
        float heat = heat(player);
        setHeat(player, heat + (float) (gain - cooling));
        // a body at 100 at the start or at the end of the second has overheated, even if it cools at once
        if (heat >= MAX || heat(player) >= MAX) {
            OVERHEATED.add(player.getUUID());
        } else if (heat(player) <= RebirthConfig.HEAT_THROTTLE.get()) {
            OVERHEATED.remove(player.getUUID());
        }
        applyLimits(player);
    }

    /** Heat of a hot biome or a dimension where water boils. */
    public static double climateHeat(Level level, BlockPos pos) {
        double heat = 0.0;
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        double from = RebirthConfig.HEAT_HOT_BIOME_FROM.get();
        if (temperature > from) {
            heat += (temperature - from) * RebirthConfig.HEAT_HOT_BIOME.get();
        }
        if (level.dimensionType().ultraWarm()) {
            heat += RebirthConfig.HEAT_NETHER.get();
        }
        return heat;
    }

    /** Heat of fire, lava, magma and lit campfires around; a few dozen blocks, once a second. */
    public static double fireHeat(Level level, BlockPos center) {
        int radius = RebirthConfig.HEAT_FIRE_RADIUS.get();
        if (radius <= 0) {
            return 0.0;
        }
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 2, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) {
                count++;
            }
        }
        return Math.min(count * RebirthConfig.HEAT_NEAR_FIRE.get(), RebirthConfig.HEAT_NEAR_FIRE_MAX.get());
    }

    private static double cooling(LivingEntity entity, BodyClass bodyClass, boolean resting) {
        double cooling = RebirthConfig.COOL_BASE.get();
        if (resting) {
            cooling += RebirthConfig.COOL_REST.get();
        }
        if (entity.isInWater()) {
            cooling += RebirthConfig.COOL_WATER.get();
        } else if (entity.isInWaterOrRain()) {
            cooling += RebirthConfig.COOL_RAIN.get();
        }
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        if (level.getBiome(pos).value().getBaseTemperature() < RebirthConfig.COOL_COLD_BIOME_BELOW.get()) {
            cooling += RebirthConfig.COOL_COLD_BIOME.get();
        }
        if (!level.canSeeSky(pos.above())) {
            cooling += RebirthConfig.COOL_SHADE.get();
        }
        var upgrades = entity instanceof ArtificialBodyEntity body ? body.upgrades()
                : entity instanceof Player player ? BodyGadgets.of(player) : null;
        if (upgrades != null && BodyGadgets.has(level.registryAccess(), upgrades, COOLING)) {
            cooling += RebirthConfig.COOLING_MODULE_COOLING.get();
        }
        return cooling * coolingMultiplier(bodyClass);
    }

    /** A standing empty body does no work and only cools down. */
    public static void tickStanding(ArtificialBodyEntity body) {
        if (body.heat() > 0.0f) {
            body.setHeat(body.heat() - (float) cooling(body, body.getBodyClass(), true));
        }
    }

    // ---------------------------------------------------------------- limits

    public static void applyLimits(Player player) {
        State state = state(player);
        boolean throttled = isThrottled(player) || state == State.OVERHEATED && enabled();
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, throttled ? -RebirthConfig.HEAT_THROTTLE_SLOWDOWN.get() : 0.0);
        setModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, throttled ? -RebirthConfig.HEAT_THROTTLE_ATTACK.get() : 0.0);
        if (player instanceof ServerPlayer serverPlayer) {
            State last = LAST_STATE.put(player.getUUID(), state);
            if (last != null && state.ordinal() > last.ordinal()) {
                String key = switch (state) {
                    case WARM -> "warm";
                    case THROTTLED -> "throttled";
                    default -> "overheated";
                };
                serverPlayer.displayClientMessage(Component.translatable("message.projectrebirth.body_heat." + key)
                        .withStyle(state == State.WARM ? ChatFormatting.GOLD : ChatFormatting.RED), false);
            } else if (last != null && last.ordinal() >= State.THROTTLED.ordinal() && state.ordinal() < State.THROTTLED.ordinal()) {
                serverPlayer.displayClientMessage(Component.translatable("message.projectrebirth.body_heat.cooled").withStyle(ChatFormatting.GREEN), false);
            }
        }
    }

    public static void removeLimits(Player player) {
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, 0.0);
        setModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, 0.0);
        LAST_STATE.remove(player.getUUID());
        LAST_POSITION.remove(player.getUUID());
        OVERHEATED.remove(player.getUUID());
        WORKED.remove(player.getUUID());
    }

    private static void setModifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0.0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_POSITION.remove(id);
        WORKED.remove(id);
        LAST_STATE.remove(id);
    }
}
