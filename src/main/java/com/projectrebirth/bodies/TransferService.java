package com.projectrebirth.bodies;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;

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

    public static TransferResult transferFromCapsule(ServerPlayer player, BlockPos capsuleLower, Direction capsuleFacing) {
        ServerLevel level = player.serverLevel();
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
        long now = level.getGameTime();
        Long last = LAST_TRANSFER.get(player.getUUID());
        if (last != null && now - last < RebirthConfig.TRANSFER_COOLDOWN_SECONDS.get() * 20L) {
            return TransferResult.COOLDOWN;
        }
        AABB capsuleSpace = new AABB(capsuleLower).expandTowards(0, 1, 0);
        if (!level.getEntitiesOfClass(HumanShellEntity.class, capsuleSpace, HumanShellEntity::isAlive).isEmpty()) {
            return TransferResult.CAPSULE_OCCUPIED;
        }
        Optional<ArtificialBodyEntity> target = findBodyNear(player, level, Vec3.atBottomCenterOf(capsuleLower));
        if (target.isEmpty()) {
            return TransferResult.NO_BODY;
        }

        // 1. The human body: inventory and health go to the registry, a visible shell stays in the capsule.
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
        enterBody(player, target.get());
        LAST_TRANSFER.put(player.getUUID(), now);
        level.playSound(null, capsuleLower, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
        return TransferResult.SUCCESS;
    }

    private static Optional<ArtificialBodyEntity> findBodyNear(ServerPlayer player, ServerLevel level, Vec3 center) {
        int radius = RebirthConfig.TRANSFER_RADIUS.get();
        return level.getEntitiesOfClass(ArtificialBodyEntity.class, new AABB(center, center).inflate(radius),
                        body -> body.isAlive() && body.canBeUsedBy(player) && body.distanceToSqr(center) <= (double) radius * radius)
                .stream()
                .min(Comparator.comparingDouble(body -> body.distanceToSqr(center)));
    }

    /** Puts the player's consciousness into the given body. The player's current inventory must already be stored or dropped. */
    private static void enterBody(ServerPlayer player, ArtificialBodyEntity body) {
        BodyClass bodyClass = body.getBodyClass();
        ServerLevel level = (ServerLevel) body.level();
        float health = body.getHealth();
        Vec3 pos = body.position();
        float yaw = body.getYRot();
        ListTag inventory = body.getStoredInventory();
        body.setStoredInventory(new ListTag());
        body.discard();
        leaveVehicle(player);

        player.getInventory().clearContent();
        InventoryUtil.giveInventory(player, inventory);
        resetCondition(player);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(bodyClass));
        applyBodyModifiers(player, bodyClass);
        player.teleportTo(level, pos.x, pos.y, pos.z, yaw, player.getXRot());
        player.setHealth(Math.min(health, player.getMaxHealth()));
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
        float bodyHealth = player.getHealth();
        body.setOwner(player.getUUID());
        body.setStoredInventory(InventoryUtil.takeInventory(player));
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
        player.setHealth(Math.max(1.0f, Math.min(health, player.getMaxHealth())));
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
        deathLevel.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
        deathLevel.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.PLAYERS, 1.0f, 1.2f);

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
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void removeBodyModifiers(Player player) {
        removeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER);
        removeModifier(player, Attributes.ARMOR, ARMOR_MODIFIER);
        removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER);
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER);
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
        NO_BODY("no_body");

        private final String key;

        TransferResult(String key) {
            this.key = key;
        }

        public Component message() {
            return this == NO_BODY
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
