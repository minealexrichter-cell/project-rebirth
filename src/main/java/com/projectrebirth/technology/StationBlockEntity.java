package com.projectrebirth.technology;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private final ItemStackHandler items = new ItemStackHandler(INPUT_SLOTS + 1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    @Nullable
    private ResourceLocation selected;
    private int progress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> selectedRecipe() == null ? 0 : productionTime(selectedRecipe().value());
                case DATA_SELECTED -> selectedIndex();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
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
                .filter(holder -> holder.value() instanceof FabricationRecipe)
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
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }
        FabricationRecipe recipe = holder.value();
        progress++;
        if (progress >= productionTime(recipe)) {
            progress = 0;
            List<ItemStack> inputs = inputs();
            recipe.consume(inputs);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                items.setStackInSlot(i, inputs.get(i));
            }
            ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
            if (output.isEmpty()) {
                items.setStackInSlot(OUTPUT_SLOT, recipe.result().copy());
            } else {
                output.grow(recipe.result().getCount());
            }
        }
        setChanged();
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        selected = tag.contains("selected") ? ResourceLocation.tryParse(tag.getString("selected")) : null;
        progress = tag.getInt("progress");
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
