package com.projectrebirth.environment;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Temperature of version 1.5.0, block C: the stress of a human body, the thermal liner and the heater. */
public final class TemperatureRegistry {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ProjectRebirth.MOD_ID);

    /** Heat or cold stress of a human body: above 0 heat, below 0 cold, from -100 to 100. Saved; a new life starts at 0. */
    public static final Supplier<AttachmentType<Float>> THERMAL_STRESS = ATTACHMENTS.register("thermal_stress",
            () -> AttachmentType.builder(() -> 0.0f).serialize(Codec.FLOAT, stress -> stress != 0.0f).build());

    /** Electromagnetic interference a player feels now (block E, see Emi); counted once a second, synced only to the player, not saved. */
    public static final Supplier<AttachmentType<Float>> EMI_LEVEL = ATTACHMENTS.register("emi_level",
            () -> AttachmentType.builder(() -> 0.0f)
                    .sync((holder, to) -> holder == to && to.connection.hasChannel(net.neoforged.neoforge.network.payload.SyncAttachmentsPayload.TYPE),
                            net.minecraft.network.codec.ByteBufCodecs.FLOAT)
                    .build());

    public static final DeferredHolder<MobEffect, MobEffect> HEATSTROKE = EFFECTS.register("heatstroke", () -> new SymptomEffect(0xff7043));
    public static final DeferredHolder<MobEffect, MobEffect> HYPOTHERMIA = EFFECTS.register("hypothermia", () -> new SymptomEffect(0x81d4fa));

    /** A thermal liner sewn into a piece of armor or a suit. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> THERMAL_LINER_INSIDE = COMPONENTS.registerComponentType(
            "thermal_liner", builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredItem<Item> THERMAL_LINER = ITEMS.registerSimpleItem("thermal_liner", new Item.Properties().stacksTo(16));

    public static final DeferredBlock<HeaterBlock> HEATER = BLOCKS.registerBlock("heater", HeaterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(TechSounds.WORKING) ? 10 : 0)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> HEATER_ITEM = ITEMS.registerSimpleBlockItem(HEATER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeaterBlockEntity>> HEATER_ENTITY =
            BLOCK_ENTITIES.register("heater", () -> BlockEntityType.Builder.of(HeaterBlockEntity::new, HEATER.get()).build(null));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ThermalLinerRecipe>> LINER_RECIPE =
            SERIALIZERS.register("thermal_liner", () -> new SimpleCraftingRecipeSerializer<>(ThermalLinerRecipe::new));

    private TemperatureRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        EFFECTS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
        modEventBus.addListener(TemperatureRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HEATER_ENTITY.get(), (heater, side) -> heater.energy().inputOnly());
    }

    /** Whether the temperature system works now. */
    public static boolean enabled() {
        return RebirthConfig.TEMP_ENABLED.get() && RebirthConfig.ENVIRONMENT_ENABLED.get();
    }
}
