package com.projectrebirth.garrison;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Emi;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionRole;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.upgrades.InstalledUpgrades;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * What every machine of a player has (1.7.0, block C), one code for androids, turrets and the mech: its owner (the faction is
 * the owner's), a name, the link to a console, two integrity bars like a power armor's, wear, the state (working, shut down, a
 * wreck), whom it remembers attacking it or its people (self-defence), and how the environment treats it. A machine entity holds
 * one and calls it from its own hurt, tick and save; the entity shows the values to the clients.
 *
 * <ul>
 *   <li>structural: any damage; at 0 the machine shuts down, all further damage goes into critical; comes back by itself after
 *   a pause without damage, with iron and the repair wrench, at the maintenance station;</li>
 *   <li>critical: explosions and fire at once (config), any damage once structural is gone; at 0 the machine becomes a wreck;
 *   only the maintenance station mends it (the machine is packed up first).</li>
 * </ul>
 */
public class MachineCore {
    public enum State {
        ACTIVE, DISABLED, WRECK;

        public static State byId(int id) {
            State[] values = values();
            return id >= 0 && id < values.length ? values[id] : ACTIVE;
        }
    }

    /**
     * A packed machine (the component of its item): its kind, integrity, wear, owner and name; its upgrades travel in the item's
     * upgrade component as for armor.
     */
    public record Packed(MachineKind kind, float structural, float critical, float wear, Optional<UUID> owner, String ownerName, String name) {
        public static final Codec<Packed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(MachineKind::byId, MachineKind::id).fieldOf("kind").forGetter(Packed::kind),
                Codec.FLOAT.fieldOf("structural").forGetter(Packed::structural),
                Codec.FLOAT.fieldOf("critical").forGetter(Packed::critical),
                Codec.FLOAT.optionalFieldOf("wear", 0.0f).forGetter(Packed::wear),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Packed::owner),
                Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Packed::ownerName),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Packed::name)).apply(i, Packed::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Packed> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

        /** A new machine of that kind: whole, no owner yet. */
        public static Packed fresh(MachineKind kind) {
            return new Packed(kind, kind.maxStructural(), kind.maxCritical(), 0.0f, Optional.empty(), "", "");
        }
    }

    private final MachineKind kind;
    private final Runnable changed;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private String name = "";
    private float structural;
    private float critical;
    private float wear;
    private float structuralLimit = -1.0f;
    private float criticalLimit = -1.0f;
    /** An android: its body already wears and gathers contamination; here only the interference counts. */
    private boolean environmentByBody;
    private State state = State.ACTIVE;
    private boolean intruderMode;
    private int secondsSinceDamage;
    private int registryCountdown;
    /** Not saved: whom this machine fights back, with the game time until when it remembers them. */
    private final Map<UUID, Long> attackers = new HashMap<>();
    /** Not saved: what the environment did to it this second (an EMI storm disturbs it, dust blinds it). */
    private boolean emiFault;
    private boolean dusty;
    /** Block D: the console this machine answers to (null: none), the order it follows, and whether it was hit by someone lately. */
    @Nullable
    private net.minecraft.core.GlobalPos console;
    private MachineTask task = MachineTask.STAND_DOWN;
    private boolean hitByAttacker;

    /** @param changed called whenever a value the clients see changes (the entity copies them into its synced data) */
    public MachineCore(MachineKind kind, Runnable changed) {
        this.kind = kind;
        this.changed = changed;
        this.structural = kind.maxStructural();
        this.critical = kind.maxCritical();
    }

    // ------------------------------------------------------------------ values

    public MachineKind kind() {
        return kind;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(@Nullable UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        changed.run();
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public float structural() {
        return structural;
    }

    public float critical() {
        return critical;
    }

    public float maxStructural() {
        return structuralLimit > 0.0f ? structuralLimit : kind.maxStructural();
    }

    public float maxCritical() {
        return criticalLimit > 0.0f ? criticalLimit : kind.maxCritical();
    }

    /**
     * Bars of its own instead of the kind's (an android: its body's integrity and a share of it); what is above the new limits
     * is cut.
     */
    public void setEnvironmentByBody(boolean byBody) {
        environmentByBody = byBody;
    }

    public void setLimits(float structural, float critical) {
        structuralLimit = structural;
        criticalLimit = critical;
        this.structural = Math.min(this.structural, maxStructural());
        this.critical = Math.min(this.critical, maxCritical());
        changed.run();
    }

    public float wear() {
        return wear;
    }

    public void setWear(float wear) {
        this.wear = Math.max(0.0f, Math.min(100.0f, wear));
    }

    public State state() {
        return state;
    }

    public boolean active() {
        return state == State.ACTIVE;
    }

    public boolean wreck() {
        return state == State.WRECK;
    }

    public boolean intruderMode() {
        return intruderMode;
    }

    public void setIntruderMode(boolean intruderMode) {
        this.intruderMode = intruderMode;
    }

    public boolean emiFault() {
        return emiFault;
    }

    public boolean dusty() {
        return dusty;
    }

    /** Sets both bars (clamped) and the state they mean; for placing a packed machine and for tests. */
    public void setIntegrity(float structural, float critical) {
        this.structural = Math.max(0.0f, Math.min(maxStructural(), structural));
        this.critical = Math.max(0.0f, Math.min(maxCritical(), critical));
        state = this.critical <= 0.0f ? State.WRECK : this.structural <= 0.0f ? State.DISABLED : State.ACTIVE;
        changed.run();
    }

    // ------------------------------------------------------------------ damage

    /** The player whose machine, armor, body or self did the damage, if any. */
    @Nullable
    public static UUID attackerOwner(DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker == null ? null : FactionService.ownerOf(attacker);
    }

    /**
     * Takes damage (server). Friendly fire of the factions is checked here, as for the armor. Returns whether it hurt: false for
     * a wreck and for damage the faction rules block.
     */
    public boolean hurt(Entity self, DamageSource source, float amount) {
        MinecraftServer server = self.getServer();
        if (server == null || wreck() || amount <= 0.0f) {
            return false;
        }
        UUID attacker = attackerOwner(source);
        if (FactionService.blocksDamage(server, attacker, owner)) {
            return false;
        }
        if (source.getEntity() != null) {
            remember(source.getEntity(), self.level().getGameTime());
            hitByAttacker = true;
        }
        float criticalLoss;
        if (structural <= 0.0f) {
            criticalLoss = amount;
        } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            criticalLoss = amount * RebirthConfig.MACHINE_CRITICAL_EXPLOSION.get().floatValue();
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            criticalLoss = amount * RebirthConfig.MACHINE_CRITICAL_FIRE.get().floatValue();
        } else {
            criticalLoss = 0.0f;
        }
        structural = Math.max(0.0f, structural - amount);
        critical = Math.max(0.0f, critical - criticalLoss);
        secondsSinceDamage = 0;
        if (critical <= 0.0f) {
            state = State.WRECK;
            structural = 0.0f;
        } else if (structural <= 0.0f) {
            state = State.DISABLED;
        }
        changed.run();
        return true;
    }

    /** Mends structural integrity (the repair wrench, the station); a shut-down machine starts again from the config's share. */
    public void repairStructural(float amount) {
        if (wreck()) {
            return;
        }
        structural = Math.min(maxStructural(), structural + amount);
        if (state == State.DISABLED && structural >= RebirthConfig.MACHINE_RESTART_FRACTION.get() * maxStructural()) {
            state = State.ACTIVE;
        }
        changed.run();
    }

    // ------------------------------------------------------------------ self-defence

    /** Remembers an attacker (and the player behind it) for the config's seconds. */
    public void remember(Entity attacker, long gameTime) {
        long until = gameTime + RebirthConfig.SELF_DEFENCE_SECONDS.get() * 20L;
        attackers.put(attacker.getUUID(), until);
        UUID behind = FactionService.ownerOf(attacker);
        if (behind != null) {
            attackers.put(behind, until);
        }
    }

    /** Whether this entity (or the player behind it) attacked the machine or its people a short while ago. */
    public boolean attackedBy(Entity target, long gameTime) {
        attackers.values().removeIf(until -> until < gameTime);
        UUID behind = FactionService.ownerOf(target);
        return attackers.containsKey(target.getUUID()) || behind != null && attackers.containsKey(behind);
    }

    // ------------------------------------------------------------------ the console (block D)

    @Nullable
    public net.minecraft.core.GlobalPos console() {
        return console;
    }

    public void setConsole(@Nullable net.minecraft.core.GlobalPos console) {
        this.console = console;
    }

    public MachineTask task() {
        return task;
    }

    public void setTask(MachineTask task) {
        this.task = task == null ? MachineTask.STAND_DOWN : task;
    }

    /** In a fight: hit by someone within the config's seconds. */
    public boolean fighting() {
        if (secondsSinceDamage >= RebirthConfig.CONSOLE_COMBAT_SECONDS.get()) {
            hitByAttacker = false;
        }
        return hitByAttacker;
    }

    /** Seconds since the last damage (for tests and the console). */
    public int secondsSinceDamage() {
        return secondsSinceDamage;
    }

    // ------------------------------------------------------------------ rights

    /**
     * Whether this player may pack up, repair and mount this machine: its owner, a member of the owner's faction whose role is in
     * [garrison.machines] managerRoles, or a player in creative mode.
     */
    public boolean mayManage(Player player) {
        return mayCommand(owner, player);
    }

    /** Whether this player may command what that player owns (machines, a console): see {@link #mayManage}. */
    public static boolean mayCommand(@Nullable UUID owner, Player player) {
        if (player.isCreative() || owner == null || owner.equals(player.getUUID())) {
            return true;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        FactionData data = FactionData.get(server);
        Optional<Faction> mine = data.of(owner);
        Optional<Faction> theirs = data.of(player.getUUID());
        if (mine.isEmpty() || theirs.isEmpty() || !mine.get().id().equals(theirs.get().id())) {
            return false;
        }
        return mine.get().role(player.getUUID()).canManageMachines();
    }

    // ------------------------------------------------------------------ time

    /**
     * One second (server): structural integrity comes back after a pause without damage; the environment works on the machine;
     * the register learns where it is now and then. {@code upgrades} decide what keeps acid, dust and interference out.
     */
    public void second(Entity self, InstalledUpgrades upgrades) {
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        secondsSinceDamage++;
        float regen = RebirthConfig.MACHINE_REGEN_PER_SECOND.get().floatValue();
        if (state == State.ACTIVE && regen > 0.0f && structural < maxStructural()
                && secondsSinceDamage >= RebirthConfig.MACHINE_REGEN_DELAY_SECONDS.get()) {
            structural = Math.min(maxStructural(), structural + regen);
            changed.run();
        }
        environment(self, level, upgrades);
        ConsoleLink.second(self, this);
        if (--registryCountdown <= 0) {
            registryCountdown = RebirthConfig.MACHINE_REGISTRY_SECONDS.get();
            MachineRegistry.get(level.getServer()).update(self, this);
        }
    }

    /** Acid and dust wear a machine outside, unless an upgrade keeps them out; an EMI storm disturbs it outside a shelter. */
    private void environment(Entity self, ServerLevel level, InstalledUpgrades upgrades) {
        emiFault = false;
        dusty = false;
        if (!Environment.isEnabled() || wreck()) {
            return;
        }
        Exposure exposure = Environment.exposureAt(level, self.blockPosition());
        boolean screened = BodyGadgets.has(level.registryAccess(), upgrades, Emi.SCREEN) || BodyGadgets.has(level.registryAccess(), upgrades, Emi.SHIELDING);
        emiFault = !screened && Emi.disturbs(exposure.get(Hazard.EMI)) && !Emi.insideShelter(level, self.blockPosition());
        if (environmentByBody) {
            return;
        }
        com.projectrebirth.shelter.Contamination.second(self);
        float acid = exposure.get(Hazard.ACID);
        if (acid > 0.0f && !BodyGadgets.protects(level.registryAccess(), upgrades, BodyGadgets.ACID)) {
            setWear(wear + acid * RebirthConfig.MACHINE_ACID_WEAR_PER_SECOND.get().floatValue());
        }
        float dust = exposure.get(Hazard.DUST);
        if (dust > 0.0f && !BodyGadgets.protects(level.registryAccess(), upgrades, BodyGadgets.DUST)) {
            setWear(wear + dust * RebirthConfig.MACHINE_DUST_WEAR_PER_SECOND.get().floatValue());
            dusty = true;
        }
    }

    // ------------------------------------------------------------------ packing and saving

    public Packed pack() {
        return new Packed(kind, structural, critical, wear, Optional.ofNullable(owner), ownerName, name);
    }

    public void unpack(Packed packed) {
        owner = packed.owner().orElse(null);
        ownerName = packed.ownerName();
        name = packed.name();
        wear = packed.wear();
        setIntegrity(packed.structural(), packed.critical());
    }

    public void save(CompoundTag tag) {
        CompoundTag core = new CompoundTag();
        if (owner != null) {
            core.putUUID("owner", owner);
        }
        core.putString("owner_name", ownerName);
        core.putString("name", name);
        core.putFloat("structural", structural);
        core.putFloat("critical", critical);
        core.putFloat("wear", wear);
        core.putInt("state", state.ordinal());
        core.putBoolean("intruders", intruderMode);
        if (console != null) {
            core.putString("console_dimension", console.dimension().location().toString());
            core.putLong("console", console.pos().asLong());
        }
        MachineTask.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, task).result().ifPresent(found -> core.put("task", found));
        tag.put("machine", core);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("machine")) {
            return;
        }
        CompoundTag core = tag.getCompound("machine");
        owner = core.hasUUID("owner") ? core.getUUID("owner") : null;
        ownerName = core.getString("owner_name");
        name = core.getString("name");
        structural = core.getFloat("structural");
        critical = core.getFloat("critical");
        wear = core.getFloat("wear");
        state = State.byId(core.getInt("state"));
        intruderMode = core.getBoolean("intruders");
        console = core.contains("console") ? net.minecraft.core.GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(core.getString("console_dimension"))), net.minecraft.core.BlockPos.of(core.getLong("console"))) : null;
        task = core.contains("task") ? MachineTask.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, core.get("task")).result().orElse(MachineTask.STAND_DOWN)
                : MachineTask.STAND_DOWN;
        changed.run();
    }
}
