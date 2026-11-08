package com.projectrebirth.upgrades;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.projectrebirth.ProjectRebirth;

/** Everything the upgrade system registers: the data registry of definitions, where upgrades are stored, and the items. */
public final class UpgradeRegistry {
    public static final ResourceKey<Registry<UpgradeDefinition>> UPGRADES = ResourceKey.createRegistryKey(ProjectRebirth.id("upgrade"));

    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, ProjectRebirth.MOD_ID);

    static {
        SERIALIZERS.register("installed_upgrades", () -> InstalledUpgrades.ENTITY_DATA);
    }

    /** The upgrades of a packed body or armor. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InstalledUpgrades>> INSTALLED = COMPONENTS.registerComponentType(
            "installed_upgrades", builder -> builder.persistent(InstalledUpgrades.CODEC).networkSynchronized(InstalledUpgrades.STREAM_CODEC));

    /**
     * The upgrades of the body a player controls: then the body entity does not exist, the player is the body.
     * Not copied on death: a destroyed body drops them (see TransferService).
     */
    public static final Supplier<AttachmentType<InstalledUpgrades>> CONTROLLED_BODY = ATTACHMENTS.register("controlled_body_upgrades",
            () -> AttachmentType.builder(() -> InstalledUpgrades.EMPTY)
                    .serialize(InstalledUpgrades.CODEC, upgrades -> !upgrades.isEmpty())
                    .sync((holder, to) -> to.connection.hasChannel(SyncAttachmentsPayload.TYPE), InstalledUpgrades.STREAM_CODEC)
                    .build());

    /** Ticks of thrust used of the jetpack's reserve; 0 = full. Synced only to the player, for the HUD and the client's lift. */
    public static final Supplier<AttachmentType<Float>> JETPACK_USED = ATTACHMENTS.register("jetpack_used",
            () -> AttachmentType.builder(() -> 0.0f)
                    .serialize(Codec.FLOAT, used -> used != 0.0f)
                    .sync((holder, to) -> holder == to && to.connection.hasChannel(SyncAttachmentsPayload.TYPE), ByteBufCodecs.FLOAT)
                    .build());

    private static final Map<String, DeferredItem<Item>> UPGRADE_ITEMS = new LinkedHashMap<>();

    public static final DeferredItem<Item> COPPER_PLATING = upgradeItem("copper_plating", 16);
    public static final DeferredItem<Item> IRON_PLATING = upgradeItem("iron_plating", 16);
    public static final DeferredItem<Item> TITANIUM_PLATING = upgradeItem("titanium_plating", 16);
    public static final DeferredItem<Item> ARMOR_COPPER_PLATING = upgradeItem("armor_copper_plating", 16);
    public static final DeferredItem<Item> ARMOR_IRON_PLATING = upgradeItem("armor_iron_plating", 16);
    public static final DeferredItem<Item> ARMOR_TITANIUM_PLATING = upgradeItem("armor_titanium_plating", 16);
    public static final DeferredItem<Item> SEALING_MODULE = upgradeItem("sealing_module", 1);
    public static final DeferredItem<Item> JETPACK = upgradeItem("jetpack", 1);
    public static final DeferredItem<Item> COMPRESSED_AIR_MODULE = upgradeItem("compressed_air_module", 1);
    public static final DeferredItem<Item> FLOODLIGHT = upgradeItem("floodlight", 1);
    public static final DeferredItem<Item> EXTRA_BATTERY = upgradeItem("extra_battery", 1);
    public static final DeferredItem<Item> COOLING_MODULE = upgradeItem("cooling_module", 1);

    private UpgradeRegistry() {
    }

    private static DeferredItem<Item> upgradeItem(String id, int stackSize) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(stackSize));
        UPGRADE_ITEMS.put(id, item);
        return item;
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        SERIALIZERS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(UpgradeRegistry::registerDataRegistry);
    }

    private static void registerDataRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Also sent to clients: their windows will show what fits where.
        event.dataPackRegistry(UPGRADES, UpgradeDefinition.CODEC, UpgradeDefinition.CODEC);
    }
}
