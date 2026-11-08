package com.projectrebirth.bodies.power;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.upgrades.InstalledUpgrades;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The charge of artificial bodies. A body standing in the world keeps it in its entity, a packed body in its item, and the
 * body a player controls in the player's data, like its upgrades: it always moves from one place to the next, so it cannot
 * be doubled. Work spends it (walking, running, blows, breaking blocks, self-repair, the jetpack), the charging pad and the
 * body battery refill it. Low charge slows the body down; no charge is the emergency mode: slow walking only, no running,
 * jumping, jetpack or self-repair, slower mining. The body never loses control and Z always works. All on the server, once
 * a second per body; the client only reads the synced charge.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class BodyPower {
    /** Upgrade kind of the extra battery gadget (data/projectrebirth/projectrebirth/upgrade/extra_battery.json). */
    public static final String BATTERY = "battery";

    private static final ResourceLocation SPEED_MODIFIER = ProjectRebirth.id("body_power_speed");
    private static final ResourceLocation JUMP_MODIFIER = ProjectRebirth.id("body_power_jump");
    /** Below this, a body counts as moving no more: standing still, pushed a little. */
    private static final double MOVING_DISTANCE = 0.3;

    public enum State {
        NORMAL, LOW, EMPTY
    }

    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();
    private static final Map<UUID, State> LAST_STATE = new HashMap<>();

    private BodyPower() {
    }

    public static boolean enabled() {
        return RebirthConfig.BODY_ENERGY_ENABLED.get();
    }

    // ---------------------------------------------------------------- capacity and charge

    public static float capacity(RegistryAccess access, BodyClass bodyClass, InstalledUpgrades upgrades) {
        float capacity = RebirthConfig.bodyStats(bodyClass).energyCapacity().get();
        if (BodyGadgets.has(access, upgrades, BATTERY)) {
            capacity += RebirthConfig.EXTRA_BATTERY_CAPACITY.get();
        }
        return capacity;
    }

    /** Capacity of the body this player controls, 0 for a human. */
    public static float capacity(Player player) {
        Optional<BodyClass> bodyClass = TransferService.state(player).body();
        return bodyClass.map(found -> capacity(player.level().registryAccess(), found, BodyGadgets.of(player))).orElse(0.0f);
    }

    public static float capacity(ArtificialBodyEntity body) {
        return capacity(body.level().registryAccess(), body.getBodyClass(), body.upgrades());
    }

    /** Charge of the body this player controls, in FE; 0 for a human. Unknown (a world from 1.4.1) counts as full. */
    public static float charge(Player player) {
        if (!TransferService.isInArtificialBody(player)) {
            return 0.0f;
        }
        float capacity = capacity(player);
        float charge = player.getData(BodyPowerRegistry.CONTROLLED_CHARGE);
        return charge < 0.0f ? capacity : Math.min(charge, capacity);
    }

    public static void setCharge(Player player, float charge) {
        player.setData(BodyPowerRegistry.CONTROLLED_CHARGE, Mth.clamp(charge, 0.0f, capacity(player)));
    }

    /** Part of the charge left, from 0 to 1. */
    public static float fraction(Player player) {
        float capacity = capacity(player);
        return capacity > 0.0f ? Mth.clamp(charge(player) / capacity, 0.0f, 1.0f) : 0.0f;
    }

    /** Adds charge to the controlled body (pad, battery); returns how much it took. */
    public static float addCharge(Player player, float amount) {
        float before = charge(player);
        setCharge(player, before + Math.max(0.0f, amount));
        return charge(player) - before;
    }

    public static float useMultiplier(BodyClass bodyClass) {
        return RebirthConfig.bodyStats(bodyClass).energyUseMultiplier().get().floatValue();
    }

    /** Spends this much work (before the class's multiplier) of the controlled body's charge. Nothing for a human or with the system off. */
    public static void spend(Player player, double amount) {
        // creative players (instant building) and spectators spend nothing
        if (!enabled() || amount <= 0.0 || player.getAbilities().instabuild || player.isSpectator()) {
            return;
        }
        TransferService.state(player).body().ifPresent(bodyClass ->
                setCharge(player, charge(player) - (float) (amount * useMultiplier(bodyClass))));
    }

    public static State state(Player player) {
        if (!enabled() || !TransferService.isInArtificialBody(player)) {
            return State.NORMAL;
        }
        float charge = charge(player);
        if (charge <= 0.0f) {
            return State.EMPTY;
        }
        return charge < capacity(player) * RebirthConfig.BODY_ENERGY_LOW_SHARE.get() ? State.LOW : State.NORMAL;
    }

    /** The emergency mode: the controlled body has no charge left. Works on both sides. */
    public static boolean isEmpty(Player player) {
        return state(player) == State.EMPTY;
    }

    /** A standing body without charge does not mend itself. */
    public static boolean isEmpty(ArtificialBodyEntity body) {
        return enabled() && body.charge() <= 0.0f;
    }

    // ---------------------------------------------------------------- once a second

    /** Called once a second for a player in a body: the steady use, then the state's limits and messages. Public for tests. */
    public static void tickSecond(ServerPlayer player) {
        Vec3 now = player.position();
        Vec3 before = LAST_POSITION.put(player.getUUID(), now);
        if (before != null && !player.isPassenger()) {
            double moved = Math.sqrt(Mth.square(now.x - before.x) + Mth.square(now.z - before.z));
            double work = RebirthConfig.BODY_ENERGY_IDLE.get();
            if (moved > MOVING_DISTANCE) {
                work += player.isSprinting() ? RebirthConfig.BODY_ENERGY_SPRINT.get() : RebirthConfig.BODY_ENERGY_WALK.get();
            }
            spend(player, work);
        } else {
            spend(player, RebirthConfig.BODY_ENERGY_IDLE.get());
        }
        applyLimits(player);
    }

    /** Sets the slowdown and the missing jump of the current state, and tells the player when the state gets worse or better. */
    public static void applyLimits(Player player) {
        State state = state(player);
        double slowdown = switch (state) {
            case NORMAL -> 0.0;
            case LOW -> RebirthConfig.BODY_ENERGY_LOW_SLOWDOWN.get();
            case EMPTY -> RebirthConfig.BODY_ENERGY_EMPTY_SLOWDOWN.get();
        };
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, -slowdown);
        setModifier(player, Attributes.JUMP_STRENGTH, JUMP_MODIFIER, state == State.EMPTY ? -1.0 : 0.0);
        if (player instanceof ServerPlayer serverPlayer) {
            State last = LAST_STATE.put(player.getUUID(), state);
            if (last != null && last != state) {
                announce(serverPlayer, last, state);
            }
        }
    }

    private static void announce(ServerPlayer player, State last, State now) {
        if (now == State.EMPTY) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.body_power.empty").withStyle(ChatFormatting.RED), false);
        } else if (now == State.LOW && last == State.NORMAL) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.body_power.low",
                    Math.round(RebirthConfig.BODY_ENERGY_LOW_SHARE.get() * 100)).withStyle(ChatFormatting.GOLD), false);
        } else if (last == State.EMPTY) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.body_power.restored").withStyle(ChatFormatting.GREEN), false);
        }
    }

    public static void removeLimits(Player player) {
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, 0.0);
        setModifier(player, Attributes.JUMP_STRENGTH, JUMP_MODIFIER, 0.0);
        LAST_STATE.remove(player.getUUID());
        LAST_POSITION.remove(player.getUUID());
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

    /** A standing empty body: no use by default (standingPerSecond = 0). */
    public static void tickStanding(ArtificialBodyEntity body) {
        double perSecond = RebirthConfig.BODY_ENERGY_STANDING.get();
        if (enabled() && perSecond > 0.0) {
            body.setCharge(body.charge() - (float) (perSecond * useMultiplier(body.getBodyClass())));
        }
    }

    // ---------------------------------------------------------------- work

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide) {
            spend(event.getEntity(), RebirthConfig.BODY_ENERGY_ATTACK.get());
            BodyHeat.add(event.getEntity(), RebirthConfig.HEAT_ATTACK.get());
        }
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getPlayer().level().isClientSide) {
            spend(event.getPlayer(), RebirthConfig.BODY_ENERGY_BLOCK.get());
            BodyHeat.add(event.getPlayer(), RebirthConfig.HEAT_BLOCK.get());
        }
    }

    /** Both sides: the client must dig as slowly as the server or the block flickers back. */
    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (isEmpty(event.getEntity())) {
            event.setNewSpeed(event.getNewSpeed() * RebirthConfig.BODY_ENERGY_EMPTY_BREAK_SPEED.get().floatValue());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POSITION.remove(event.getEntity().getUUID());
        LAST_STATE.remove(event.getEntity().getUUID());
    }
}
