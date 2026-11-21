package com.projectrebirth.technology.energy;

import java.util.function.IntSupplier;

import com.projectrebirth.technology.TechMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The window of an energy block without slots (1.8.0 block B): solar panel, radioisotope generator, battery, backup battery.
 * Before 1.8.0 these only wrote their state into the action bar. The menu carries only numbers: the kind, the state, the
 * stock and capacity, what it makes a tick, and one more number (the life-support blocks a backup battery feeds).
 */
public class EnergyPanelMenu extends AbstractContainerMenu {
    public enum Kind {
        SOLAR, ISOTOPE, BATTERY, BACKUP
    }

    /** States of a solar panel. */
    public static final int SOLAR_SUN = 0;
    public static final int SOLAR_NIGHT = 1;
    public static final int SOLAR_NO_SKY = 2;
    public static final int SOLAR_RAIN = 3;
    public static final int SOLAR_DUST = 4;
    /** States of a backup battery. */
    public static final int BACKUP_WAITING = 0;
    public static final int BACKUP_FEEDING = 1;

    private static final int DATA_KIND = 0;
    private static final int DATA_STATE = 1;
    private static final int DATA_ENERGY_LOW = 2;
    private static final int DATA_ENERGY_HIGH = 3;
    private static final int DATA_CAPACITY_LOW = 4;
    private static final int DATA_CAPACITY_HIGH = 5;
    private static final int DATA_MAKES = 6;
    private static final int DATA_EXTRA = 7;
    private static final int DATA_COUNT = 8;

    private final BlockPos pos;
    private final ContainerData data;

    /** Client side. */
    public EnergyPanelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    private EnergyPanelMenu(int containerId, BlockPos pos, ContainerData data) {
        super(TechMenus.ENERGY_PANEL.get(), containerId);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    /**
     * The window of the energy block at that place, or null if it is not one: the one place that knows what each of the
     * four blocks shows. Public for tests.
     */
    @javax.annotation.Nullable
    public static SimpleMenuProvider providerFor(net.minecraft.world.level.Level level, BlockPos pos) {
        Component title = level.getBlockState(pos).getBlock().getName();
        net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof SolarPanelBlockEntity panel) {
            return provider(pos, title, Kind.SOLAR, panel.energy(), () -> {
                if (panel.producing()) {
                    return panel.share() < 1.0f ? SOLAR_DUST : SOLAR_SUN;
                }
                return !level.isDay() ? SOLAR_NIGHT : !level.canSeeSky(pos.above()) ? SOLAR_NO_SKY : SOLAR_RAIN;
            }, () -> panel.producing() ? Math.round(com.projectrebirth.config.RebirthConfig.SOLAR_ENERGY_PER_TICK.get() * panel.share()) : 0, () -> 0);
        }
        if (entity instanceof RadioisotopeGeneratorBlockEntity generator) {
            return provider(pos, title, Kind.ISOTOPE, generator.energy(), () -> 0,
                    com.projectrebirth.config.RebirthConfig.ISOTOPE_ENERGY_PER_TICK::get, () -> 0);
        }
        if (entity instanceof BatteryBlockEntity battery) {
            return provider(pos, title, Kind.BATTERY, battery.energy(), () -> 0, () -> 0, () -> 0);
        }
        if (entity instanceof BackupBatteryBlockEntity battery) {
            return provider(pos, title, Kind.BACKUP, battery.energy(), () -> battery.isOnReserve() ? BACKUP_FEEDING : BACKUP_WAITING,
                    () -> 0, battery::supported);
        }
        return null;
    }

    /** Opens the window of the energy block at that place for the player (server side); nothing if it is not one. */
    public static void open(ServerPlayer player, BlockPos pos) {
        SimpleMenuProvider provider = providerFor(player.level(), pos);
        if (provider != null) {
            player.openMenu(provider, buf -> buf.writeBlockPos(pos));
        }
    }

    private static SimpleMenuProvider provider(BlockPos pos, Component title, Kind kind, ModEnergyStorage energy,
                                               IntSupplier state, IntSupplier makes, IntSupplier extra) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_KIND -> kind.ordinal();
                    case DATA_STATE -> state.getAsInt();
                    case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                    case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                    case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                    case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                    case DATA_MAKES -> makes.getAsInt();
                    case DATA_EXTRA -> extra.getAsInt();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        return new SimpleMenuProvider((id, inventory, p) -> new EnergyPanelMenu(id, pos, data), title);
    }

    public BlockPos pos() {
        return pos;
    }

    public Kind kind() {
        Kind[] kinds = Kind.values();
        int index = data.get(DATA_KIND);
        return index >= 0 && index < kinds.length ? kinds[index] : Kind.BATTERY;
    }

    public int state() {
        return data.get(DATA_STATE);
    }

    public int energy() {
        return EnergyUtil.join(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(DATA_CAPACITY_LOW), data.get(DATA_CAPACITY_HIGH));
    }

    /** Energy made a tick right now (generators), 0 for batteries. */
    public int makes() {
        return data.get(DATA_MAKES);
    }

    public int extra() {
        return data.get(DATA_EXTRA);
    }

    @Override
    public boolean stillValid(Player player) {
        return !player.level().getBlockState(pos).isAir()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
