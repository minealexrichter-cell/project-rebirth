package com.projectrebirth.garrison;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.Parked;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeLayout;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * A machine standing in the world as a creature of its own (1.7.0): the turrets and the mech of later blocks, and the stand-in
 * machine of the automated tests (androids are artificial bodies and carry a {@link MachineCore} themselves). The common part:
 *
 * <ul>
 *   <li>damage through its {@link MachineCore}: shut down at no structural integrity (it does not move), a wreck at no critical;</li>
 *   <li>the repair wrench mends structural integrity with iron; with Shift it packs a working or shut-down machine into its item
 *   (the owner or a manager of the owner's faction) and takes a wreck apart (anyone): the loot table
 *   {@code entities/<kind>_machine_wreck}, the upgrades and a damaged AI core;</li>
 *   <li>the upgrade mounting tool with Shift opens its upgrade slots (armor upgrades);</li>
 *   <li>the register knows it while it stands in the world.</li>
 * </ul>
 */
public abstract class MachineEntity extends PathfinderMob implements GarrisonMachine, UpgradeHost {
    private static final EntityDataAccessor<Float> STRUCTURAL = SynchedEntityData.defineId(MachineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CRITICAL = SynchedEntityData.defineId(MachineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MachineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MachineEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final MachineCore core;
    private InstalledUpgrades upgrades = InstalledUpgrades.EMPTY;
    /** Set while it is being packed up: going from the world then is not a loss. */
    private boolean packing;

    protected MachineEntity(EntityType<? extends MachineEntity> type, Level level, MachineKind kind) {
        super(type, level);
        this.core = new MachineCore(kind, this::syncCore);
        syncCore();
    }

    /** The item this machine packs into. */
    protected abstract Item packedItem();

    @Override
    public MachineCore machineCore() {
        return core;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STRUCTURAL, 0.0f);
        builder.define(CRITICAL, 0.0f);
        builder.define(STATE, 0);
        builder.define(OWNER, Optional.empty());
    }

    private void syncCore() {
        if (core == null) {
            return;
        }
        entityData.set(STRUCTURAL, core.structural());
        entityData.set(CRITICAL, core.critical());
        entityData.set(STATE, core.state().ordinal());
        entityData.set(OWNER, Optional.ofNullable(core.owner()));
    }

    /** On both sides: the bars and the state as the server last said. */
    public float shownStructural() {
        return entityData.get(STRUCTURAL);
    }

    public float shownCritical() {
        return entityData.get(CRITICAL);
    }

    public MachineCore.State shownState() {
        return MachineCore.State.byId(entityData.get(STATE));
    }

    public Optional<UUID> shownOwner() {
        return entityData.get(OWNER);
    }

    // ------------------------------------------------------------------ upgrades

    @Override
    public InstalledUpgrades upgrades() {
        return upgrades;
    }

    @Override
    public void setUpgrades(InstalledUpgrades upgrades) {
        this.upgrades = upgrades;
    }

    @Override
    public UpgradeLayout layout() {
        return new UpgradeLayout.MachineSlots(core.kind().upgradeSlots());
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            Upgrades.takeAll(this).forEach(this::spawnAtLocation);
            discard();
            return true;
        }
        if (isInvulnerableTo(source) || invulnerableTime > 10) {
            return false;
        }
        boolean wasWreck = core.wreck();
        amount *= 1.0f - platingShare();
        if (!core.hurt(this, source, amount)) {
            return false;
        }
        invulnerableTime = 20;
        level().broadcastDamageEvent(this, source);
        playSound(SoundEvents.IRON_GOLEM_HURT, 0.8f, 1.2f);
        if (!wasWreck && core.wreck()) {
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
            if (level() instanceof ServerLevel server) {
                MachineRegistry.get(server.getServer()).update(this, core);
            }
        }
        return true;
    }

    /** The share of damage its armor plating keeps off (1.7.0, block G: by the plates' metal, config). */
    public float platingShare() {
        for (com.projectrebirth.upgrades.InstalledUpgrades.Installed entry : upgrades.entries()) {
            var definition = Upgrades.definition(registryAccess(), entry.stack());
            if (definition.isPresent() && definition.get().kind().equals("armor_plating")) {
                return switch (definition.get().material().orElse("")) {
                    case "copper" -> RebirthConfig.MACHINE_PLATING_COPPER.get().floatValue();
                    case "titanium" -> RebirthConfig.MACHINE_PLATING_TITANIUM.get().floatValue();
                    default -> RebirthConfig.MACHINE_PLATING_IRON.get().floatValue();
                };
            }
        }
        return 0.0f;
    }

    @Override
    public boolean isDeadOrDying() {
        // a machine never dies like a creature: it shuts down and becomes a wreck
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0) {
            core.second(this, upgrades);
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (!core.active()) {
            Parked.keepOnlyFalling(this);
            super.travel(Vec3.ZERO);
        } else {
            super.travel(input);
        }
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || !core.active();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) {
            MachineRegistry.get(server.getServer()).remove(getUUID());
            if (!packing) {
                ConsoleLink.lost(this, core);
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ hands

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level().isClientSide) {
            return stack.is(PowerArmorRegistry.REPAIR_WRENCH.get()) || stack.is(PowerArmorRegistry.UPGRADE_MOUNTING_TOOL.get())
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (stack.is(PowerArmorRegistry.REPAIR_WRENCH.get())) {
            useWrench(player, stack);
            return InteractionResult.CONSUME;
        }
        if (stack.is(PowerArmorRegistry.UPGRADE_MOUNTING_TOOL.get())) {
            if (!player.isShiftKeyDown()) {
                say(player, "mounting_hint");
            } else if (core.wreck()) {
                say(player, "wreck");
            } else if (!core.mayManage(player)) {
                say(player, "not_yours");
            } else if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, who) -> new MachineMountingMenu(id, inventory, this),
                        Component.translatable("screen.projectrebirth.machine_mounting.title")), buf -> buf.writeVarInt(getId()));
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    protected static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.projectrebirth.machine." + key, args), true);
    }

    /** The repair wrench: mend with iron; with Shift pack up a machine or take a wreck apart. Public for tests. */
    public void useWrench(Player player, ItemStack wrench) {
        if (player.isShiftKeyDown()) {
            if (core.wreck()) {
                dismantle(player);
            } else if (!core.mayManage(player)) {
                say(player, "not_yours");
            } else {
                packUp(player);
            }
            return;
        }
        if (core.wreck()) {
            say(player, "wreck");
            return;
        }
        if (!core.mayManage(player)) {
            say(player, "not_yours");
            return;
        }
        if (core.structural() >= core.maxStructural()) {
            say(player, core.critical() < core.maxCritical() ? "critical_needs_station" : "nothing_to_repair");
            return;
        }
        if (!takeOne(player, Items.IRON_INGOT)) {
            say(player, "need_iron");
            return;
        }
        core.repairStructural(RebirthConfig.MACHINE_REPAIR_PER_INGOT.get().floatValue());
        wrench.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
        say(player, "repaired", Mth.ceil(core.structural()), Mth.ceil(core.maxStructural()), Mth.ceil(core.critical()), Mth.ceil(core.maxCritical()));
    }

    private static boolean takeOne(Player player, Item item) {
        if (player.isCreative()) {
            return true;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                inventory.getItem(slot).shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Packs the machine into its item with its integrity, wear, owner, name and upgrades. */
    public ItemStack packUp(Player player) {
        if (!isAlive()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(packedItem());
        stack.set(GarrisonRegistry.PACKED_MACHINE.get(), core.pack());
        Upgrades.putOnStack(stack, upgrades);
        upgrades = InstalledUpgrades.EMPTY;
        ConsoleLink.packed(this, core);
        packing = true;
        discard();
        ItemStack given = stack.copy();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        say(player, "packed");
        return given;
    }

    /**
     * Takes a wreck apart: its parts (loot table {@code entities/<kind>_machine_wreck}), its upgrades and a damaged AI core. Public
     * for tests.
     */
    public void dismantle(Player player) {
        if (!isAlive() || !(level() instanceof ServerLevel server)) {
            return;
        }
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ProjectRebirth.id("entities/" + core.kind().id() + "_machine_wreck"));
        LootTable table = server.getServer().reloadableRegistries().getLootTable(key);
        LootParams.Builder params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position());
        if (player != null) {
            params.withParameter(LootContextParams.THIS_ENTITY, player);
        }
        List<ItemStack> drops = new java.util.ArrayList<>(table.getRandomItems(params.create(LootContextParamSets.CHEST)));
        drops.addAll(Upgrades.takeAll(this));
        drops.add(new ItemStack(GarrisonRegistry.DAMAGED_AI_CORE.get()));
        drops.forEach(drop -> spawnAtLocation(drop, 0.5f));
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.7f, 1.2f);
        if (player != null) {
            say(player, "dismantled");
        }
        discard();
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        core.save(tag);
        Upgrades.save(tag, registryAccess(), upgrades);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        core.load(tag);
        upgrades = Upgrades.load(tag, registryAccess());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(packedItem());
    }

    @Override
    protected void pushEntities() {
        if (core.active()) {
            super.pushEntities();
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (core.active() || Parked.allowsMove(type)) {
            super.move(type, movement);
        }
    }
}
