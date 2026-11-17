package com.projectrebirth.garrison;

import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The console of a service post (1.7.0, block H; the concept's section 5.6). Next to it lie four post plates in a square of two by
 * two. Once a second a machine standing on the plates is served: charged from the console's energy (an android's body charge, a
 * mech's own store), its structural integrity mended with iron ingots or armor plates from a chest next to the console, a mech's
 * magazine filled with rounds and rockets from that chest. Critical integrity it does not mend: that is the maintenance station's.
 * Androids and mechs come to it with the order "to the post" and when low on energy.
 */
public class ServicePostBlockEntity extends BlockEntity {
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.POST_ENERGY_CAPACITY.get(),
            () -> Math.max(1, RebirthConfig.POST_ENERGY_CAPACITY.get() / 20), () -> 0, this::setChanged);
    private int repairWait;

    public ServicePostBlockEntity(BlockPos pos, BlockState state) {
        super(GarrisonRegistry.SERVICE_POST_ENTITY.get(), pos, state);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    /** The lower north-west corner of the square of plates next to the console, or null when there is none. */
    @Nullable
    public BlockPos plates() {
        if (level == null) {
            return null;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = worldPosition.relative(side);
            if (!isPlate(next)) {
                continue;
            }
            for (int dx = -1; dx <= 0; dx++) {
                for (int dz = -1; dz <= 0; dz++) {
                    BlockPos corner = next.offset(dx, 0, dz);
                    if (isPlate(corner) && isPlate(corner.east()) && isPlate(corner.south()) && isPlate(corner.south().east())) {
                        return corner;
                    }
                }
            }
        }
        return null;
    }

    private boolean isPlate(BlockPos pos) {
        return level.getBlockState(pos).is(GarrisonRegistry.SERVICE_POST_PLATE.get());
    }

    /** Where a machine stands to be served: the middle of the plates' top (1.7.2: half a block up, a plate is a slab), or null. */
    @Nullable
    public Vec3 standPoint() {
        BlockPos corner = plates();
        return corner == null ? null : new Vec3(corner.getX() + 1.0, corner.getY() + ServicePostPlateBlock.HEIGHT, corner.getZ() + 1.0);
    }

    /** The machines on the plates. */
    public List<Entity> served() {
        BlockPos corner = plates();
        if (corner == null || level == null) {
            return List.of();
        }
        AABB area = new AABB(corner.getX(), corner.getY() + ServicePostPlateBlock.HEIGHT - 0.25, corner.getZ(), corner.getX() + 2, corner.getY() + 4, corner.getZ() + 2);
        return level.getEntities((Entity) null, area, found -> found instanceof GarrisonMachine && found.isAlive());
    }

    /** A chest (any item store) next to the console, not a plate; or null. */
    @Nullable
    private IItemHandler chest() {
        for (Direction side : Direction.values()) {
            BlockPos next = worldPosition.relative(side);
            if (isPlate(next)) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, next, side.getOpposite());
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    /** Public for tests. */
    public void serverTick() {
        if (!(level instanceof ServerLevel) || (level.getGameTime() + worldPosition.hashCode()) % 20 != 0) {
            return;
        }
        second();
    }

    /** One second of service. Public for tests. */
    public void second() {
        if (!RebirthConfig.GARRISON_ENABLED.get() || !RebirthConfig.POST_ENABLED.get()) {
            return;
        }
        List<Entity> machines = served();
        if (machines.isEmpty()) {
            return;
        }
        IItemHandler chest = chest();
        boolean repairNow = --repairWait <= 0;
        if (repairNow) {
            repairWait = RebirthConfig.POST_REPAIR_SECONDS.get();
        }
        for (Entity entity : machines) {
            charge(entity);
            if (chest != null) {
                if (repairNow) {
                    repair((GarrisonMachine) entity, chest);
                }
                if (entity instanceof GuardMechEntity mech) {
                    mech.refill(chest);
                }
            }
        }
    }

    private void charge(Entity entity) {
        int give = Math.min(RebirthConfig.POST_CHARGE_PER_SECOND.get(), energy.getEnergyStored());
        if (give <= 0) {
            return;
        }
        if (entity instanceof GuardMechEntity mech) {
            energy.consume(mech.addEnergy(give));
        } else if (entity instanceof AndroidEntity android && BodyPower.enabled()) {
            float room = BodyPower.capacity(android) - android.charge();
            int used = (int) Math.max(0, Math.min(give, Math.ceil(room)));
            if (used > 0) {
                android.setCharge(Math.min(BodyPower.capacity(android), android.charge() + used));
                energy.consume(used);
            }
        }
    }

    private static void repair(GarrisonMachine machine, IItemHandler chest) {
        MachineCore core = machine.machineCore();
        if (core.wreck() || core.structural() >= core.maxStructural()) {
            return;
        }
        float perIngot = RebirthConfig.MACHINE_REPAIR_PER_INGOT.get().floatValue();
        for (int slot = 0; slot < chest.getSlots(); slot++) {
            ItemStack stack = chest.getStackInSlot(slot);
            boolean plate = stack.is(com.projectrebirth.powerarmor.PowerArmorRegistry.ARMOR_PLATE.get());
            if ((plate || stack.is(Items.IRON_INGOT)) && !chest.extractItem(slot, 1, false).isEmpty()) {
                core.repairStructural(perIngot * (plate ? RebirthConfig.POST_PLATE_REPAIR.get().floatValue() : 1.0f));
                return;
            }
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

    @Override
    public void onLoad() {
        super.onLoad();
        LoadedSites.add(LoadedSites.Kind.SERVICE_POST, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LoadedSites.remove(LoadedSites.Kind.SERVICE_POST, this);
    }

    /** The stand point of the nearest working service post within the config's search radius (loaded ones only), or null. */
    @Nullable
    public static Vec3 nearest(ServerLevel level, BlockPos from) {
        ServicePostBlockEntity post = LoadedSites.nearest(level, LoadedSites.Kind.SERVICE_POST, ServicePostBlockEntity.class, from,
                RebirthConfig.POST_RADIUS.get(), found -> found.standPoint() != null);
        return post == null ? null : post.standPoint();
    }
}
