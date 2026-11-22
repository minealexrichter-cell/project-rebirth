package com.projectrebirth.water;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.shelter.CartridgeItem;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.technology.AutomationHandler;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The water purifier (1.6.0, block C): pushes polluted water through a filter cartridge (the one of the air filtration unit) with
 * energy. A bottle of polluted water comes out as a clean water bottle, a bucket as a water bucket; each wears the cartridge a
 * little, and a used up cartridge turns spent in its slot. Hoppers bring polluted water and cartridges and take the clean water
 * and the spent cartridges away. Numbers in the config ([water.purifier]).
 */
public class WaterPurifierBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOT = 0;
    public static final int CARTRIDGE_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int SLOTS = 3;

    public static final int STATE_IDLE = 0;
    public static final int STATE_WORKING = 1;
    public static final int STATE_NO_CARTRIDGE = 2;
    public static final int STATE_NO_ENERGY = 3;
    public static final int STATE_OUTPUT_FULL = 4;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_ENERGY_LOW = 2;
    public static final int DATA_ENERGY_HIGH = 3;
    public static final int DATA_CAPACITY_LOW = 4;
    public static final int DATA_CAPACITY_HIGH = 5;
    public static final int DATA_CARTRIDGE = 6;
    public static final int DATA_STATE = 7;
    public static final int DATA_COUNT = 8;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == CARTRIDGE_SLOT ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case INPUT_SLOT -> work(stack) > 0;
                case CARTRIDGE_SLOT -> stack.is(ShelterRegistry.FILTER_CARTRIDGE.get()) || stack.is(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get());
                default -> true;
            };
        }
    };
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> slot == INPUT_SLOT && work(stack) > 0 || slot == CARTRIDGE_SLOT && stack.is(ShelterRegistry.FILTER_CARTRIDGE.get()),
            slot -> slot == OUTPUT_SLOT || slot == CARTRIDGE_SLOT && items.getStackInSlot(CARTRIDGE_SLOT).is(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get()));
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.PURIFIER_ENERGY_CAPACITY.get(),
            () -> Math.max(1, RebirthConfig.PURIFIER_ENERGY_PER_TICK.get() * 4), () -> 0, this::setChanged);
    private int progress;
    private int state;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> totalTicks(items.getStackInSlot(INPUT_SLOT));
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                case DATA_CARTRIDGE -> Math.round(1000.0f * CartridgeItem.lifeShare(items.getStackInSlot(CARTRIDGE_SLOT)));
                case DATA_STATE -> state;
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

    public WaterPurifierBlockEntity(BlockPos pos, BlockState state) {
        super(WaterRegistry.WATER_PURIFIER_ENTITY.get(), pos, state);
    }

    /** How many bottles of work an input is: a bottle 1, a bucket a few, a piece of contaminated food 1, anything else 0. */
    public static int work(ItemStack stack) {
        if (stack.is(WaterRegistry.POLLUTED_WATER_BOTTLE.get())) {
            return 1;
        }
        if (stack.is(WaterRegistry.POLLUTED_WATER_BUCKET.get())) {
            return RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.PURIFIER_BUCKET_BOTTLES.get() : 3;
        }
        // 1.9.0: contaminated food is washed, one piece for a bottle's work
        return com.projectrebirth.environment.ContaminatedFood.isContaminated(stack) ? 1 : 0;
    }

    static int totalTicks(ItemStack input) {
        return work(input) * (RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.PURIFIER_TICKS.get() : 80);
    }

    /** What clean water an input gives. */
    public static ItemStack cleaned(ItemStack input) {
        if (com.projectrebirth.environment.ContaminatedFood.isContaminated(input)) {
            return com.projectrebirth.environment.ContaminatedFood.washed(input.copyWithCount(1));
        }
        if (input.is(WaterRegistry.POLLUTED_WATER_BUCKET.get())) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
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

    void serverTick() {
        ItemStack input = items.getStackInSlot(INPUT_SLOT);
        int work = work(input);
        state = work == 0 ? STATE_IDLE : step(input, work);
        if (state != STATE_WORKING && work == 0) {
            progress = 0;
        }
        TechSounds.setWorking(this, state == STATE_WORKING);
    }

    private int step(ItemStack input, int work) {
        ItemStack cartridge = items.getStackInSlot(CARTRIDGE_SLOT);
        float wear = (float) (RebirthConfig.SHELTER_CARTRIDGE_LIFE.get() * RebirthConfig.PURIFIER_CARTRIDGE_SHARE.get() * work);
        if (!cartridge.is(ShelterRegistry.FILTER_CARTRIDGE.get())) {
            return STATE_NO_CARTRIDGE;
        }
        ItemStack out = cleaned(input);
        ItemStack held = items.getStackInSlot(OUTPUT_SLOT);
        if (!held.isEmpty() && (!ItemStack.isSameItemSameComponents(held, out) || held.getCount() >= held.getMaxStackSize())) {
            return STATE_OUTPUT_FULL; // water bottles stack (config [water] waterBottleStack), buckets do not
        }
        if (!energy.consume(RebirthConfig.PURIFIER_ENERGY_PER_TICK.get())) {
            return STATE_NO_ENERGY;
        }
        progress++;
        if (progress >= totalTicks(input)) {
            progress = 0;
            ItemStack left = input.copy();
            left.shrink(1);
            items.setStackInSlot(INPUT_SLOT, left);
            items.setStackInSlot(OUTPUT_SLOT, held.isEmpty() ? out : held.copyWithCount(held.getCount() + 1));
            float life = CartridgeItem.life(cartridge) - wear;
            if (life <= 0.0f) {
                items.setStackInSlot(CARTRIDGE_SLOT, new ItemStack(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get()));
            } else {
                ItemStack worn = cartridge.copy();
                CartridgeItem.setLife(worn, life);
                items.setStackInSlot(CARTRIDGE_SLOT, worn);
            }
        }
        return STATE_WORKING;
    }

    void dropContents() {
        for (int slot = 0; slot < SLOTS; slot++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(slot));
            items.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("progress", progress);
        tag.putInt("energy", energy.saved());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        progress = tag.getInt("progress");
        energy.load(tag.getInt("energy"));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WaterPurifierMenu(containerId, inventory, worldPosition, items, data);
    }
}
