package com.projectrebirth.technology.energy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The backup battery: charged from the cable like any machine (after the life support), it gives energy only to life
 * support blocks, next to it or on the cable next to it, and only to those the main power does not keep charged
 * ([energy] backupStartShare). Nothing else can take energy out of it.
 */
public class BackupBatteryBlockEntity extends BlockEntity {
    /** Set by the registry (in the shelter package) before any battery is made. */
    static Supplier<BlockEntityType<BackupBatteryBlockEntity>> type;

    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.BACKUP_CAPACITY.get(),
            () -> RebirthConfig.BACKUP_TRANSFER.get(), () -> RebirthConfig.BACKUP_TRANSFER.get(), this::setChanged);
    private final IEnergyStorage input = energy.inputOnly();
    /** It feeds in bursts; it counts as on reserve this long after the last one, so the state does not flicker. */
    private static final int RESERVE_HOLD_TICKS = 100;
    private long lastGave = Long.MIN_VALUE / 2;
    private boolean onReserve;
    private int lastSignal = -1;
    private int supported;

    public BackupBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(type.get(), pos, state);
    }

    public static void setType(Supplier<BlockEntityType<BackupBatteryBlockEntity>> registered) {
        type = registered;
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    /** What other blocks see: it only takes energy in. */
    public IEnergyStorage input() {
        return input;
    }

    /** Whether it fed life support in the last few seconds: the main power does not keep up. */
    public boolean isOnReserve() {
        return onReserve;
    }

    /** Life support blocks it found at the last look. */
    public int supported() {
        return supported;
    }

    public int signal() {
        int capacity = energy.getMaxEnergyStored();
        return capacity <= 0 || energy.getEnergyStored() == 0 ? 0 : 1 + Mth.floor(14.0f * energy.getEnergyStored() / capacity);
    }

    /** The life support it can reach: next to it, or on a cable next to it. */
    private List<IEnergyStorage> lifeSupport() {
        Map<IEnergyStorage, Boolean> found = new IdentityHashMap<>();
        for (Direction side : Direction.values()) {
            BlockPos next = worldPosition.relative(side);
            if (level.getBlockEntity(next) instanceof LifeSupport support) {
                found.put(support.lifeSupportEnergy(), true);
            } else if (level.getBlockState(next).getBlock() instanceof CableBlock) {
                for (IEnergyStorage storage : CableNetwork.at(level, next).lifeSupport(level)) {
                    found.put(storage, true);
                }
            }
        }
        return new ArrayList<>(found.keySet());
    }

    /** One tick; public for tests. */
    public void serverTick() {
        List<IEnergyStorage> targets = lifeSupport();
        supported = targets.size();
        int left = Math.min(RebirthConfig.BACKUP_TRANSFER.get(), energy.getEnergyStored());
        double start = RebirthConfig.BACKUP_START_SHARE.get();
        targets.sort(Comparator.comparingDouble(t -> t.getMaxEnergyStored() <= 0 ? 1.0 : t.getEnergyStored() / (double) t.getMaxEnergyStored()));
        for (IEnergyStorage target : targets) {
            if (left <= 0) {
                break;
            }
            if (LifeSupport.charged(target, start)) {
                continue;
            }
            int accepted = target.receiveEnergy(Math.min(left, target.getMaxEnergyStored() - target.getEnergyStored()), false);
            if (accepted > 0) {
                energy.take(accepted, false);
                left -= accepted;
                lastGave = level.getGameTime();
            }
        }
        boolean reserve = level.getGameTime() - lastGave < RESERVE_HOLD_TICKS;
        if (reserve != onReserve) {
            onReserve = reserve;
            TechSounds.setWorking(this, onReserve);
        }
        int signal = signal();
        if (signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
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
