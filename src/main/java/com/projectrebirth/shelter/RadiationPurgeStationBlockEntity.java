package com.projectrebirth.shelter;

import java.util.List;
import java.util.Locale;

import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The radiation purge station: a platform. A person standing on it loses accumulated radiation dose slowly while it works.
 * Unlike the decontamination chamber it works on the dose inside the body, not on what is outside it; unlike antiradin it is
 * slow but used again and again. It works only inside a sealed room with clean air, and not on artificial bodies (they have
 * no dose). It needs a lot of energy.
 */
public class RadiationPurgeStationBlockEntity extends BlockEntity {
    public enum Reason {
        WORKING, NOBODY, NO_DOSE, NOT_SEALED, DIRTY_AIR, NO_ENERGY, ARTIFICIAL_BODY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.PURGE_CAPACITY.get(),
            () -> RebirthConfig.PURGE_RECEIVE.get(), () -> 0, this::setChanged);
    private Reason last = Reason.NOBODY;

    public RadiationPurgeStationBlockEntity(BlockPos pos, BlockState state) {
        super(ShelterRegistry.RADIATION_PURGE_STATION_ENTITY.get(), pos, state);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public Reason lastReason() {
        return last;
    }

    private List<ServerPlayer> standing(ServerLevel level) {
        AABB top = new AABB(worldPosition.above()).expandTowards(0, 1, 0).deflate(0.05, 0, 0.05);
        return level.getEntitiesOfClass(ServerPlayer.class, top, player -> player.isAlive() && !player.isSpectator());
    }

    /** What would happen to this person now. Public for tests. */
    public Reason reasonFor(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (TransferService.isInArtificialBody(player)) {
            return Reason.ARTIFICIAL_BODY;
        }
        if (HumanBody.biology(player).dose() <= 0.0f && HumanBody.biology(player).residual() <= 0.0f) {
            return Reason.NO_DOSE;
        }
        Room room = Shelters.sealedRoomAt(level, worldPosition.above());
        if (room == null) {
            return Reason.NOT_SEALED;
        }
        if (room.pollution() > RebirthConfig.PURGE_MAX_POLLUTION.get()) {
            return Reason.DIRTY_AIR;
        }
        if (energy.getEnergyStored() < energyPerTick()) {
            return Reason.NO_ENERGY;
        }
        return Reason.WORKING;
    }

    private static int energyPerTick() {
        return RebirthConfig.STATIONS_NEED_ENERGY.get() ? RebirthConfig.PURGE_ENERGY.get() : 0;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Reason now = Reason.NOBODY;
        boolean used = false;
        for (ServerPlayer player : standing(server)) {
            Reason reason = reasonFor(player);
            if (reason == Reason.WORKING) {
                if (!used) {
                    energy.consume(energyPerTick());
                    used = true;
                }
                Biology old = HumanBody.biology(player);
                float rate = RebirthConfig.PURGE_DOSE_PER_SECOND.get().floatValue() / 20.0f;
                // 1.9.0: the accumulated dose goes too, more slowly
                HumanBody.setBiology(player, old.withDose(old.dose() - rate)
                        .withResidual(old.residual() - rate * com.projectrebirth.config.WastelandConfig.DOSE_PURGE_RESIDUAL_SHARE.get().floatValue()));
                if (server.getGameTime() % 10 == 0) {
                    server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.3, 0.6, 0.3, 0.01);
                }
            }
            if (now == Reason.NOBODY || reason == Reason.WORKING) {
                now = reason;
            }
        }
        last = now;
        TechSounds.setWorking(this, used);
    }

    /** The line shown on right-click, about the person who clicked. */
    public Component status(ServerPlayer player) {
        Reason reason = reasonFor(player);
        return Component.translatable("message.projectrebirth.purge.status",
                String.format(Locale.ROOT, "%.1f", HumanBody.biology(player).dose()),
                String.format(Locale.ROOT, "%.1f", RebirthConfig.PURGE_DOSE_PER_SECOND.get()),
                EnergyUtil.format(energy.getEnergyStored()),
                Component.translatable("message.projectrebirth.purge.reason." + reason.id()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.saved());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag.getInt("energy"));
    }
}
