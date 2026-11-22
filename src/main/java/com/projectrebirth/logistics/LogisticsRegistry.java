package com.projectrebirth.logistics;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 1.8.0, block J: the logistics of Project: Rebirth — for now the storage, a multiblock with one big store for a faction that
 * work androids hand in to and take tools from. In 1.11 "Logistics" the managing androids are built on it.
 */
public final class LogisticsRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** The plan of the storage (data/projectrebirth/projectrebirth/multiblock/storage.json). */
    public static final net.minecraft.resources.ResourceLocation SCHEMA = ProjectRebirth.id("storage");

    public static final DeferredBlock<StorageControllerBlock> STORAGE_CONTROLLER = BLOCKS.register("storage_controller",
            () -> new StorageControllerBlock(machine()));
    /** The racks of the storage: shelves of crates in a steel frame; hidden under the hall of a whole storage (a frame of it). */
    public static final DeferredBlock<com.projectrebirth.multiblock.FrameBlock> STORAGE_RACK = BLOCKS.register("storage_rack",
            () -> new com.projectrebirth.multiblock.FrameBlock(machine().noOcclusion()));
    /** The roof panels: carbon, flat until the storage is whole, then the hip roof. */
    public static final DeferredBlock<StorageRoofBlock> STORAGE_ROOF = BLOCKS.register("storage_roof",
            () -> new StorageRoofBlock(machine().noOcclusion()));
    public static final DeferredItem<BlockItem> STORAGE_ROOF_ITEM = ITEMS.registerSimpleBlockItem(STORAGE_ROOF);
    public static final DeferredItem<BlockItem> STORAGE_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(STORAGE_CONTROLLER);
    public static final DeferredItem<BlockItem> STORAGE_RACK_ITEM = ITEMS.registerSimpleBlockItem(STORAGE_RACK);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageControllerBlockEntity>> STORAGE_ENTITY =
            BLOCK_ENTITIES.register("storage_controller", () -> BlockEntityType.Builder.of(StorageControllerBlockEntity::new, STORAGE_CONTROLLER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<StorageMenu>> STORAGE_MENU =
            MENUS.register("storage", () -> IMenuTypeExtension.create(StorageMenu::new));

    private LogisticsRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(LogisticsRegistry::registerCapabilities);
        modEventBus.addListener(LogisticsRegistry::registerPayloads);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // the controller itself is a chest to whoever stands at it (work androids, hoppers), while the construction is whole
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STORAGE_ENTITY.get(), (storage, side) -> storage.portItems());
    }

    /** What the window asks: show the store filtered by that text, sorted so, from that row on. */
    public record ViewPayload(int containerId, String query, int sort, int row) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(ProjectRebirth.id("storage_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ViewPayload::containerId, ByteBufCodecs.stringUtf8(64), ViewPayload::query,
                ByteBufCodecs.VAR_INT, ViewPayload::sort, ByteBufCodecs.VAR_INT, ViewPayload::row, ViewPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ViewPayload.TYPE, ViewPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof StorageMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.view(payload.query(), payload.sort(), payload.row());
            }
        });
    }
}
