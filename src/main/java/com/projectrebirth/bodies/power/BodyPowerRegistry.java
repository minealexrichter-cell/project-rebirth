package com.projectrebirth.bodies.power;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Charge of artificial bodies (version 1.5.0, block A): where it is kept, and the pad and battery that refill it. */
public final class BodyPowerRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);

    /** Charge of a packed body, in FE. Absent on a new (full) body. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> STORED_CHARGE = COMPONENTS.registerComponentType(
            "stored_charge", builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    /** Energy in a portable body battery, in FE. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BATTERY_ENERGY = COMPONENTS.registerComponentType(
            "battery_energy", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Charge of the body a player controls, in FE; below 0 = not known yet (a world from 1.4.1), which counts as full.
     * Synced only to the player, for the HUD and the client's own limits (jetpack, running). Not copied on death.
     */
    public static final Supplier<AttachmentType<Float>> CONTROLLED_CHARGE = ATTACHMENTS.register("controlled_body_charge",
            () -> AttachmentType.builder(() -> -1.0f)
                    .serialize(Codec.FLOAT, charge -> charge >= 0.0f)
                    .sync((holder, to) -> holder == to && to.connection.hasChannel(SyncAttachmentsPayload.TYPE), ByteBufCodecs.FLOAT)
                    .build());

    /** Heat (0-100) of the body a player controls; synced only to the player. Not copied on death. */
    public static final Supplier<AttachmentType<Float>> CONTROLLED_HEAT = ATTACHMENTS.register("controlled_body_heat",
            () -> AttachmentType.builder(() -> 0.0f)
                    .serialize(Codec.FLOAT, heat -> heat > 0.0f)
                    .sync((holder, to) -> holder == to && to.connection.hasChannel(SyncAttachmentsPayload.TYPE), ByteBufCodecs.FLOAT)
                    .build());

    public static final DeferredBlock<ChargingPadBlock> CHARGING_PAD = BLOCKS.registerBlock("charging_pad", ChargingPadBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(TechSounds.WORKING) ? 7 : 1)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> CHARGING_PAD_ITEM = ITEMS.registerSimpleBlockItem(CHARGING_PAD);
    public static final DeferredItem<BodyBatteryItem> BODY_BATTERY = ITEMS.register("body_battery",
            () -> new BodyBatteryItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingPadBlockEntity>> CHARGING_PAD_ENTITY =
            BLOCK_ENTITIES.register("charging_pad", () -> BlockEntityType.Builder.of(ChargingPadBlockEntity::new, CHARGING_PAD.get()).build(null));

    private BodyPowerRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(BodyPowerRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CHARGING_PAD_ENTITY.get(), (pad, side) -> pad.energy().inputOnly());
        // Other mods' chargers can fill the battery; only the body itself takes energy out of it (see BodyBatteryItem).
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> new ComponentEnergyStorage(stack, BATTERY_ENERGY.get(),
                RebirthConfig.BODY_BATTERY_CAPACITY.get(), RebirthConfig.BODY_BATTERY_RECEIVE.get(), 0), BODY_BATTERY.get());
    }
}
