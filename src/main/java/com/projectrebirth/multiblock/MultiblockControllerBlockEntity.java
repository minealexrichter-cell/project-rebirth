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
    /** Block B: the manipulators of the construction, and whether they were last set working. */
    private final List<BlockPos> manipulators = new ArrayList<>();
    /** The frames hidden under the hall of a whole construction (Shell), and whether the hall stands (also on the client). */
    private final List<BlockPos> hidden = new ArrayList<>();
    private boolean formed;
    private boolean manipulatorsWorking;
    /** What the clients last heard of the machine under assembly: its result and the stage of its model (-1: none). */
    private ItemStack shownResult = ItemStack.EMPTY;
    private int shownStage = -1;
    /** On the client: the machine under assembly as the server told it. */
    private ItemStack previewResult = ItemStack.EMPTY;
    /** The part of the current stage the grippers carry: on the server as last sent, on the client as heard. */
    private ItemStack shownPart = ItemStack.EMPTY;
    private ItemStack previewPart = ItemStack.EMPTY;
    private int previewStage = -1;

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
        tag.putInt("preview_stage", shownStage);
        tag.putBoolean("formed", formed);
        if (!shownResult.isEmpty()) {
            tag.put("preview_result", shownResult.save(registries));
        }
        if (!shownPart.isEmpty()) {
            tag.put("preview_part", shownPart.save(registries));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("schema")) {
            schemaId = ResourceLocation.tryParse(tag.getString("schema"));
        }
        previewStage = tag.contains("preview_stage") ? tag.getInt("preview_stage") : -1;
        formed = tag.getBoolean("formed");
        previewResult = tag.contains("preview_result") ? ItemStack.parseOptional(registries, tag.getCompound("preview_result")) : ItemStack.EMPTY;
        previewPart = tag.contains("preview_part") ? ItemStack.parseOptional(registries, tag.getCompound("preview_part")) : ItemStack.EMPTY;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
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
        findManipulators(schema);
        formShell(schema);
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

    /**
     * The hall (1.7.0): over a whole construction whose plan has a shell, its frames turn invisible and the clients draw the hall
     * (MultiblockControllerRenderer); otherwise the frames hidden before show again.
     */
    private void formShell(MultiblockSchema schema) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean whole = check != null && check.complete() && schema.shell().isPresent();
        List<BlockPos> now = new ArrayList<>();
        if (whole) {
            String hide = schema.shell().get().hide();
            for (int y = 0; y < schema.height(); y++) {
                for (int r = 0; r < schema.depth(); r++) {
                    for (int c = 0; c < schema.width(); c++) {
                        if (hide.indexOf(schema.letter(y, r, c)) >= 0) {
                            now.add(schema.worldPos(worldPosition, facing(), c, y, r).immutable());
                        }
                    }
                }
            }
        }
        for (BlockPos pos : hidden) {
            if (!now.contains(pos)) {
                setFormed(pos, false);
            }
        }
        for (BlockPos pos : now) {
            setFormed(pos, true);
        }
        hidden.clear();
        hidden.addAll(now);
        if (formed != whole) {
            formed = whole;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private void setFormed(BlockPos pos, boolean value) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FrameBlock && state.getValue(FrameBlock.FORMED) != value) {
            level.setBlock(pos, state.setValue(FrameBlock.FORMED, value), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Whether the hall of the construction stands (on the client: as the server said). */
    public boolean formed() {
        return formed;
    }

    /** The manipulators inside a whole construction, each turned to face the machine under assembly. */
    private void findManipulators(MultiblockSchema schema) {
        // the arms of the construction as it was let go first: stopped and unlinked (the whole ones are linked again below)
        if (level != null) {
            for (BlockPos pos : manipulators) {
                BlockState old = level.getBlockState(pos);
                if (old.getBlock() instanceof ManipulatorBlock && old.getValue(TechSounds.WORKING)) {
                    level.setBlock(pos, old.setValue(TechSounds.WORKING, false), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
                if (level.getBlockEntity(pos) instanceof ManipulatorBlockEntity arm && worldPosition.equals(arm.controller())) {
                    arm.setController(null);
                }
            }
        }
        manipulators.clear();
        manipulatorsWorking = false;
        if (level == null || check == null || !check.complete()) {
            return;
        }
        net.minecraft.world.phys.Vec3 target = schema.displayPoint(worldPosition, facing());
        BoundingBox box = check.bounds();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState found = level.getBlockState(pos);
            if (found.getBlock() instanceof ManipulatorBlock) {
                double dx = target.x - (pos.getX() + 0.5), dz = target.z - (pos.getZ() + 0.5);
                Direction toward = Direction.getNearest(dx, 0, dz);
                BlockState turned = found.setValue(HorizontalDirectionalBlock.FACING, toward.getAxis().isHorizontal() ? toward : found.getValue(HorizontalDirectionalBlock.FACING))
                        .setValue(TechSounds.WORKING, false);
                if (turned != found) {
                    level.setBlock(pos, turned, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
                manipulators.add(pos.immutable());
                if (level.getBlockEntity(pos) instanceof ManipulatorBlockEntity arm) {
                    arm.setController(worldPosition);
                }
            }
        }
    }

    private void setManipulatorsWorking(boolean working) {
        if (working == manipulatorsWorking || level == null) {
            return;
        }
        manipulatorsWorking = working;
        for (BlockPos pos : manipulators) {
            BlockState found = level.getBlockState(pos);
            if (found.getBlock() instanceof ManipulatorBlock && found.getValue(TechSounds.WORKING) != working) {
                level.setBlock(pos, found.setValue(TechSounds.WORKING, working), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Tells the clients what stands in the zone when that changes (the start, a new stage, the end). */
    private void syncPreview() {
        AssemblyRecipe recipe = stage >= 0 ? recipe(running) : null;
        ItemStack result = recipe == null ? ItemStack.EMPTY : recipe.result();
        int modelStage = recipe == null ? -1 : recipe.stages().get(stage).modelStage();
        ItemStack part = ItemStack.EMPTY;
        if (recipe != null && !recipe.stages().get(stage).materials().isEmpty()) {
            ItemStack[] options = recipe.stages().get(stage).materials().get(0).ingredient().getItems();
            part = options.length > 0 ? options[0].copyWithCount(1) : ItemStack.EMPTY;
        }
        if (modelStage != shownStage || !ItemStack.isSameItem(result, shownResult) || !ItemStack.isSameItem(part, shownPart)) {
            shownStage = modelStage;
            shownResult = result.copy();
            shownPart = part;
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    /** On the client: the part the grippers carry in this stage, or empty. */
    public ItemStack previewPart() {
        return previewPart;
    }

    /** On the client: the result of the recipe under assembly, or empty. */
    public ItemStack previewResult() {
        return previewResult;
    }

    /** On the client: the stage of the model under assembly (-1: nothing in the zone). */
    public int previewStage() {
        return previewStage;
    }

    /** A stage begins: its sound at the machine. */
    private void stageSound(AssemblyRecipe.Stage begun) {
        if (level == null) {
            return;
        }
        // 1.7.0, block I: the garrison's own sounds for each stage
        net.minecraft.sounds.SoundEvent sound = switch (begun.name()) {
            case "frame", "plating" -> com.projectrebirth.core.ModSounds.GARRISON_WELDING.get();
            case "actuators", "weapon" -> com.projectrebirth.core.ModSounds.GARRISON_MANIPULATOR.get();
            case "electronics" -> com.projectrebirth.core.ModSounds.GARRISON_ELECTRONICS.get();
            case "ai_core" -> com.projectrebirth.core.ModSounds.GARRISON_CORE_BOOT.get();
            case "calibration" -> com.projectrebirth.core.ModSounds.GARRISON_CALIBRATION.get();
            default -> com.projectrebirth.core.ModSounds.GARRISON_MANIPULATOR.get();
        };
        level.playSound(null, worldPosition, sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
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
        setManipulatorsWorking(state == STATE_WORKING);
        syncPreview();
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
            stageSound(next);
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
        stageSound(first);
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
                case AssemblyRecipe.KEEP_ANDROID -> {
                    // the body as it was, with the core and the module of the materials put into it
                    result = base.copyWithCount(1);
                    ItemStack aiCore = ItemStack.EMPTY, module = ItemStack.EMPTY;
                    for (ItemStack used : consumed) {
                        if (used.getItem() instanceof com.projectrebirth.garrison.AiCoreItem && aiCore.isEmpty()) {
                            aiCore = used.copyWithCount(1);
                        } else if ((used.is(com.projectrebirth.garrison.GarrisonRegistry.COMBAT_MODULE.get())
                                || used.is(com.projectrebirth.garrison.GarrisonRegistry.WORK_MODULE.get())) && module.isEmpty()) {
                            module = used.copyWithCount(1);
                        }
                    }
                    result.set(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get(),
                            new com.projectrebirth.garrison.GarrisonRegistry.AndroidParts(aiCore, module));
                    result.remove(com.projectrebirth.garrison.GarrisonRegistry.PACKED_MACHINE.get());
                }
                case AssemblyRecipe.KEEP_STRIP_ANDROID -> {
                    result = base.copyWithCount(1);
                    var parts = result.remove(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get());
                    result.remove(com.projectrebirth.garrison.GarrisonRegistry.PACKED_MACHINE.get());
                    if (parts != null) {
                        left.add(parts.core().copy());
                        if (!parts.module().isEmpty()) {
                            left.add(parts.module().copy());
                        }
                    }
                }
                default -> {
                }
            }
        }
        if (level != null) {
            level.playSound(null, worldPosition, com.projectrebirth.core.ModSounds.GARRISON_ASSEMBLY_DONE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        // 1.7.0, block G: a machine built here belongs to the owner of its AI core
        com.projectrebirth.garrison.PackedMachineItem.bindToCore(result, consumed);
        ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
        if (rollOut(result)) {
            // it left the dock on its own
        } else if (output.isEmpty()) {
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

    /**
     * What a plan with an exit (the heavy dock) builds is not put into the output slot: it stands where it was assembled, facing
     * the gate, and rolls out by itself. Power armor and (1.7.1) machines roll out as they are, a turret in its crate. Returns false
     * when it cannot (no exit, nothing that rolls, no room, over a limit).
     */
    private boolean rollOut(ItemStack result) {
        MultiblockSchema schema = schema();
        if (!(level instanceof ServerLevel server) || schema == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 exit = schema.exitPoint(worldPosition, facing());
        if (exit == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 at = schema.displayPoint(worldPosition, facing());
        if (result.getItem() instanceof com.projectrebirth.garrison.TurretItem) {
            return com.projectrebirth.garrison.TurretCrateEntity.rollOut(server, result, at, exit, facing().toYRot(), owner) != null;
        }
        if (result.getItem() instanceof com.projectrebirth.garrison.PackedMachineItem) {
            return com.projectrebirth.garrison.PackedMachineItem.deployFromDock(server, result, at, exit, facing().toYRot(), owner) != null;
        }
        if (!(result.getItem() instanceof com.projectrebirth.powerarmor.PowerArmorItem armorItem)) {
            return false;
        }
        com.projectrebirth.powerarmor.PowerArmorEntity armor = com.projectrebirth.powerarmor.PowerArmorRegistry.POWER_ARMOR.get().create(server);
        if (armor == null) {
            return false;
        }
        float yaw = facing().toYRot();
        armor.setVariant(armorItem.variant());
        armor.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        armor.setYHeadRot(yaw);
        armor.setYBodyRot(yaw);
        if (!server.noCollision(armor)) {
            return false;
        }
        if (owner != null) {
            armor.setOwner(owner);
        }
        armor.setUpgrades(Upgrades.ofStack(result));
        server.addFreshEntity(armor);
        armor.setIntegrity(armor.getMaxStructural(), armor.getMaxCritical());
        armor.rollOutTo(exit);
        return true;
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
        // the hall goes with its controller: the hidden frames show again
        for (BlockPos pos : hidden) {
            setFormed(pos, false);
        }
        hidden.clear();
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
        tag.putLongArray("hidden", hidden.stream().mapToLong(BlockPos::asLong).toArray());
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
        hidden.clear();
        for (long packed : tag.getLongArray("hidden")) {
            hidden.add(BlockPos.of(packed));
        }
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
    public net.minecraft.world.phys.AABB renderBounds() {
        MultiblockSchema schema = schema();
        if (schema == null) {
            return new net.minecraft.world.phys.AABB(worldPosition);
        }
        int reach = Math.max(schema.width(), Math.max(schema.depth(), schema.height()));
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(reach);
    }


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
