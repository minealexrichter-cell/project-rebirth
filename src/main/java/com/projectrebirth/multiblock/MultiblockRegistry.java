package com.projectrebirth.multiblock;

import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The multiblock engine (1.7.0, block A): the plans (a data pack registry, also sent to clients for the hologram, the Codex and
 * JEI), the assembly recipe, the generic controller of the tests and the two ports.
 */
public final class MultiblockRegistry {
    public static final ResourceKey<Registry<MultiblockSchema>> SCHEMAS = ResourceKey.createRegistryKey(ProjectRebirth.id("multiblock"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblyRecipe>> ASSEMBLY =
            RECIPE_TYPES.register("assembly", () -> RecipeType.simple(ProjectRebirth.id("assembly")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblyRecipe>> ASSEMBLY_SERIALIZER =
            SERIALIZERS.register("assembly", () -> new RecipeSerializer<>() {
                @Override
                public MapCodec<AssemblyRecipe> codec() {
                    return AssemblyRecipe.CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> streamCodec() {
                    return AssemblyRecipe.STREAM_CODEC;
                }
            });

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** The controller without a plan of its own: only the automated tests use it (no item, no recipe). */
    public static final DeferredBlock<MultiblockControllerBlock> CONTROLLER = BLOCKS.register("multiblock_controller",
            () -> new MultiblockControllerBlock(null, machine()));
    public static final DeferredBlock<PortBlock> ENERGY_PORT = BLOCKS.register("energy_port", () -> new PortBlock(PortBlock.ENERGY, machine()));
    public static final DeferredBlock<PortBlock> CARGO_PORT = BLOCKS.register("cargo_port", () -> new PortBlock(PortBlock.CARGO, machine()));
    public static final DeferredItem<BlockItem> ENERGY_PORT_ITEM = ITEMS.registerSimpleBlockItem(ENERGY_PORT);
    public static final DeferredItem<BlockItem> CARGO_PORT_ITEM = ITEMS.registerSimpleBlockItem(CARGO_PORT);

    // ---------------------------------------------------------------- block B: the body workshop and the heavy dock
    public static final ResourceLocation BODY_WORKSHOP = ProjectRebirth.id("body_workshop");
    public static final ResourceLocation HEAVY_DOCK = ProjectRebirth.id("heavy_dock");

    public static final DeferredBlock<net.minecraft.world.level.block.Block> ASSEMBLY_PLATE = BLOCKS.registerSimpleBlock("assembly_plate", machine());
    public static final DeferredBlock<net.minecraft.world.level.block.Block> ASSEMBLY_FRAME = BLOCKS.register("assembly_frame",
            () -> new net.minecraft.world.level.block.Block(machine().noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> HEAVY_FRAME = BLOCKS.register("heavy_frame",
            () -> new net.minecraft.world.level.block.Block(machine().strength(5.0f, 9.0f).noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CRANE_BEAM = BLOCKS.register("crane_beam",
            () -> new net.minecraft.world.level.block.Block(machine().noOcclusion()));
    public static final DeferredBlock<ManipulatorBlock> MANIPULATOR = BLOCKS.register("manipulator", () -> new ManipulatorBlock(machine().noOcclusion()));
    public static final DeferredBlock<MultiblockControllerBlock> WORKSHOP_CONTROLLER = BLOCKS.register("body_workshop_controller",
            () -> new MultiblockControllerBlock(BODY_WORKSHOP, machine().lightLevel(state -> state.getValue(com.projectrebirth.technology.TechSounds.WORKING) ? 10 : 0)));
    public static final DeferredBlock<MultiblockControllerBlock> DOCK_CONTROLLER = BLOCKS.register("heavy_dock_controller",
            () -> new MultiblockControllerBlock(HEAVY_DOCK, machine().lightLevel(state -> state.getValue(com.projectrebirth.technology.TechSounds.WORKING) ? 10 : 0)));
    public static final DeferredItem<BlockItem> ASSEMBLY_PLATE_ITEM = ITEMS.registerSimpleBlockItem(ASSEMBLY_PLATE);
    public static final DeferredItem<BlockItem> ASSEMBLY_FRAME_ITEM = ITEMS.registerSimpleBlockItem(ASSEMBLY_FRAME);
    public static final DeferredItem<BlockItem> HEAVY_FRAME_ITEM = ITEMS.registerSimpleBlockItem(HEAVY_FRAME);
    public static final DeferredItem<BlockItem> CRANE_BEAM_ITEM = ITEMS.registerSimpleBlockItem(CRANE_BEAM);
    public static final DeferredItem<BlockItem> MANIPULATOR_ITEM = ITEMS.registerSimpleBlockItem(MANIPULATOR);
    public static final DeferredItem<BlockItem> WORKSHOP_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(WORKSHOP_CONTROLLER);
    public static final DeferredItem<BlockItem> DOCK_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(DOCK_CONTROLLER);

    /** The new parts: the heavy actuator (assembly station), the power frame and the armor hulls (armor workstation). */
    public static final DeferredItem<net.minecraft.world.item.Item> HEAVY_ACTUATOR = ITEMS.registerSimpleItem("heavy_actuator");
    public static final DeferredItem<net.minecraft.world.item.Item> POWER_FRAME = ITEMS.registerSimpleItem("power_frame",
            new net.minecraft.world.item.Item.Properties().stacksTo(16));
    public static final DeferredItem<net.minecraft.world.item.Item> COMBAT_ARMOR_HULL = ITEMS.registerSimpleItem("combat_armor_hull",
            new net.minecraft.world.item.Item.Properties().stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.Item> MOBILE_ARMOR_HULL = ITEMS.registerSimpleItem("mobile_armor_hull",
            new net.minecraft.world.item.Item.Properties().stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.Item> WORKER_ARMOR_HULL = ITEMS.registerSimpleItem("worker_armor_hull",
            new net.minecraft.world.item.Item.Properties().stacksTo(1));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>> CONTROLLER_ENTITY =
            BLOCK_ENTITIES.register("multiblock_controller", () -> BlockEntityType.Builder.of(MultiblockControllerBlockEntity::new, CONTROLLER.get(),
                    WORKSHOP_CONTROLLER.get(), DOCK_CONTROLLER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManipulatorBlockEntity>> MANIPULATOR_ENTITY =
            BLOCK_ENTITIES.register("manipulator", () -> BlockEntityType.Builder.of(ManipulatorBlockEntity::new, MANIPULATOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortBlockEntity>> PORT_ENTITY =
            BLOCK_ENTITIES.register("multiblock_port", () -> BlockEntityType.Builder.of(PortBlockEntity::new, ENERGY_PORT.get(), CARGO_PORT.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<MultiblockMenu>> CONTROLLER_MENU =
            MENUS.register("multiblock_controller", () -> IMenuTypeExtension.create(MultiblockMenu::new));

    private MultiblockRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
        MultiblockStatusPayload.register(modEventBus);
        modEventBus.addListener(MultiblockRegistry::registerDataRegistry);
        modEventBus.addListener(MultiblockRegistry::registerCapabilities);
    }

    private static void registerDataRegistry(DataPackRegistryEvent.NewRegistry event) {
        // sent to clients too: the hologram, the Codex and JEI draw the plans
        event.dataPackRegistry(SCHEMAS, MultiblockSchema.CODEC, MultiblockSchema.CODEC);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PORT_ENTITY.get(), (port, side) -> {
            MultiblockControllerBlockEntity controller = port.getBlockState().getBlock() instanceof PortBlock block && block.kind().equals(PortBlock.ENERGY)
                    ? port.controller() : null;
            return controller == null ? null : controller.energy().inputOnly();
        });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PORT_ENTITY.get(), (port, side) -> {
            MultiblockControllerBlockEntity controller = port.getBlockState().getBlock() instanceof PortBlock block && block.kind().equals(PortBlock.CARGO)
                    ? port.controller() : null;
            return controller == null ? null : controller.automation();
        });
    }

    /** Whether the multiblocks work at all ([garrison] and [garrison.multiblocks]). */
    public static boolean enabled() {
        return !RebirthConfig.SERVER_SPEC.isLoaded() || RebirthConfig.GARRISON_ENABLED.get() && RebirthConfig.MULTIBLOCKS_ENABLED.get();
    }

    /** A plan by its name, or null if there is none or it is a plan of the tests outside them. */
    @Nullable
    public static MultiblockSchema schema(RegistryAccess access, ResourceLocation id) {
        MultiblockSchema schema = access.registry(SCHEMAS).map(registry -> registry.get(id)).orElse(null);
        return schema != null && schema.available() ? schema : null;
    }

    /** The recipes of a multiblock in display order; recipes are synced to clients, so both sides get the same list. */
    public static List<RecipeHolder<AssemblyRecipe>> recipesFor(Level level, ResourceLocation schema) {
        return level.getRecipeManager().getAllRecipesFor(ASSEMBLY.get()).stream()
                .filter(holder -> holder.value().multiblock().equals(schema) && holder.value().available())
                .sorted(Comparator.comparingInt((RecipeHolder<AssemblyRecipe> holder) -> holder.value().sort())
                        .thenComparing(holder -> holder.id().toString()))
                .toList();
    }

    /** All the available recipes of all multiblocks (for JEI). */
    public static List<RecipeHolder<AssemblyRecipe>> allRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ASSEMBLY.get()).stream().filter(holder -> holder.value().available())
                .sorted(Comparator.comparing((RecipeHolder<AssemblyRecipe> holder) -> holder.value().multiblock().toString())
                        .thenComparingInt(holder -> holder.value().sort()))
                .toList();
    }

}
