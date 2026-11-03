package com.projectrebirth.technology;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Maintenance station: one service slot for packed armor or a packed body and three material slots. Every repair
 * step takes {@code maintenance.stepTicks} and uses one material item. Works only on the server.
 */
public class MaintenanceBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SERVICE_SLOT = 0;
    public static final int FIRST_MATERIAL_SLOT = 1;
    public static final int MATERIAL_SLOTS = 3;
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;

    private final ItemStackHandler items = new ItemStackHandler(1 + MATERIAL_SLOTS) {
        @Override
        public int getSlotLimit(int slot) {
            return slot == SERVICE_SLOT ? 1 : super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> slot != SERVICE_SLOT || Maintenance.needsRepair(stack),
            slot -> slot == SERVICE_SLOT && !Maintenance.needsRepair(items.getStackInSlot(SERVICE_SLOT)));
    private int progress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == DATA_PROGRESS ? progress : stepTicks();
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public MaintenanceBlockEntity(BlockPos pos, BlockState state) {
        super(TechBlockEntities.MAINTENANCE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    /**
     * For hoppers and pipes: the serviced item goes in only if it can be repaired and comes out only when fully
     * repaired; materials only go in.
     */
    public AutomationHandler automation() {
        return automation;
    }

    public static int stepTicks() {
        return RebirthConfig.MAINTENANCE_STEP_TICKS.get();
    }

    void serverTick() {
        ItemStack target = items.getStackInSlot(SERVICE_SLOT);
        Item material = Maintenance.needsRepair(target) ? Maintenance.materialFor(target, this::hasMaterial) : null;
        if (material == null) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }
        progress++;
        if (progress >= stepTicks()) {
            progress = 0;
            takeMaterial(material);
            ItemStack repaired = target.copy();
            Maintenance.apply(repaired, material);
            items.setStackInSlot(SERVICE_SLOT, repaired);
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.4f);
            }
        }
        setChanged();
    }

    private boolean hasMaterial(Item item) {
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            if (items.getStackInSlot(FIRST_MATERIAL_SLOT + i).is(item)) {
                return true;
            }
        }
        return false;
    }

    private void takeMaterial(Item item) {
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            if (items.getStackInSlot(FIRST_MATERIAL_SLOT + i).is(item)) {
                items.extractItem(FIRST_MATERIAL_SLOT + i, 1, false);
                return;
            }
        }
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
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        progress = tag.getInt("progress");
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MaintenanceMenu(containerId, inventory, worldPosition, items, data);
    }
}
