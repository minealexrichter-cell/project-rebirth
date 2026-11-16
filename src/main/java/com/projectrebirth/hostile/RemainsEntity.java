package com.projectrebirth.hostile;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * What is left of a destroyed machine or a dead brute (1.6.0, part B3): the same model, lying the way it fell (its "dead"
 * animation), with a battered texture. A patrol robot's and the mech's wreck stays like the wreck of a power armor and is taken
 * apart with the repair wrench held while sneaking, for parts (loot table entities/&lt;kind&gt;_wreck). A brute's body radiates
 * (see {@link Auras}) and rots away after a while (config).
 */
public class RemainsEntity extends Entity implements GeoEntity {
    public enum Kind {
        PATROL_ROBOT("patrol_robot", 1.9f, 0.7f, true),
        MECH("mech", 3.2f, 2.0f, true),
        BRUTE("brute", 2.4f, 0.9f, false);

        public final String id;
        public final float width, height;
        public final boolean machine;

        Kind(String id, float width, float height, boolean machine) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.machine = machine;
        }

        static Kind of(int index) {
            return values()[Math.floorMod(index, values().length)];
        }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(RemainsEntity.class, EntityDataSerializers.INT);
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.5f, 1.0f, 0.3f), 1.0f);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int age;

    public RemainsEntity(EntityType<? extends RemainsEntity> type, Level level) {
        super(type, level);
    }

    /** Leaves the remains of {@code body} where it fell, turned the way it looked. */
    public static RemainsEntity leave(Entity body, Kind kind) {
        RemainsEntity remains = new RemainsEntity(HostileRegistry.REMAINS.get(), body.level());
        remains.entityData.set(KIND, kind.ordinal());
        remains.refreshDimensions();
        remains.moveTo(body.getX(), body.getY(), body.getZ(), body instanceof net.minecraft.world.entity.LivingEntity living ? living.yBodyRot : body.getYRot(), 0.0f);
        body.level().addFreshEntity(remains);
        return remains;
    }

    public Kind kind() {
        return Kind.of(entityData.get(KIND));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        Kind kind = kind();
        return EntityDimensions.scalable(kind.width, kind.height);
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround() || !isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0.0, -0.04, 0.0).multiply(0.9, 0.98, 0.9));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        }
        Kind kind = kind();
        if (level().isClientSide) {
            if (kind == Kind.BRUTE && random.nextInt(4) == 0) {
                level().addParticle(GLOW, getRandomX(1.0), getY() + random.nextDouble() * getBbHeight(), getRandomZ(1.0), 0.0, 0.02, 0.0);
            } else if (kind.machine && random.nextInt(kind == Kind.MECH ? 3 : 8) == 0) {
                level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE,
                        getRandomX(0.6), getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.6), 0.0, 0.03, 0.0);
            }
            return;
        }
        age++;
        if (kind == Kind.BRUTE && age > (RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.CORPSE_LIFETIME.get() : 6000)) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.4, getZ(), 20, 0.8, 0.3, 0.8, 0.01);
            }
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return kind().machine;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** A machine's wreck comes apart under the repair wrench held while sneaking. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !kind().machine) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(PowerArmorRegistry.REPAIR_WRENCH.get())) {
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.remains.wrench"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.remains.wrench"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (level() instanceof ServerLevel server) {
            dismantle(server, player);
            stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Drops its parts (loot table entities/&lt;kind&gt;_wreck) and is gone. Public for tests. */
    public void dismantle(ServerLevel server, Player player) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ProjectRebirth.id("entities/" + kind().id + "_wreck"));
        LootTable table = server.getServer().reloadableRegistries().getLootTable(key);
        LootParams.Builder params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position());
        if (player != null) {
            params.withParameter(LootContextParams.THIS_ENTITY, player);
        }
        for (ItemStack drop : table.getRandomItems(params.create(LootContextParamSets.CHEST))) {
            spawnAtLocation(drop, 0.5f);
        }
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 10, 0.6, 0.3, 0.6, 0.02);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.7f, 1.2f);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt("Kind"));
        age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", entityData.get(KIND));
        tag.putInt("Age", age);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation." + kind().id + ".dead"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Where it lies, for tests. */
    public Vec3 lyingAt() {
        return position();
    }
}
