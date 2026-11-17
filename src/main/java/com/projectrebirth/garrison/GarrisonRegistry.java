package com.projectrebirth.garrison;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The garrison's machines (1.7.0, block C): the AI core and its parts, the behaviour modules, the data a core and a packed machine
 * carry, the window of a machine's upgrades, and the stand-in machine of the automated tests (no item in the creative tab, no
 * recipe, hidden from JEI: it exists only for the tests of the machines' base, as the multiblock controller of block A).
 */
public final class GarrisonRegistry {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);

    /** The owner of an AI core: who it serves, their name and their faction's name when it was bound (for the tooltip). */
    public record CoreOwner(UUID id, String name, String faction) {
        public static final Codec<CoreOwner> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(CoreOwner::id),
                Codec.STRING.fieldOf("name").forGetter(CoreOwner::name),
                Codec.STRING.optionalFieldOf("faction", "").forGetter(CoreOwner::faction)).apply(i, CoreOwner::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, CoreOwner> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, CoreOwner::id, ByteBufCodecs.STRING_UTF8, CoreOwner::name, ByteBufCodecs.STRING_UTF8, CoreOwner::faction,
                CoreOwner::new);
    }

    /**
     * The AI core and the behaviour module put into an artificial body: that body is an android (the body workshop builds it in
     * block E). Kept on the packed body; the stations that work on bodies keep it.
     */
    public record AndroidParts(ItemStack core, ItemStack module) {
        public static final Codec<AndroidParts> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("core").forGetter(AndroidParts::core),
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("module", ItemStack.EMPTY).forGetter(AndroidParts::module)).apply(i, AndroidParts::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, AndroidParts> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, AndroidParts::core, ItemStack.OPTIONAL_STREAM_CODEC, AndroidParts::module, AndroidParts::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoreOwner>> CORE_OWNER = COMPONENTS.registerComponentType("core_owner",
            builder -> builder.persistent(CoreOwner.CODEC).networkSynchronized(CoreOwner.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MachineCore.Packed>> PACKED_MACHINE = COMPONENTS.registerComponentType("packed_machine",
            builder -> builder.persistent(MachineCore.Packed.CODEC).networkSynchronized(MachineCore.Packed.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AndroidParts>> ANDROID_PARTS = COMPONENTS.registerComponentType("android_parts",
            builder -> builder.persistent(AndroidParts.CODEC).networkSynchronized(AndroidParts.STREAM_CODEC));

    public static final DeferredItem<Item> NEURAL_MATRIX = ITEMS.registerSimpleItem("neural_matrix", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<AiCoreItem> AI_CORE = ITEMS.register("ai_core",
            () -> new AiCoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> DAMAGED_AI_CORE = ITEMS.registerSimpleItem("damaged_ai_core", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> COMBAT_MODULE = ITEMS.registerSimpleItem("combat_module", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> WORK_MODULE = ITEMS.registerSimpleItem("work_module", new Item.Properties().stacksTo(16));

    public static final DeferredHolder<EntityType<?>, EntityType<TestMachineEntity>> TEST_MACHINE = ENTITY_TYPES.register("test_machine",
            () -> EntityType.Builder.of(TestMachineEntity::new, MobCategory.MISC).sized(0.8f, 1.6f).clientTrackingRange(8).build("test_machine"));
    public static final DeferredItem<PackedMachineItem> TEST_MACHINE_ITEM = ITEMS.register("test_machine",
            () -> new PackedMachineItem(MachineKind.TEST, TEST_MACHINE::get, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TabletData>> TABLET_DATA = COMPONENTS.registerComponentType("tablet_data",
            builder -> builder.persistent(TabletData.CODEC).networkSynchronized(TabletData.STREAM_CODEC));

    /** Block D: the console and the tactical tablet. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<ConsoleBlock> CONSOLE = BLOCKS.register("console",
            () -> new ConsoleBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .strength(3.5f, 6.0f).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(com.projectrebirth.technology.TechSounds.WORKING) ? 7 : 0)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONSOLE_ITEM = ITEMS.registerSimpleBlockItem(CONSOLE);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<ConsoleBlockEntity>> CONSOLE_ENTITY =
            BLOCK_ENTITIES.register("console", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(ConsoleBlockEntity::new, CONSOLE.get()).build(null));
    public static final DeferredItem<TabletItem> TABLET = ITEMS.register("tactical_tablet", () -> new TabletItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<MenuType<?>, MenuType<ConsoleMenu>> CONSOLE_MENU =
            MENUS.register("console", () -> IMenuTypeExtension.create(ConsoleMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AndroidGearMenu>> ANDROID_GEAR =
            MENUS.register("android_gear", () -> IMenuTypeExtension.create(AndroidGearMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMountingMenu>> MACHINE_MOUNTING =
            MENUS.register("machine_mounting", () -> IMenuTypeExtension.create(MachineMountingMenu::new));

    /** Block G: the defence turrets, their mount, their parts and ammunition. */
    public static final DeferredItem<Item> ROCKET = ITEMS.registerSimpleItem("rocket", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> MACHINE_GUN_NODE = ITEMS.registerSimpleItem("machine_gun_node", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> ROCKET_LAUNCHER_NODE = ITEMS.registerSimpleItem("rocket_launcher_node", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> STABILIZER = ITEMS.registerSimpleItem("stabilizer", new Item.Properties().stacksTo(16));
    public static final net.neoforged.neoforge.registries.DeferredBlock<TurretMountBlock> TURRET_MOUNT = BLOCKS.register("turret_mount",
            () -> new TurretMountBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .strength(4.0f, 8.0f).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> TURRET_MOUNT_ITEM = ITEMS.registerSimpleBlockItem(TURRET_MOUNT);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<TurretMountBlockEntity>> TURRET_MOUNT_ENTITY =
            BLOCK_ENTITIES.register("turret_mount", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TurretMountBlockEntity::new, TURRET_MOUNT.get()).build(null));
    public static final DeferredHolder<EntityType<?>, EntityType<DefenceTurretEntity>> MACHINE_GUN_TURRET = turret(TurretType.MACHINE_GUN);
    public static final DeferredHolder<EntityType<?>, EntityType<DefenceTurretEntity>> ROCKET_TURRET = turret(TurretType.ROCKET);
    public static final DeferredItem<TurretItem> MACHINE_GUN_TURRET_ITEM = ITEMS.register("machine_gun_turret",
            () -> new TurretItem(TurretType.MACHINE_GUN, MACHINE_GUN_TURRET::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TurretItem> ROCKET_TURRET_ITEM = ITEMS.register("rocket_turret",
            () -> new TurretItem(TurretType.ROCKET, ROCKET_TURRET::get, new Item.Properties().stacksTo(1)));

    /** Block H: the guard mech and the service post. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GuardMechEntity.Loadout>> MECH_LOADOUT = COMPONENTS.registerComponentType("mech_loadout",
            builder -> builder.persistent(GuardMechEntity.Loadout.CODEC).networkSynchronized(GuardMechEntity.Loadout.STREAM_CODEC));
    public static final DeferredItem<Item> MECH_ARMOR_HULL = ITEMS.registerSimpleItem("mech_armor_hull", new Item.Properties().stacksTo(1));
    public static final DeferredHolder<EntityType<?>, EntityType<GuardMechEntity>> GUARD_MECH = ENTITY_TYPES.register("guard_mech",
            () -> EntityType.Builder.of(GuardMechEntity::new, MobCategory.MISC).sized(2.2f, 3.4f).eyeHeight(2.6f).fireImmune().clientTrackingRange(12).build("guard_mech"));
    public static final DeferredItem<PackedMachineItem> GUARD_MECH_ITEM = ITEMS.register("guard_mech",
            () -> new PackedMachineItem(MachineKind.MECH, GUARD_MECH::get, new Item.Properties().stacksTo(1)));
    public static final net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block> SERVICE_POST_PLATE = BLOCKS.registerSimpleBlock("service_post_plate",
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .strength(4.0f, 8.0f).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SERVICE_POST_PLATE_ITEM = ITEMS.registerSimpleBlockItem(SERVICE_POST_PLATE);
    public static final net.neoforged.neoforge.registries.DeferredBlock<ServicePostBlock> SERVICE_POST = BLOCKS.register("service_post",
            () -> new ServicePostBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .strength(4.0f, 8.0f).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SERVICE_POST_ITEM = ITEMS.registerSimpleBlockItem(SERVICE_POST);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<ServicePostBlockEntity>> SERVICE_POST_ENTITY =
            BLOCK_ENTITIES.register("service_post", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(ServicePostBlockEntity::new, SERVICE_POST.get()).build(null));

    private static DeferredHolder<EntityType<?>, EntityType<DefenceTurretEntity>> turret(TurretType turret) {
        return ENTITY_TYPES.register(turret.id(), () -> EntityType.Builder.<DefenceTurretEntity>of((type, level) -> new DefenceTurretEntity(type, level, turret),
                MobCategory.MISC).sized(0.95f, 1.9f).eyeHeight(1.5f).clientTrackingRange(10).build(turret.id()));
    }

    private GarrisonRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ConsoleNetwork.register(modEventBus);
        Androids.register(modEventBus);
        modEventBus.addListener(GarrisonRegistry::registerCapabilities);
        modEventBus.addListener(GarrisonRegistry::registerAttributes);
    }

    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, CONSOLE_ENTITY.get(), (console, side) -> console.energy().inputOnly());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, TURRET_MOUNT_ENTITY.get(), (mount, side) -> mount.energy().inputOnly());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, TURRET_MOUNT_ENTITY.get(), (mount, side) -> mount.ammo());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, SERVICE_POST_ENTITY.get(), (post, side) -> post.energy().inputOnly());
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TEST_MACHINE.get(), TestMachineEntity.createAttributes().build());
        event.put(MACHINE_GUN_TURRET.get(), DefenceTurretEntity.createAttributes().build());
        event.put(ROCKET_TURRET.get(), DefenceTurretEntity.createAttributes().build());
        event.put(GUARD_MECH.get(), GuardMechEntity.createAttributes().build());
    }
}
