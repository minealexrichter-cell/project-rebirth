package com.projectrebirth.garrison;

import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The window of a turret mount (1.8.0, block E; before, a click only wrote a line): its nine slots of ammunition, its energy, and
 * the turret on it: kind, integrity, how far it shoots, its upgrades, whether it can shoot now. The mount itself is unchanged.
 */
public class TurretMountMenu extends AbstractContainerMenu {
    public static final int AMMO_X = 170;
    public static final int AMMO_Y = 37;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 150;
    /** The data: energy and capacity (split), the turret (0 none, 1 machine gun, 2 rockets), its integrities ×10, range, upgrades, rounds, rockets. */
    public static final int DATA_ENERGY_LOW = 0, DATA_ENERGY_HIGH = 1, DATA_CAPACITY_LOW = 2, DATA_CAPACITY_HIGH = 3, DATA_TURRET = 4,
            DATA_STRUCTURAL = 5, DATA_MAX_STRUCTURAL = 6, DATA_CRITICAL = 7, DATA_MAX_CRITICAL = 8, DATA_RANGE = 9, DATA_UPGRADES = 10,
            DATA_ROUNDS = 11, DATA_ROCKETS = 12, DATA_PER_SHOT = 13, DATA_COUNT = 14;

    private final BlockPos pos;
    private final ContainerData data;

    /** Client side. */
    public TurretMountMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(TurretMountBlockEntity.SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return TurretMountBlockEntity.isAmmo(stack);
            }
        }, new SimpleContainerData(DATA_COUNT));
    }

    private TurretMountMenu(int containerId, Inventory inventory, BlockPos pos, ItemStackHandler ammo, ContainerData data) {
        super(GarrisonRegistry.TURRET_MOUNT_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        for (int i = 0; i < TurretMountBlockEntity.SLOTS; i++) {
            addSlot(new SlotItemHandler(ammo, i, AMMO_X + (i % 3) * 18, AMMO_Y + (i / 3) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    /** The turret standing on the mount, or null. */
    @Nullable
    public static DefenceTurretEntity turretOn(Level level, BlockPos mount) {
        List<DefenceTurretEntity> found = level.getEntitiesOfClass(DefenceTurretEntity.class, new AABB(mount.above()).inflate(0.2, 0.5, 0.2),
                DefenceTurretEntity::isAlive);
        return found.isEmpty() ? null : found.get(0);
    }

    /** The window of the mount at that place (server); public for tests. */
    public static SimpleMenuProvider provider(TurretMountBlockEntity mount) {
        BlockPos pos = mount.getBlockPos();
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                Level level = mount.getLevel();
                DefenceTurretEntity turret = level == null ? null : turretOn(level, pos);
                return switch (index) {
                    case DATA_ENERGY_LOW -> EnergyUtil.low(mount.energy().getEnergyStored());
                    case DATA_ENERGY_HIGH -> EnergyUtil.high(mount.energy().getEnergyStored());
                    case DATA_CAPACITY_LOW -> EnergyUtil.low(mount.energy().getMaxEnergyStored());
                    case DATA_CAPACITY_HIGH -> EnergyUtil.high(mount.energy().getMaxEnergyStored());
                    case DATA_TURRET -> turret == null ? 0 : turret.turret().ordinal() + 1;
                    case DATA_STRUCTURAL -> turret == null ? 0 : Math.round(turret.machineCore().structural() * 10);
                    case DATA_MAX_STRUCTURAL -> turret == null ? 0 : Math.round(turret.machineCore().maxStructural() * 10);
                    case DATA_CRITICAL -> turret == null ? 0 : Math.round(turret.machineCore().critical() * 10);
                    case DATA_MAX_CRITICAL -> turret == null ? 0 : Math.round(turret.machineCore().maxCritical() * 10);
                    case DATA_RANGE -> turret == null ? 0 : turret.turret().range();
                    case DATA_UPGRADES -> turret == null ? 0 : turret.consoleUpgrades().size();
                    case DATA_ROUNDS -> mount.count(WeaponRegistry.ASSAULT_RIFLE_AMMO.get());
                    case DATA_ROCKETS -> mount.count(GarrisonRegistry.ROCKET.get());
                    case DATA_PER_SHOT -> turret == null ? 0 : turret.turret().energyPerShot();
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
        return new SimpleMenuProvider((id, inventory, player) -> new TurretMountMenu(id, inventory, pos, mount.ammo(), data),
                mount.getBlockState().getBlock().getName());
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    public int energy() {
        return EnergyUtil.join(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(DATA_CAPACITY_LOW), data.get(DATA_CAPACITY_HIGH));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof TurretMountBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int ammo = TurretMountBlockEntity.SLOTS;
        if (index < ammo) {
            if (!moveItemStackTo(stack, ammo, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!TurretMountBlockEntity.isAmmo(stack) || !moveItemStackTo(stack, 0, ammo, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
