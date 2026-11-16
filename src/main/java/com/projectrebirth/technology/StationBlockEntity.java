package com.projectrebirth.technology;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * State of a production station: six input slots, one output slot, the recipe the player picked and the
 * production progress. Works only on the server; the window gets progress and selection through {@link #data}.
 */
public class StationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOTS = FabricationRecipe.MAX_INGREDIENTS;
    public static final int OUTPUT_SLOT = INPUT_SLOTS;
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_SELECTED = 2;
    public static final int DATA_ENERGY_LOW = 3;
    public static final int DATA_ENERGY_HIGH = 4;
    public static final int DATA_CAPACITY_LOW = 5;
    public static final int DATA_CAPACITY_HIGH = 6;
    /** 1 while an electromagnetic storm holds the station back (block E of 1.5.0). */
    public static final int DATA_EMI = 7;
    public static final int DATA_COUNT = 8;

    private final ItemStackHandler items = new ItemStackHandler(INPUT_SLOTS + 1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> slot < INPUT_SLOTS, slot -> slot == OUTPUT_SLOT);
    @Nullable
    private ResourceLocation selected;
    private int progress;
    /** How fast it may work now; checked once a second (block E of 1.5.0). */
    private float emiFactor = 1.0f;
    private boolean emiChecked;
    /** Takes energy from any side; nothing can be taken out. */
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.STATION_ENERGY_CAPACITY.get(),
            () -> Integer.MAX_VALUE, () -> 0, this::setChanged);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> selectedRecipe() == null ? 0 : productionTime(selectedRecipe().value());
                case DATA_SELECTED -> selectedIndex();
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                case DATA_EMI -> emiFactor < 1.0f ? 1 : 0;
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

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(TechBlockEntities.STATION.get(), pos, state);
    }

    public Station station() {
        return ((StationBlock) getBlockState().getBlock()).station();
    }

    public ItemStackHandler items() {
        return items;
    }

    /** For hoppers and pipes: materials go into the input slots, only the product comes out. */
    public AutomationHandler automation() {
        return automation;
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    /** Ticks of work done on the current product. */
    public int progress() {
        return progress;
    }

    /** Energy for one tick of work; 0 when the config lets stations work without energy. */
    public static int energyPerTick() {
        return RebirthConfig.STATIONS_NEED_ENERGY.get() ? RebirthConfig.STATION_ENERGY_PER_TICK.get() : 0;
    }

    /** Picks the recipe at the given index of the station's list; picking the selected one again clears it. */
    public void select(int index) {
        List<RecipeHolder<FabricationRecipe>> recipes = TechRecipes.recipesFor(level, station());
        if (index < 0 || index >= recipes.size()) {
            return;
        }
        ResourceLocation id = recipes.get(index).id();
        selected = id.equals(selected) ? null : id;
        progress = 0;
        setChanged();
    }

    @Nullable
    private RecipeHolder<FabricationRecipe> selectedRecipe() {
        if (selected == null || level == null) {
            return null;
        }
        return level.getRecipeManager().byKey(selected)
                .filter(holder -> holder.value() instanceof FabricationRecipe recipe && recipe.available())
                .map(StationBlockEntity::cast)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<FabricationRecipe> cast(RecipeHolder<?> holder) {
        return (RecipeHolder<FabricationRecipe>) holder;
    }

    private int selectedIndex() {
        if (selected == null || level == null) {
            return -1;
        }
        List<RecipeHolder<FabricationRecipe>> recipes = TechRecipes.recipesFor(level, station());
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).id().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    public static int productionTime(FabricationRecipe recipe) {
        return Math.max(1, (int) Math.round(recipe.time() * RebirthConfig.PRODUCTION_TIME_MULTIPLIER.get()));
    }

    void serverTick() {
        RecipeHolder<FabricationRecipe> holder = selectedRecipe();
        if (holder == null || !canWork(holder.value())) {
            TechSounds.setWorking(this, false);
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }
        FabricationRecipe recipe = holder.value();
        if (level instanceof net.minecraft.server.level.ServerLevel server && (server.getGameTime() % 20 == 0 || !emiChecked)) {
            emiChecked = true;
            emiFactor = com.projectrebirth.environment.Emi.stationFactor(server, worldPosition);
        }
        if (emiFactor < 1.0f && level.getRandom().nextFloat() >= emiFactor) {
            // an electromagnetic storm outside a shelter: wait, keeping progress and materials, using no energy
            TechSounds.setWorking(this, false);
            return;
        }
        if (!energy.consume(energyPerTick())) {
            // Out of energy: wait, keeping the progress made so far.
            TechSounds.setWorking(this, false);
            return;
        }
        TechSounds.setWorking(this, true);
        progress++;
        if (progress >= productionTime(recipe)) {
            progress = 0;
            level.playSound(null, worldPosition, ModSounds.STATION_DONE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            List<ItemStack> before = inputs();
            List<ItemStack> inputs = inputs();
            recipe.consume(inputs);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                items.setStackInSlot(i, inputs.get(i));
            }
            ItemStack result = recipe.result().copy();
            List<ItemStack> left = carryUpgrades(before, inputs, result);
            ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
            if (output.isEmpty()) {
                items.setStackInSlot(OUTPUT_SLOT, result);
            } else {
                output.grow(result.getCount());
            }
            left.forEach(this::giveBack);
        }
        setChanged();
    }

    /** A better body keeps the upgrades of the one it was made from. Returns the pieces that do not fit in the result. */
    private List<ItemStack> carryUpgrades(List<ItemStack> before, List<ItemStack> after, ItemStack result) {
        List<ItemStack> left = new ArrayList<>();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!before.get(i).isEmpty() && after.get(i).isEmpty()) {
                left.addAll(Upgrades.moveAll(level.registryAccess(), Upgrades.ofStack(before.get(i)), result));
            }
        }
        return left;
    }

    /** Into the input slots, where the player sees it; to the ground if they are full. */
    private void giveBack(ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < INPUT_SLOTS && !rest.isEmpty(); i++) {
            rest = items.insertItem(i, rest, false);
        }
        if (!rest.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), rest);
        }
    }

    private boolean canWork(FabricationRecipe recipe) {
        ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
        boolean fits = output.isEmpty() || ItemStack.isSameItemSameComponents(output, recipe.result())
                && output.getCount() + recipe.result().getCount() <= output.getMaxStackSize();
        return fits && recipe.missing(inputs()).isEmpty();
    }

    /** Copies of the input stacks, in slot order. */
    private List<ItemStack> inputs() {
        List<ItemStack> list = new ArrayList<>(INPUT_SLOTS);
        for (int i = 0; i < INPUT_SLOTS; i++) {
            list.add(items.getStackInSlot(i).copy());
        }
        return list;
    }

    void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < items.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        if (selected != null) {
            tag.putString("selected", selected.toString());
        }
        tag.putInt("progress", progress);
        tag.putInt("energy", energy.saved());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        selected = tag.contains("selected") ? ResourceLocation.tryParse(tag.getString("selected")) : null;
        progress = tag.getInt("progress");
        // Stations from 0.1.0 have no energy yet; their progress is kept.
        energy.load(tag.getInt("energy"));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StationMenu(containerId, inventory, this, items, data);
    }
}
