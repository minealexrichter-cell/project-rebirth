package com.projectrebirth.machinemind;

import javax.annotation.Nullable;

import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A module of the factory of a node (and the base of its computer): its strength, its box, its look. */
public class MindModuleBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mind.idle");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("animation.mind.work");
    /** Ticks without damage before it mends itself. */
    static final long CALM = 600L;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The computer it belongs to; null: none yet. */
    @Nullable
    private BlockPos computer;
    private float strength = -1.0f;
    /** Until when it works (shown). */
    private long workUntil;
    private boolean shownActive;
    private boolean shownWorking;
    long lastExplosion = -1L;
    long lastDamage = -100_000L;

    public MindModuleBlockEntity(BlockPos pos, BlockState state) {
        this(MindRegistry.MODULE_ENTITY.get(), pos, state);
    }

    protected MindModuleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public MindModule module() {
        return getBlockState().getBlock() instanceof MindModuleBlock block ? block.module() : MindModule.LINE;
    }

    /** The look of it, 1 to 3 (only the computer's changes). */
    public int tier() {
        return 1;
    }

    public boolean covers(BlockPos pos) {
        int r = module().reach(tier());
        return Math.abs(pos.getX() - worldPosition.getX()) <= r && Math.abs(pos.getZ() - worldPosition.getZ()) <= r
                && pos.getY() >= worldPosition.getY() && pos.getY() < worldPosition.getY() + module().height(tier());
    }

    @Nullable
    public MindComputerBlockEntity computer() {
        return level != null && computer != null && level.isLoaded(computer) && level.getBlockEntity(computer) instanceof MindComputerBlockEntity entity ? entity : null;
    }

    public void setComputer(BlockPos pos) {
        if (!pos.equals(computer)) {
            computer = pos.immutable();
            setChanged();
        }
    }

    /** Whether it is part of a living node now. */
    public boolean active() {
        MindComputerBlockEntity node = computer();
        return node != null && node.active();
    }

    public float maxStrength() {
        return WastelandConfig.MIND_MODULE_STRENGTH.get().floatValue();
    }

    public float strength() {
        return strength < 0.0f ? maxStrength() : strength;
    }

    /** Hurts it; at the end it breaks and its node is set back. */
    public void damage(ServerLevel server, float amount) {
        strength = Math.max(0.0f, strength() - amount);
        lastDamage = server.getGameTime();
        setChanged();
        hurtEffects(server);
        MindComputerBlockEntity node = computer();
        if (node != null) {
            node.noteDamage(server);
        }
        if (strength <= 0.0f) {
            if (node != null) {
                node.delay(WastelandConfig.MIND_MODULE_DELAY.get() * 1200L);
            }
            MindComputerBlockEntity.burst(server, worldPosition, 1.5);
            MindComputerBlockEntity.dropLoot(server, worldPosition, module().getSerializedName());
            server.removeBlock(worldPosition, false);
        }
    }

    void hurtEffects(ServerLevel server) {
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 8, 0.6, 0.6, 0.6, 0.1);
        server.playSound(null, worldPosition, ModSounds.MIND_HURT.get(), SoundSource.HOSTILE, 0.8f, 0.9f + server.random.nextFloat() * 0.2f);
    }

    /** It is at work for a while: the animation shows it. */
    public void work(ServerLevel server, int ticks) {
        workUntil = server.getGameTime() + ticks;
    }

    public void serverTick(ServerLevel server, BlockPos pos) {
        if (Math.floorMod(server.getGameTime() + pos.asLong(), 20L) != 0L) {
            return;
        }
        if (active()) {
            fillBox(server);
            if (strength() < maxStrength() && server.getGameTime() - lastDamage > CALM) {
                strength = Math.min(maxStrength(), strength() + 1.0f);
                setChanged();
            }
        }
        show(server);
    }

    /** Puts the invisible blocks of its box where nothing of a player stands. */
    protected void fillBox(ServerLevel server) {
        for (BlockPos at : module().box(worldPosition, tier())) {
            if (!at.equals(worldPosition) && server.isLoaded(at) && !server.getBlockState(at).is(MindRegistry.PART.get()) && MindBase.mayReplace(server, at)
                    && MindBase.empty(server, at)) {
                server.setBlock(at, MindRegistry.PART.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    protected void show(ServerLevel server) {
        boolean active = active(), working = active && server.getGameTime() < workUntil;
        if (active != shownActive || working != shownWorking) {
            shownActive = active;
            shownWorking = working;
            sync();
        }
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public boolean shownActive() {
        return shownActive;
    }

    public void clientTick(Level level, BlockPos pos) {
        if (shownWorking && level.random.nextInt(6) == 0) {
            double r = module().reach(tier()) + 0.3;
            level.addParticle(module() == MindModule.FOUNDRY ? ParticleTypes.LAVA : ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * r * 2,
                    pos.getY() + 0.8, pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * r * 2, 0.0, 0.05, 0.0);
        }
        if (shownWorking && module() != MindModule.COMPUTER && level.getGameTime() % 50L == 0L) {
            level.playLocalSound(pos, ModSounds.MIND_FACTORY.get(), SoundSource.BLOCKS, 0.5f, 0.95f + level.random.nextFloat() * 0.1f, false);
        }
    }

    /** Its block goes away. */
    public void removed(ServerLevel server) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (computer != null) {
            tag.putLong("computer", computer.asLong());
        }
        tag.putFloat("strength", strength);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        computer = tag.contains("computer") ? BlockPos.of(tag.getLong("computer")) : null;
        strength = tag.contains("strength") ? tag.getFloat("strength") : -1.0f;
        if (tag.contains("shown_active")) {
            shownActive = tag.getBoolean("shown_active");
            shownWorking = tag.getBoolean("shown_working");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("shown_active", shownActive);
        tag.putBoolean("shown_working", shownWorking);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state ->
                shownActive ? state.setAndContinue(shownWorking ? WORK : IDLE) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
