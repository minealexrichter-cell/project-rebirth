package com.projectrebirth.garrison;

import java.util.List;

import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The window of a service post (1.8.0, block E; before, a click only wrote a line): whether its plates lie right, its energy, the
 * machine on the plates now (kind, integrity, charge) and what the chest next to it holds for the service (iron ingots, armor plates,
 * rounds, rockets). No slots: the chest is the post's store, as before.
 */
public class ServicePostMenu extends AbstractContainerMenu {
    public static final int DATA_ENERGY_LOW = 0, DATA_ENERGY_HIGH = 1, DATA_CAPACITY_LOW = 2, DATA_CAPACITY_HIGH = 3, DATA_PLATES = 4,
            DATA_SERVED = 5, DATA_KIND = 6, DATA_STRUCTURAL = 7, DATA_MAX_STRUCTURAL = 8, DATA_CHARGE = 9, DATA_CHEST = 10, DATA_IRON = 11,
            DATA_PLATES_ITEM = 12, DATA_ROUNDS = 13, DATA_ROCKETS = 14, DATA_COUNT = 15;

    private final BlockPos pos;
    private final ContainerData data;

    /** Client side. */
    public ServicePostMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    private ServicePostMenu(int containerId, BlockPos pos, ContainerData data) {
        super(GarrisonRegistry.SERVICE_POST_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    /** The window of the post (server); public for tests. */
    public static SimpleMenuProvider provider(ServicePostBlockEntity post) {
        BlockPos pos = post.getBlockPos();
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                List<Entity> served = index >= DATA_SERVED && index <= DATA_CHARGE ? post.served() : List.of();
                Entity first = served.isEmpty() ? null : served.get(0);
                MachineCore core = first instanceof GarrisonMachine machine ? machine.machineCore() : null;
                return switch (index) {
                    case DATA_ENERGY_LOW -> EnergyUtil.low(post.energy().getEnergyStored());
                    case DATA_ENERGY_HIGH -> EnergyUtil.high(post.energy().getEnergyStored());
                    case DATA_CAPACITY_LOW -> EnergyUtil.low(post.energy().getMaxEnergyStored());
                    case DATA_CAPACITY_HIGH -> EnergyUtil.high(post.energy().getMaxEnergyStored());
                    case DATA_PLATES -> post.plates() == null ? 0 : 1;
                    case DATA_SERVED -> served.size();
                    case DATA_KIND -> core == null ? -1 : core.kind().ordinal();
                    case DATA_STRUCTURAL -> core == null ? 0 : Math.round(core.structural() * 10);
                    case DATA_MAX_STRUCTURAL -> core == null ? 0 : Math.round(core.maxStructural() * 10);
                    case DATA_CHARGE -> first instanceof GarrisonMachine machine ? Math.round(machine.consoleEnergy() * 100) : -100;
                    case DATA_CHEST -> post.stock() == null ? 0 : 1;
                    case DATA_IRON -> post.count(net.minecraft.world.item.Items.IRON_INGOT);
                    case DATA_PLATES_ITEM -> post.count(com.projectrebirth.powerarmor.PowerArmorRegistry.ARMOR_PLATE.get());
                    case DATA_ROUNDS -> post.count(com.projectrebirth.weapons.WeaponRegistry.ASSAULT_RIFLE_AMMO.get());
                    case DATA_ROCKETS -> post.count(GarrisonRegistry.ROCKET.get());
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
        return new SimpleMenuProvider((id, inventory, player) -> new ServicePostMenu(id, pos, data), post.getBlockState().getBlock().getName());
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
        return player.level().getBlockEntity(pos) instanceof ServicePostBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
