package com.projectrebirth.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.AutomationHandler;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The controller of a multiblock (1.7.0, block A). It checks its construction when placed, when loaded and when a block inside
 * its bounds changes (never by polling every tick); a construction that is not whole pauses the work and nothing is lost. It
 * links its ports, takes energy and materials (by hand or through the ports) and builds the selected recipe stage by stage: the
 * materials of a stage are taken when it begins, its time runs while there is energy for each tick. Broken, it drops what it
 * holds and the materials of the stages already begun. Numbers in the config ([garrison.multiblocks]).
 */
public class MultiblockControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BASE_SLOT = 0;
    public static final int FIRST_MATERIAL = 1;
    public static final int MATERIALS = AssemblyRecipe.MAX_MATERIALS;
    public static final int OUTPUT_SLOT = FIRST_MATERIAL + MATERIALS;
    public static final int SLOTS = OUTPUT_SLOT + 1;

    public static final int STATE_NO_PLAN = 0;
    public static final int STATE_INCOMPLETE = 1;
    public static final int STATE_IDLE = 2;
    public static final int STATE_NO_BASE = 3;
    public static final int STATE_MATERIALS = 4;
    public static final int STATE_NO_ENERGY = 5;
    public static final int STATE_WORKING = 6;
    public static final int STATE_OUTPUT_FULL = 7;
    public static final int STATE_OFF = 8;

    public static final int DATA_STATE = 0;
    public static final int DATA_STAGE = 1;
    public static final int DATA_STAGES = 2;
    public static final int DATA_TICKS = 3;
    public static final int DATA_STAGE_TIME = 4;
    public static final int DATA_ENERGY_LOW = 5;
    public static final int DATA_ENERGY_HIGH = 6;
    public static final int DATA_CAPACITY_LOW = 7;
    public static final int DATA_CAPACITY_HIGH = 8;
    public static final int DATA_SELECTED = 9;
    public static final int DATA_PROBLEMS = 10;
    public static final int DATA_COUNT = 11;

    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == BASE_SLOT ? 1 : 64;
        }
    };
    /** Hoppers and pipes through the cargo port: materials (and the item to start from) in, the finished items out. */
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> slot == BASE_SLOT ? acceptsAsBase(stack) : slot >= FIRST_MATERIAL && slot < OUTPUT_SLOT && !acceptsAsBase(stack),
            slot -> slot == OUTPUT_SLOT);
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.MULTIBLOCK_ENERGY_CAPACITY.get(),
            () -> RebirthConfig.MULTIBLOCK_ENERGY_RECEIVE.get(), () -> 0, this::setChanged);

    @Nullable
    private ResourceLocation schemaId;
    @Nullable
    private UUID owner;
    @Nullable
    private ResourceLocation selected;
    /** The recipe being built, its stage (-1: none) and the ticks of that stage. */
    @Nullable
    private ResourceLocation running;
    private int stage = -1;
    private int ticks;
    /** The item the work started from, and the materials of the stages begun: given back if the controller is broken. */
    private ItemStack base = ItemStack.EMPTY;
    private final List<ItemStack> consumed = new ArrayList<>();

    private boolean dirty = true;
    @Nullable
    private MultiblockSchema.Check check;
    private final List<BlockPos> linked = new ArrayList<>();
    private int state = STATE_NO_PLAN;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            AssemblyRecipe recipe = runningRecipe();
            return switch (index) {
                case DATA_STATE -> state;
                case DATA_STAGE -> stage;
                case DATA_STAGES -> recipe == null ? 0 : recipe.stages().size();
                case DATA_TICKS -> ticks;
                case DATA_STAGE_TIME -> recipe == null || stage < 0 ? 0 : recipe.stages().get(stage).time();
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                case DATA_SELECTED -> selectedIndex();
                case DATA_PROBLEMS -> check == null ? 0 : check.problems().size();
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

    public MultiblockControllerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiblockRegistry.CONTROLLER_ENTITY.get(), pos, state);
        if (state.getBlock() instanceof MultiblockControllerBlock block) {
            schemaId = block.schema();
        }
    }

    // ---------------------------------------------------------------- what it is

    public ItemStackHandler items() {
        return items;
    }

    public AutomationHandler automation() {
        return automation;
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    @Nullable
    public ResourceLocation schemaId() {
        return schemaId;
    }

    /** Sets the plan of a controller that has none of its own (the generic controller of the tests). */
    public void setSchema(ResourceLocation id) {
        schemaId = id;
        markDirty();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** The client learns the plan (for the hologram). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (schemaId != null) {
            tag.putString("schema", schemaId.toString());
        }
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable
    public MultiblockSchema schema() {
        return level == null || schemaId == null ? null : MultiblockRegistry.schema(level.registryAccess(), schemaId);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        markDirty();
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public Direction facing() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    public int state() {
        return state;
    }

    public int stage() {
        return stage;
    }

    public int ticks() {
        return ticks;
    }

    @Nullable
    public MultiblockSchema.Check lastCheck() {
        return check;
    }

    public boolean complete() {
        return check != null && check.complete();
    }

    /** The materials of the stages begun (they come back if the controller is broken). */
    public List<ItemStack> consumedView() {
        return List.copyOf(consumed);
    }

    public ItemStack baseView() {
        return base.copy();
    }

    /** Whether the port at that place is one of this construction's. */
    public boolean serves(BlockPos port) {
        return linked.contains(port);
    }

    public List<RecipeHolder<AssemblyRecipe>> recipes() {
        return level == null || schemaId == null ? List.of() : MultiblockRegistry.recipesFor(level, schemaId);
    }

    public void select(@Nullable ResourceLocation id) {
        selected = id;
        setChanged();
    }

    @Nullable
    private AssemblyRecipe recipe(@Nullable ResourceLocation id) {
        if (id == null || level == null) {
            return null;
        }
        return level.getRecipeManager().byKey(id).filter(holder -> holder.value() instanceof AssemblyRecipe)
                .map(holder -> (AssemblyRecipe) holder.value()).orElse(null);
    }

    @Nullable
    public AssemblyRecipe runningRecipe() {
        return recipe(stage >= 0 ? running : selected);
    }

    private int selectedIndex() {
        List<RecipeHolder<AssemblyRecipe>> list = recipes();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    /** The menu's arrows: the previous or the next recipe of this multiblock. */
    void cycleRecipe(int step) {
        List<RecipeHolder<AssemblyRecipe>> list = recipes();
        if (list.isEmpty()) {
            selected = null;
        } else {
            int index = selectedIndex();
            int next = index < 0 ? (step > 0 ? 0 : list.size() - 1) : Math.floorMod(index + step, list.size());
            selected = list.get(next).id();
        }
        setChanged();
    }

    private boolean acceptsAsBase(ItemStack stack) {
        AssemblyRecipe recipe = recipe(selected);
        return recipe != null && recipe.input().isPresent() && recipe.acceptsBase(stack);
    }

    // ---------------------------------------------------------------- the construction

    /** A block inside the bounds changed: look again on the next tick. */
    public void markDirty() {
        dirty = true;
    }

    /** Looks at the construction now, links its ports and tells open windows. Public for tests. */
    public MultiblockSchema.Check revalidate() {
        dirty = false;
        MultiblockSchema schema = schema();
        if (level == null || schema == null || !schema.available()) {
            check = null;
            relink(List.of());
            return null;
        }
        check = schema.check(level, worldPosition, facing(), owner);
        List<BlockPos> ports = new ArrayList<>();
        if (check.complete()) {
            ports.addAll(check.energyPorts());
            ports.addAll(check.cargoPorts());
        }
        relink(ports);
        if (level instanceof ServerLevel server) {
            MultiblockTracker.track(server, worldPosition, check.bounds());
            for (ServerPlayer player : server.players()) {
                if (player.containerMenu instanceof MultiblockMenu menu && menu.pos().equals(worldPosition)) {
                    sendProblems(player);
                }
            }
        }
        return check;
    }

    private void relink(List<BlockPos> ports) {
        if (level == null) {
            return;
        }
        for (BlockPos old : linked) {
            if (!ports.contains(old) && level.getBlockEntity(old) instanceof PortBlockEntity port) {
                port.link(null);
            }
        }
        linked.clear();
        linked.addAll(ports);
        for (BlockPos pos : ports) {
            if (level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                port.link(worldPosition);
            }
        }
    }

    void sendProblems(ServerPlayer player) {
        if (player.connection.hasChannel(MultiblockStatusPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, MultiblockStatusPayload.of(worldPosition, check));
        }
    }

    // ---------------------------------------------------------------- the work

    public void serverTick() {
        if (dirty) {
            revalidate();
        }
        state = work();
        TechSounds.setWorking(this, state == STATE_WORKING);
    }

    private int work() {
        if (!MultiblockRegistry.enabled()) {
            return STATE_OFF;
        }
        if (check == null) {
            return STATE_NO_PLAN;
        }
        if (!check.complete()) {
            return STATE_INCOMPLETE;
        }
        if (stage < 0) {
            return start();
        }
        AssemblyRecipe recipe = recipe(running);
        if (recipe == null) {
            // the recipe is gone (a data pack changed): give everything back
            refund();
            return STATE_IDLE;
        }
        AssemblyRecipe.Stage current = recipe.stages().get(stage);
        if (ticks < current.time()) {
            if (!energy.consume(current.energy())) {
                return STATE_NO_ENERGY;
            }
            ticks++;
            setChanged();
            return STATE_WORKING;
        }
        if (stage + 1 < recipe.stages().size()) {
            AssemblyRecipe.Stage next = recipe.stages().get(stage + 1);
            if (!next.missing(materials()).isEmpty()) {
                return STATE_MATERIALS;
            }
            consumed.addAll(next.consume(materialsLive()));
            stage++;
            ticks = 0;
            setChanged();
            return STATE_WORKING;
        }
        finish(recipe);
        return STATE_IDLE;
    }

    private int start() {
        AssemblyRecipe recipe = recipe(selected);
        if (recipe == null || !recipe.available() || !recipe.multiblock().equals(schemaId)) {
            return STATE_IDLE;
        }
        ItemStack baseStack = items.getStackInSlot(BASE_SLOT);
        if (recipe.input().isPresent() && !recipe.acceptsBase(baseStack)) {
            return STATE_NO_BASE;
        }
        if (!outputTakes(recipe.result())) {
            return STATE_OUTPUT_FULL;
        }
        AssemblyRecipe.Stage first = recipe.stages().get(0);
        if (!first.missing(materials()).isEmpty()) {
            return STATE_MATERIALS;
        }
        base = recipe.input().isPresent() ? baseStack.copyWithCount(1) : ItemStack.EMPTY;
        if (!base.isEmpty()) {
            items.setStackInSlot(BASE_SLOT, baseStack.copyWithCount(baseStack.getCount() - 1));
        }
        consumed.clear();
        consumed.addAll(first.consume(materialsLive()));
        running = selected;
        stage = 0;
        ticks = 0;
        setChanged();
        return STATE_WORKING;
    }

    private void finish(AssemblyRecipe recipe) {
        ItemStack result = recipe.result().copy();
        List<ItemStack> left = new ArrayList<>();
        if (!base.isEmpty() && level != null) {
            switch (recipe.keep()) {
                case AssemblyRecipe.KEEP_UPGRADES -> left.addAll(Upgrades.moveAll(level.registryAccess(), Upgrades.ofStack(base), result));
                case AssemblyRecipe.KEEP_ALL -> result.applyComponents(base.getComponentsPatch());
                default -> {
                }
            }
        }
        ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            items.setStackInSlot(OUTPUT_SLOT, result);
        } else {
            output.grow(result.getCount());
            items.setStackInSlot(OUTPUT_SLOT, output);
        }
        left.forEach(this::giveBack);
        base = ItemStack.EMPTY;
        consumed.clear();
        running = null;
        stage = -1;
        ticks = 0;
        setChanged();
    }

    private boolean outputTakes(ItemStack result) {
        ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /** Copies of the material slots (to look at). */
    private List<ItemStack> materials() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = FIRST_MATERIAL; i < OUTPUT_SLOT; i++) {
            out.add(items.getStackInSlot(i).copy());
        }
        return out;
    }

    /** The material slots themselves (to take from); the handler is told afterwards. */
    private List<ItemStack> materialsLive() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = FIRST_MATERIAL; i < OUTPUT_SLOT; i++) {
            out.add(items.getStackInSlot(i));
        }
        setChanged();
        return out;
    }

    /** The materials still missing for the stage the controller waits for (or the first), for the window. */
    public List<net.neoforged.neoforge.common.crafting.SizedIngredient> missingNow() {
        AssemblyRecipe recipe = runningRecipe();
        if (recipe == null) {
            return List.of();
        }
        int next = stage < 0 ? 0 : stage + 1;
        return next < recipe.stages().size() ? recipe.stages().get(next).missing(materials()) : List.of();
    }

    private void giveBack(ItemStack stack) {
        ItemStack rest = stack;
        for (int i = FIRST_MATERIAL; i < OUTPUT_SLOT && !rest.isEmpty(); i++) {
            rest = items.insertItem(i, rest, false);
        }
        if (!rest.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), rest);
        }
    }

    /** Gives back the item started from and the materials of the stages begun, and forgets the work. */
    private void refund() {
        List<ItemStack> back = new ArrayList<>(consumed);
        if (!base.isEmpty()) {
            back.add(base);
        }
        base = ItemStack.EMPTY;
        consumed.clear();
        running = null;
        stage = -1;
        ticks = 0;
        back.forEach(this::giveBack);
        setChanged();
    }

    /** Broken: everything it holds and the materials of the stages begun fall out. */
    void dropContents() {
        if (level == null) {
            return;
        }
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            all.add(items.getStackInSlot(i));
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
        all.addAll(consumed);
        all.add(base);
        consumed.clear();
        base = ItemStack.EMPTY;
        stage = -1;
        for (ItemStack stack : all) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
        relink(List.of());
    }

    // ---------------------------------------------------------------- the world

    @Override
    public void onLoad() {
        super.onLoad();
        dirty = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            MultiblockTracker.untrack(server, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("energy", energy.saved());
        if (schemaId != null) {
            tag.putString("schema", schemaId.toString());
        }
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        if (selected != null) {
            tag.putString("selected", selected.toString());
        }
        if (running != null) {
            tag.putString("running", running.toString());
        }
        tag.putInt("stage", stage);
        tag.putInt("ticks", ticks);
        if (!base.isEmpty()) {
            tag.put("base", base.save(registries));
        }
        ListTag list = new ListTag();
        for (ItemStack stack : consumed) {
            if (!stack.isEmpty()) {
                list.add(stack.save(registries));
            }
        }
        tag.put("consumed", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        energy.load(tag.getInt("energy"));
        if (tag.contains("schema")) {
            schemaId = ResourceLocation.tryParse(tag.getString("schema"));
        }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        selected = tag.contains("selected") ? ResourceLocation.tryParse(tag.getString("selected")) : null;
        running = tag.contains("running") ? ResourceLocation.tryParse(tag.getString("running")) : null;
        stage = tag.contains("stage") ? tag.getInt("stage") : -1;
        ticks = tag.getInt("ticks");
        base = tag.contains("base") ? ItemStack.parseOptional(registries, tag.getCompound("base")) : ItemStack.EMPTY;
        consumed.clear();
        ListTag list = tag.getList("consumed", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                consumed.add(stack);
            }
        }
        dirty = true;
    }

    /** The bounds of the construction as last seen; the controller alone before the first look. */
    public BoundingBox bounds() {
        return check == null ? new BoundingBox(worldPosition) : check.bounds();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (dirty || check == null) {
                revalidate();
            }
            sendProblems(serverPlayer);
        }
        return new MultiblockMenu(containerId, inventory, worldPosition, items, data, this);
    }
}
