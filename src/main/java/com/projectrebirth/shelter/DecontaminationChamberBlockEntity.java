package com.projectrebirth.shelter;

import java.util.List;
import java.util.Locale;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.technology.AutomationHandler;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.LifeSupport;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The decontamination chamber (lower half). Whoever stands in it, or right in front of it (a power armor or a body is too
 * large to step in), is washed: their external contamination comes off in a few seconds, for energy and decontamination
 * solution. Hoppers bring the solution. A life support block: the cable feeds it first.
 */
public class DecontaminationChamberBlockEntity extends BlockEntity implements LifeSupport {
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ShelterRegistry.DECONTAMINATION_SOLUTION.get());
        }
    };
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> stack.is(ShelterRegistry.DECONTAMINATION_SOLUTION.get()), slot -> false);
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.DECON_CAPACITY.get(),
            () -> RebirthConfig.SHELTER_ENERGY_RECEIVE.get(), () -> 0, this::setChanged);
    /** Washing left of the solution already opened. */
    private float solutionLeft;
    private long lastWashed = Long.MIN_VALUE / 2;

    public DecontaminationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ShelterRegistry.DECONTAMINATION_CHAMBER_ENTITY.get(), pos, state);
    }

    /** Contamination the solution already poured in can still wash off (1.8.0: for the window). */
    public float solutionLeft() {
        return solutionLeft;
    }

    public ItemStackHandler items() {
        return items;
    }

    public AutomationHandler automation() {
        return automation;
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    @Override
    public IEnergyStorage lifeSupportEnergy() {
        return energy;
    }

    /** Where it washes: its two blocks and the block in front of them. */
    public AABB washArea() {
        Direction front = getBlockState().getValue(DecontaminationChamberBlock.FACING);
        AABB inside = new AABB(worldPosition).expandTowards(0, 1, 0);
        return inside.expandTowards(front.getStepX(), 0, front.getStepZ());
    }

    private static boolean isCarrier(Entity entity) {
        return entity instanceof Player player && !player.isSpectator() && !(player.getVehicle() instanceof PowerArmorEntity)
                || entity instanceof PowerArmorEntity || entity instanceof ArtificialBodyEntity;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        List<Entity> carriers = server.getEntities((Entity) null, washArea(), entity -> isCarrier(entity) && Contamination.of(entity) > 0.0f);
        boolean washed = false;
        int need = RebirthConfig.STATIONS_NEED_ENERGY.get() ? RebirthConfig.DECON_ENERGY.get() : 0;
        if (!carriers.isEmpty() && energy.getEnergyStored() >= need) {
            float rate = RebirthConfig.DECON_PER_SECOND.get().floatValue() / 20.0f;
            for (Entity carrier : carriers) {
                float wash = Math.min(Contamination.of(carrier), rate);
                if (solutionLeft < wash && !items.getStackInSlot(0).isEmpty()) {
                    items.extractItem(0, 1, false);
                    solutionLeft += RebirthConfig.DECON_PER_SOLUTION.get().floatValue();
                }
                wash = Math.min(wash, solutionLeft);
                if (wash <= 0.0f) {
                    continue;
                }
                Contamination.set(carrier, Contamination.of(carrier) - wash);
                solutionLeft -= wash;
                washed = true;
                if (server.getGameTime() % 4 == 0) {
                    server.sendParticles(ParticleTypes.SPLASH, carrier.getX(), carrier.getY() + carrier.getBbHeight(), carrier.getZ(),
                            6, 0.3, 0.2, 0.3, 0.1);
                }
            }
            if (washed) {
                energy.consume(need);
                lastWashed = server.getGameTime();
                setChanged();
            }
        }
        if (server.getGameTime() % 20 == 0 && washFood(server, need)) {
            lastWashed = server.getGameTime();
            setChanged();
        }
        boolean washing = server.getGameTime() - lastWashed < 10;
        TechSounds.setWorking(this, washing);
        // the water falls in the upper half too
        BlockPos above = worldPosition.above();
        BlockState upper = server.getBlockState(above);
        if (upper.getBlock() instanceof DecontaminationChamberBlock && upper.getValue(TechSounds.WORKING) != washing) {
            server.setBlock(above, upper.setValue(TechSounds.WORKING, washing), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /**
     * 1.9.0: a person standing inside has one stack of contaminated food in their inventory washed each second, for the same
     * energy and [wasteland.food] chamberSolutionPerStack of the solution. Public for tests.
     */
    public boolean washFood(ServerLevel server, int need) {
        if (energy.getEnergyStored() < need) {
            return false;
        }
        for (Player player : server.getEntitiesOfClass(Player.class, washArea(), player -> !player.isSpectator())) {
            net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!com.projectrebirth.environment.ContaminatedFood.isContaminated(stack)) {
                    continue;
                }
                float cost = com.projectrebirth.config.WastelandConfig.FOOD_CHAMBER_SOLUTION.get().floatValue();
                if (solutionLeft < cost && !items.getStackInSlot(0).isEmpty()) {
                    items.extractItem(0, 1, false);
                    solutionLeft += RebirthConfig.DECON_PER_SOLUTION.get().floatValue();
                }
                if (solutionLeft < cost) {
                    return false;
                }
                solutionLeft -= cost;
                energy.consume(need);
                inventory.setItem(slot, com.projectrebirth.environment.ContaminatedFood.washed(stack));
                server.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
                return true;
            }
        }
        return false;
    }

    /** The line shown on right-click. */
    public Component status(Player player) {
        int solutions = items.getStackInSlot(0).getCount();
        return Component.translatable("message.projectrebirth.decontamination.status", solutions,
                String.format(Locale.ROOT, "%.0f", solutionLeft), EnergyUtil.format(energy.getEnergyStored()),
                String.format(Locale.ROOT, "%.1f", Contamination.of(player.getVehicle() instanceof PowerArmorEntity armor ? armor : player)));
    }

    /** Puts solution in from a player's hand; returns what is left in the hand. */
    public ItemStack insert(ItemStack stack) {
        return items.insertItem(0, stack, false);
    }

    void dropContents() {
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(0));
        items.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("energy", energy.saved());
        tag.putFloat("solution_left", solutionLeft);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        energy.load(tag.getInt("energy"));
        solutionLeft = tag.getFloat("solution_left");
    }
}
