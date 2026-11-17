package com.projectrebirth.bodies;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * All server-side consciousness transfer logic: entering a body from a capsule, returning to the human body,
 * and what happens when the controlled body is destroyed.
 * <p>
 * Model: the player entity is the consciousness. Entering a body removes the body entity and gives the player
 * its class stats, health and inventory; leaving it spawns the body entity again with the player's current
 * health and inventory. Every switch removes data from its old place before putting it into the new one,
 * so nothing can be duplicated.
 */
public final class TransferService {
    private static final ResourceLocation HEALTH_MODIFIER = ProjectRebirth.id("body_max_health");
    private static final ResourceLocation ARMOR_MODIFIER = ProjectRebirth.id("body_armor");
    private static final ResourceLocation SPEED_MODIFIER = ProjectRebirth.id("body_speed");
    private static final ResourceLocation ATTACK_MODIFIER = ProjectRebirth.id("body_attack");
    private static final ResourceLocation PLATING_ARMOR_MODIFIER = ProjectRebirth.id("body_plating_armor");
    private static final ResourceLocation PLATING_TOUGHNESS_MODIFIER = ProjectRebirth.id("body_plating_toughness");

    /** Game time of the last transfer per player. Not saved: a restart simply clears the cooldown. */
    private static final Map<UUID, Long> LAST_TRANSFER = new HashMap<>();

    private TransferService() {
    }

    public static PossessionState state(Player player) {
        return player.getData(BodyAttachments.POSSESSION);
    }

    public static boolean isInArtificialBody(Player player) {
        return !state(player).isHuman();
    }

    // ---------------------------------------------------------------- capsule -> body

    /** Transfer into the nearest usable body around the capsule (admin command). */
    public static TransferResult transferFromCapsule(ServerPlayer player, BlockPos capsuleLower, Direction capsuleFacing) {
        return transferFromCapsule(player, capsuleLower, capsuleFacing, null);
    }

    /**
     * Transfer into the body chosen in the capsule window, or the nearest one if {@code chosenBody} is null.
     * Everything is checked again here: the window only shows what the server allowed when it was opened.
     */
    public static TransferResult transferFromCapsule(ServerPlayer player, BlockPos capsuleLower, Direction capsuleFacing,
                                                     @Nullable UUID chosenBody) {
        ServerLevel level = player.serverLevel();
        TransferResult problem = capsuleProblem(player, capsuleLower);
        if (problem != null) {
            return problem;
        }
        long now = level.getGameTime();
        Optional<ArtificialBodyEntity> target;
        if (chosenBody == null) {
            target = findBodyNear(player, level, Vec3.atBottomCenterOf(capsuleLower));
            if (target.isEmpty()) {
                return TransferResult.NO_BODY;
            }
        } else {
            Choice choice = resolveChoice(player, level, Vec3.atBottomCenterOf(capsuleLower), chosenBody);
            if (choice.problem() != null) {
                return choice.problem();
            }
            target = Optional.of(choice.body());
        }

        // 1. The human body: inventory and health go to the registry, a visible shell stays in the capsule.
        ShellRegistry registry = ShellRegistry.get(player.server);
        Vec3 shellPos = new Vec3(capsuleLower.getX() + 0.5, capsuleLower.getY() + 2.0 / 16.0, capsuleLower.getZ() + 0.5);
        float shellYaw = capsuleFacing.toYRot();
        ShellRegistry.ShellRecord record = new ShellRegistry.ShellRecord(UUID.randomUUID(), level.dimension(), shellPos, shellYaw,
                player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
                InventoryUtil.takeInventory(player));
        registry.put(player.getUUID(), record);
        HumanShellEntity shell = BodyEntities.HUMAN_SHELL.get().create(level);
        if (shell != null) {
            // The entity UUID equals the record's shell id, so the entity can be found directly on return.
            shell.setUUID(record.shellId());
            shell.setup(player.getUUID(), record.shellId(), player.getName());
            shell.moveTo(shellPos.x, shellPos.y, shellPos.z, shellYaw, 0.0f);
            shell.setYHeadRot(shellYaw);
            shell.setYBodyRot(shellYaw);
            level.addFreshEntity(shell);
            shell.setHealth(record.health());
        }

        // 2. The consciousness moves into the artificial body.
        if (RebirthConfig.CAPSULE_CHARGES.get()) {
            target.get().setCharge(BodyPower.capacity(target.get()));
        }
        enterBody(player, target.get());
        LAST_TRANSFER.put(player.getUUID(), now);
        level.playSound(null, capsuleLower, ModSounds.TRANSFER_ENTER.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        return TransferResult.SUCCESS;
    }

    /** Why this player cannot use the capsule at all right now, or null if they can. */
    @Nullable
    public static TransferResult capsuleProblem(ServerPlayer player, BlockPos capsuleLower) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (isInArtificialBody(player)) {
            return TransferResult.ALREADY_IN_BODY;
        }
        if (player.isPassenger() || player.isSleeping() || player.isDeadOrDying()) {
            return TransferResult.NOT_POSSIBLE_NOW;
        }
        ShellRegistry registry = ShellRegistry.get(player.server);
        if (registry.get(player.getUUID()).isPresent()) {
            // A human player must never have a stored human body; refuse instead of overwriting its inventory.
            ProjectRebirth.LOGGER.warn("Player {} is human but still has a stored human body; transfer refused", player.getScoreboardName());
            return TransferResult.NOT_POSSIBLE_NOW;
        }
        Long last = LAST_TRANSFER.get(player.getUUID());
        if (last != null && now - last < RebirthConfig.TRANSFER_COOLDOWN_SECONDS.get() * 20L) {
            return TransferResult.COOLDOWN;
        }
        AABB capsuleSpace = new AABB(capsuleLower).expandTowards(0, 1, 0);
        if (!level.getEntitiesOfClass(HumanShellEntity.class, capsuleSpace, HumanShellEntity::isAlive).isEmpty()) {
            return TransferResult.CAPSULE_OCCUPIED;
        }
        return null;
    }

    private record Choice(@Nullable ArtificialBodyEntity body, @Nullable TransferResult problem) {
    }

    /** Checks a body chosen in the capsule window against the current state of the world and the transfer rules. */
    private static Choice resolveChoice(ServerPlayer player, ServerLevel level, Vec3 capsuleCenter, UUID bodyId) {
        BodyRegistry registry = BodyRegistry.get(player.server);
        Optional<BodyRegistry.BodyRecord> record = registry.find(player.getUUID(), bodyId);
        if (record.isEmpty()) {
            return new Choice(null, TransferResult.BODY_UNAVAILABLE);
        }
        switch (record.get().state()) {
            case DESTROYED -> {
                return new Choice(null, TransferResult.BODY_DESTROYED);
            }
            case CONTROLLED -> {
                return new Choice(null, TransferResult.BODY_OCCUPIED);
            }
            default -> {
            }
        }
        if (!record.get().dimension().equals(level.dimension())) {
            return new Choice(null, TransferResult.OTHER_DIMENSION);
        }
        if (!withinRadius(record.get().position(), capsuleCenter)) {
            return new Choice(null, TransferResult.TOO_FAR);
        }
        if (level.getEntity(bodyId) instanceof ArtificialBodyEntity body && body.isAlive()) {
            if (!body.canBeUsedBy(player)) {
                return new Choice(null, TransferResult.BODY_UNAVAILABLE);
            }
            return withinRadius(body.position(), capsuleCenter) ? new Choice(body, null) : new Choice(null, TransferResult.TOO_FAR);
        }
        if (level.areEntitiesLoaded(new ChunkPos(BlockPos.containing(record.get().position())).toLong())) {
            // Its chunk is loaded but the body is not there: it is gone (for example removed by a command).
            registry.markDestroyed(player.getUUID(), bodyId, level.dimension(), record.get().position());
            return new Choice(null, TransferResult.BODY_DESTROYED);
        }
        return new Choice(null, TransferResult.LOADING);
    }

    public static boolean withinRadius(Vec3 bodyPosition, Vec3 capsuleCenter) {
        int radius = RebirthConfig.TRANSFER_RADIUS.get();
        return bodyPosition.distanceToSqr(capsuleCenter) <= (double) radius * radius;
    }

    private static Optional<ArtificialBodyEntity> findBodyNear(ServerPlayer player, ServerLevel level, Vec3 center) {
        int radius = RebirthConfig.TRANSFER_RADIUS.get();
        return level.getEntitiesOfClass(ArtificialBodyEntity.class, new AABB(center, center).inflate(radius),
                        body -> body.isAlive() && body.hostsConsciousness() && body.canBeUsedBy(player) && body.distanceToSqr(center) <= (double) radius * radius)
                .stream()
                .min(Comparator.comparingDouble(body -> body.distanceToSqr(center)));
    }

    /** Puts the player's consciousness into the given body. The player's current inventory must already be stored or dropped. */
    private static void enterBody(ServerPlayer player, ArtificialBodyEntity body) {
        ServerLevel fromLevel = player.serverLevel();
        Vec3 from = player.position();
        BodyClass bodyClass = body.getBodyClass();
        ServerLevel level = (ServerLevel) body.level();
        float health = body.getHealth();
        Vec3 pos = body.position();
        float yaw = body.getYRot();
        ListTag inventory = body.getStoredInventory();
        InstalledUpgrades upgrades = body.upgrades();
        float charge = body.charge();
        float heat = body.heat();
        float wear = body.wear();
        BodyRegistry.get(player.server).markControlled(player.getUUID(), body);
        body.setStoredInventory(new ListTag());
        body.setUpgrades(InstalledUpgrades.EMPTY);
        body.discard();
        leaveVehicle(player);

        player.getInventory().clearContent();
        InventoryUtil.giveInventory(player, inventory);
        resetCondition(player);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(bodyClass));
        // A body from 1.0.0 may still be wearing armor.
        BodyInventory.clearArmor(player);
        player.setData(UpgradeRegistry.CONTROLLED_BODY, upgrades);
        player.setData(UpgradeRegistry.JETPACK_USED, 0.0f);
        player.setData(BodyPowerRegistry.CONTROLLED_CHARGE, charge);
        player.setData(BodyPowerRegistry.CONTROLLED_HEAT, heat);
        player.setData(BodyPowerRegistry.CONTROLLED_WEAR, wear);
        applyBodyModifiers(player, bodyClass);
        player.teleportTo(level, pos.x, pos.y, pos.z, yaw, player.getXRot());
        level.playSound(null, player.blockPosition(), ModSounds.TRANSFER_ENTER.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.setHealth(Math.min(health, player.getMaxHealth()));
        TransferAnimationPayload.sendArrived(player, fromLevel == level, from);
    }

    // ---------------------------------------------------------------- body -> human (voluntary)

    public static ReturnResult returnToHumanBody(ServerPlayer player) {
        Optional<BodyClass> bodyClass = state(player).body();
        if (bodyClass.isEmpty()) {
            return ReturnResult.NOT_IN_BODY;
        }
        Optional<ShellRegistry.ShellRecord> record = ShellRegistry.get(player.server).get(player.getUUID());
        if (record.isEmpty() || player.server.getLevel(record.get().dimension()) == null) {
            return ReturnResult.HUMAN_BODY_LOST;
        }
        if (player.isPassenger()) {
            // The body would be left inside a vehicle (e.g. power armor); get out first.
            return ReturnResult.IN_VEHICLE;
        }

        // Leave the artificial body standing where it is, with its current inventory and integrity.
        ServerLevel level = player.serverLevel();
        ArtificialBodyEntity body = BodyEntities.type(bodyClass.get()).create(level);
        if (body == null) {
            return ReturnResult.HUMAN_BODY_LOST;
        }
        // The body keeps its id, so it stays the same entry in the capsule window.
        BodyRegistry registry = BodyRegistry.get(player.server);
        registry.controlled(player.getUUID()).ifPresent(controlled -> {
            registry.remove(player.getUUID(), controlled.id());
            if (level.getEntity(controlled.id()) == null) {
                body.setUUID(controlled.id());
            }
        });
        float bodyHealth = player.getHealth();
        body.setOwner(player.getUUID());
        body.setStoredInventory(InventoryUtil.takeInventory(player));
        body.setUpgrades(player.getData(UpgradeRegistry.CONTROLLED_BODY));
        body.setCharge(BodyPower.charge(player));
        body.setHeat(com.projectrebirth.bodies.power.BodyHeat.heat(player));
        body.setWear(com.projectrebirth.bodies.power.BodyWear.wear(player));
        player.setData(BodyPowerRegistry.CONTROLLED_HEAT, 0.0f);
        player.setData(BodyPowerRegistry.CONTROLLED_WEAR, 0.0f);
        player.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY);
        player.setData(BodyPowerRegistry.CONTROLLED_CHARGE, -1.0f);
        body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        body.setYHeadRot(player.getYRot());
        body.setYBodyRot(player.getYRot());
        level.addFreshEntity(body);
        body.setHealth(bodyHealth);

        restoreHumanBody(player, record.get());
        LAST_TRANSFER.put(player.getUUID(), level.getGameTime());
        return ReturnResult.SUCCESS;
    }

    private static void restoreHumanBody(ServerPlayer player, ShellRegistry.ShellRecord record) {
        ServerLevel fromLevel = player.serverLevel();
        Vec3 from = player.position();
        leaveVehicle(player);
        ShellRegistry.get(player.server).remove(player.getUUID());
        ServerLevel level = player.server.getLevel(record.dimension());
        float health = record.health();
        if (level.getEntity(record.shellId()) instanceof HumanShellEntity shell) {
            if (shell.isAlive()) {
                health = shell.getHealth();
            }
            shell.discard();
        }
        player.getInventory().clearContent();
        InventoryUtil.giveInventory(player, record.inventory());
        resetCondition(player);
        removeBodyModifiers(player);
        player.setData(BodyAttachments.POSSESSION, PossessionState.HUMAN);
        player.getFoodData().setFoodLevel(record.foodLevel());
        player.getFoodData().setSaturation(record.saturation());
        player.teleportTo(level, record.position().x, record.position().y, record.position().z, record.yaw(), 0.0f);
        level.playSound(null, player.blockPosition(), ModSounds.TRANSFER_RETURN.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.setHealth(Math.max(1.0f, Math.min(health, player.getMaxHealth())));
        TransferAnimationPayload.sendArrived(player, fromLevel == level, from);
    }

    // ---------------------------------------------------------------- controlled body destroyed

    /**
     * Called when a player controlling an artificial body dies. The body is destroyed and its inventory dropped.
     * Consciousness returns to the human body if it is alive, otherwise to a backup body.
     *
     * @return true if the player survived (the death event must be cancelled), false for a normal death
     */
    public static boolean handleControlledBodyDeath(ServerPlayer player) {
        if (!isInArtificialBody(player)) {
            return false;
        }
        ServerLevel deathLevel = player.serverLevel();
        InventoryUtil.flushTransientItems(player);
        player.getInventory().dropAll();
        for (InstalledUpgrades.Installed upgrade : player.getData(UpgradeRegistry.CONTROLLED_BODY).entries()) {
            player.drop(upgrade.stack().copy(), true, false);
        }
        player.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY);
        player.setData(BodyPowerRegistry.CONTROLLED_CHARGE, -1.0f);
        player.setData(BodyPowerRegistry.CONTROLLED_HEAT, 0.0f);
        player.setData(BodyPowerRegistry.CONTROLLED_WEAR, 0.0f);
        deathLevel.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
        boolean robot = state(player).body().map(bodyClass -> !bodyClass.synthetic()).orElse(false);
        deathLevel.playSound(null, player.blockPosition(), robot ? ModSounds.ROBOT_DESTROYED.get() : SoundEvents.PLAYER_DEATH,
                SoundSource.PLAYERS, 1.0f, 1.0f);

        BodyRegistry bodies = BodyRegistry.get(player.server);
        bodies.controlled(player.getUUID()).ifPresent(controlled ->
                bodies.markDestroyed(player.getUUID(), controlled.id(), deathLevel.dimension(), player.position()));

        Optional<ShellRegistry.ShellRecord> record = ShellRegistry.get(player.server).get(player.getUUID());
        if (record.isPresent() && player.server.getLevel(record.get().dimension()) != null) {
            restoreHumanBody(player, record.get());
            afterEmergencyReturn(player, Component.translatable("message.projectrebirth.transfer.body_destroyed_human"));
            return true;
        }
        if (RebirthConfig.BACKUP_BODIES_ENABLED.get()) {
            Optional<ArtificialBodyEntity> backup = findBackupBody(player);
            if (backup.isPresent()) {
                player.getInventory().clearContent();
                enterBody(player, backup.get());
                afterEmergencyReturn(player, Component.translatable("message.projectrebirth.transfer.body_destroyed_backup",
                        Component.translatable(backup.get().getBodyClass().translationKey())));
                return true;
            }
        }
        // Nowhere to go: an ordinary Minecraft death. The robot's items are already on the ground.
        removeBodyModifiers(player);
        player.setData(BodyAttachments.POSSESSION, PossessionState.HUMAN);
        player.sendSystemMessage(Component.translatable("message.projectrebirth.transfer.no_body_left"));
        return false;
    }

    /** Nearest loaded, unoccupied body of this player: same dimension first, then any other. */
    private static Optional<ArtificialBodyEntity> findBackupBody(ServerPlayer player) {
        ArtificialBodyEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerLevel level : player.server.getAllLevels()) {
            List<? extends ArtificialBodyEntity> bodies = level.getEntities(EntityTypeTest.forClass(ArtificialBodyEntity.class),
                    body -> body.isAlive() && player.getUUID().equals(body.getOwner()));
            for (ArtificialBodyEntity body : bodies) {
                double distance = level == player.level() ? body.distanceToSqr(player) : Double.MAX_VALUE / 2;
                if (distance < bestDistance) {
                    best = body;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static void afterEmergencyReturn(ServerPlayer player, Component message) {
        int seconds = RebirthConfig.EMERGENCY_RETURN_INVULNERABILITY_SECONDS.get();
        if (seconds > 0) {
            BodyRules.addOwnEffect(player, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, seconds * 20, 4, false, false, true));
        }
        player.sendSystemMessage(message);
    }

    /** Gets the player out of any vehicle before teleporting; a shut-down power armor is told to let them out. */
    private static void leaveVehicle(ServerPlayer player) {
        if (player.getVehicle() instanceof PowerArmorEntity armor) {
            armor.releasePilot();
        }
        player.stopRiding();
    }

    /** Each body has its own physical condition: effects, fire and falling do not travel with the consciousness. */
    private static void resetCondition(ServerPlayer player) {
        player.removeAllEffects();
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.fallDistance = 0.0f;
        player.setAirSupply(player.getMaxAirSupply());
    }

    // ---------------------------------------------------------------- attributes

    public static void applyBodyModifiers(ServerPlayer player, BodyClass bodyClass) {
        removeBodyModifiers(player);
        RebirthConfig.BodyStats stats = RebirthConfig.bodyStats(bodyClass);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        addModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER, stats.maxHealth().get() - maxHealth.getBaseValue(),
                AttributeModifier.Operation.ADD_VALUE);
        addModifier(player, Attributes.ARMOR, ARMOR_MODIFIER, stats.armor().get(), AttributeModifier.Operation.ADD_VALUE);
        addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, stats.speedMultiplier().get() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, stats.attackDamageBonus().get(), AttributeModifier.Operation.ADD_VALUE);
        InstalledUpgrades upgrades = player.getData(UpgradeRegistry.CONTROLLED_BODY);
        addModifier(player, Attributes.ARMOR, PLATING_ARMOR_MODIFIER, BodyGadgets.armorBonus(player.registryAccess(), upgrades),
                AttributeModifier.Operation.ADD_VALUE);
        addModifier(player, Attributes.ARMOR_TOUGHNESS, PLATING_TOUGHNESS_MODIFIER, BodyGadgets.toughnessBonus(player.registryAccess(), upgrades),
                AttributeModifier.Operation.ADD_VALUE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        BodyPower.applyLimits(player);
        com.projectrebirth.bodies.power.BodyHeat.applyLimits(player);
        com.projectrebirth.bodies.power.BodyWear.applyLimits(player);
    }

    public static void removeBodyModifiers(Player player) {
        BodyPower.removeLimits(player);
        com.projectrebirth.bodies.power.BodyHeat.removeLimits(player);
        com.projectrebirth.bodies.power.BodyWear.removeLimits(player);
        removeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER);
        removeModifier(player, Attributes.ARMOR, ARMOR_MODIFIER);
        removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER);
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER);
        removeModifier(player, Attributes.ARMOR, PLATING_ARMOR_MODIFIER);
        removeModifier(player, Attributes.ARMOR_TOUGHNESS, PLATING_TOUGHNESS_MODIFIER);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void addModifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double value,
                                    AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addPermanentModifier(new AttributeModifier(id, value, operation));
        }
    }

    private static void removeModifier(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    // ---------------------------------------------------------------- results

    public enum TransferResult {
        SUCCESS("success"),
        ALREADY_IN_BODY("already_in_body"),
        NOT_POSSIBLE_NOW("not_possible_now"),
        COOLDOWN("cooldown"),
        CAPSULE_OCCUPIED("capsule_occupied"),
        NO_BODY("no_body"),
        BODY_UNAVAILABLE("body_unavailable"),
        BODY_DESTROYED("body_destroyed"),
        BODY_OCCUPIED("body_occupied"),
        OTHER_DIMENSION("other_dimension"),
        TOO_FAR("too_far"),
        /** The chosen body is in an unloaded chunk; the capsule loads it and tries again. */
        LOADING("loading");

        private final String key;

        TransferResult(String key) {
            this.key = key;
        }

        public Component message() {
            return this == NO_BODY || this == TOO_FAR
                    ? Component.translatable("message.projectrebirth.transfer." + key, RebirthConfig.TRANSFER_RADIUS.get())
                    : Component.translatable("message.projectrebirth.transfer." + key);
        }
    }

    public enum ReturnResult {
        SUCCESS("return_success"),
        NOT_IN_BODY("not_in_body"),
        IN_VEHICLE("in_vehicle"),
        HUMAN_BODY_LOST("human_body_lost");

        private final String key;

        ReturnResult(String key) {
            this.key = key;
        }

        public Component message() {
            return Component.translatable("message.projectrebirth.transfer." + key);
        }
    }
}
