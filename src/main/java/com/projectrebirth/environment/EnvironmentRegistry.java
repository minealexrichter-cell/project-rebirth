package com.projectrebirth.environment;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The registrations of the environment. Its data tables stay on the server: the server decides everything. */
public final class EnvironmentRegistry {
    public static final ResourceKey<Registry<EnvironmentProfile>> PROFILES = ResourceKey.createRegistryKey(ProjectRebirth.id("environment"));
    public static final ResourceKey<Registry<DimensionRules>> DIMENSIONS = ResourceKey.createRegistryKey(ProjectRebirth.id("environment_dimension"));

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);

    /** What the environment was at a player's place at the last calculation. Not saved: it is counted anew within a second. */
    public static final Supplier<AttachmentType<Exposure>> LAST_EXPOSURE = ATTACHMENTS.register("last_exposure",
            () -> AttachmentType.builder(() -> Exposure.NONE).build());

    /** The dose and the toxic load of a person; saved with the player. */
    public static final Supplier<AttachmentType<Biology>> BIOLOGY = ATTACHMENTS.register("biology",
            () -> AttachmentType.builder(() -> Biology.CLEAN).serialize(Biology.CODEC, biology -> !biology.equals(Biology.CLEAN)).build());

    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> RADIATION_SICKNESS = EFFECTS.register("radiation_sickness", () -> new SymptomEffect(0x8fd14f));
    public static final DeferredHolder<MobEffect, MobEffect> TOXIC_POISONING = EFFECTS.register("toxic_poisoning", () -> new SymptomEffect(0xb0a030));

    public static final DeferredItem<MedicineItem> ANTIRADIN = ITEMS.register("antiradin", () -> new MedicineItem(new Item.Properties().stacksTo(16),
            player -> {
                Biology old = HumanBody.biology(player);
                HumanBody.setBiology(player, new Biology(old.dose() - RebirthConfig.HUMAN_ANTIRADIN_DOSE.get().floatValue(), old.toxin()));
            }));
    public static final DeferredItem<MedicineItem> SORBENT = ITEMS.register("sorbent", () -> new MedicineItem(new Item.Properties().stacksTo(16),
            player -> {
                Biology old = HumanBody.biology(player);
                HumanBody.setBiology(player, new Biology(old.dose(), old.toxin() - RebirthConfig.HUMAN_SORBENT_TOXIN.get().floatValue()));
            }));

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);
    /** Seconds' worth of filter left, in a filter or in the head piece it was put into. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> FILTER_LIFE = COMPONENTS.registerComponentType("filter_life",
            builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, ProjectRebirth.MOD_ID);
    /** Placeholders until block G: the look of leather armor in the colour of the item. */
    private static final List<ArmorMaterial.Layer> TINTED = List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("leather"), "", true));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RESPIRATOR_MATERIAL = MATERIALS.register("respirator", () -> new ArmorMaterial(
            defense(1, 0, 0, 0), 5, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.STRING), TINTED, 0.0f, 0.0f));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HAZMAT_MATERIAL = MATERIALS.register("hazmat", () -> new ArmorMaterial(
            defense(1, 2, 2, 1), 8, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.SLIME_BALL), TINTED, 0.0f, 0.0f));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HEAVY_SUIT_MATERIAL = MATERIALS.register("heavy_suit", () -> new ArmorMaterial(
            defense(2, 5, 4, 2), 6, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT), TINTED, 1.0f, 0.05f));

    private static java.util.Map<ArmorItem.Type, Integer> defense(int head, int chest, int legs, int feet) {
        java.util.Map<ArmorItem.Type, Integer> map = new java.util.EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.HELMET, head);
        map.put(ArmorItem.Type.CHESTPLATE, chest);
        map.put(ArmorItem.Type.LEGGINGS, legs);
        map.put(ArmorItem.Type.BOOTS, feet);
        map.put(ArmorItem.Type.BODY, chest);
        return map;
    }

    public static final List<DeferredItem<? extends Item>> TINTED_GEAR = new java.util.ArrayList<>();

    private static DeferredItem<ProtectiveArmorItem> gear(String id, Holder<ArmorMaterial> material, ArmorItem.Type type, int durability, int color) {
        DeferredItem<ProtectiveArmorItem> item = ITEMS.register(id, () -> new ProtectiveArmorItem(material, type, new Item.Properties()
                .durability(type.getDurability(durability)).component(DataComponents.DYED_COLOR, new DyedItemColor(color, false))));
        TINTED_GEAR.add(item);
        return item;
    }

    public static final DeferredItem<ProtectiveArmorItem> RESPIRATOR = gear("respirator", RESPIRATOR_MATERIAL, ArmorItem.Type.HELMET, 10, 0x3A3F46);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_HELMET = gear("hazmat_helmet", HAZMAT_MATERIAL, ArmorItem.Type.HELMET, 10, 0xE0B33A);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_SUIT = gear("hazmat_suit", HAZMAT_MATERIAL, ArmorItem.Type.CHESTPLATE, 10, 0xE0B33A);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_TROUSERS = gear("hazmat_trousers", HAZMAT_MATERIAL, ArmorItem.Type.LEGGINGS, 10, 0xE0B33A);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_BOOTS = gear("hazmat_boots", HAZMAT_MATERIAL, ArmorItem.Type.BOOTS, 10, 0xE0B33A);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT_HELMET = gear("heavy_suit_helmet", HEAVY_SUIT_MATERIAL, ArmorItem.Type.HELMET, 25, 0x5F6B3E);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT = gear("heavy_suit", HEAVY_SUIT_MATERIAL, ArmorItem.Type.CHESTPLATE, 25, 0x5F6B3E);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT_TROUSERS = gear("heavy_suit_trousers", HEAVY_SUIT_MATERIAL, ArmorItem.Type.LEGGINGS, 25, 0x5F6B3E);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT_BOOTS = gear("heavy_suit_boots", HEAVY_SUIT_MATERIAL, ArmorItem.Type.BOOTS, 25, 0x5F6B3E);
    public static final DeferredItem<FilterItem> FILTER = ITEMS.register("filter", () -> new FilterItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> DOSIMETER = ITEMS.registerSimpleItem("dosimeter", new Item.Properties().stacksTo(1));

    /**
     * Rules of a whole dimension, from data/&lt;namespace&gt;/projectrebirth/environment_dimension/*.json. A dimension without
     * such a file has no catastrophes, so that dimensions of other mods are left alone.
     *
     * @param dimension   the dimension
     * @param catastrophes whether catastrophes happen there at all
     */
    public record DimensionRules(ResourceKey<Level> dimension, boolean catastrophes) {
        public static final Codec<DimensionRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(DimensionRules::dimension),
                Codec.BOOL.optionalFieldOf("catastrophes", false).forGetter(DimensionRules::catastrophes)
        ).apply(instance, DimensionRules::new));
    }

    private EnvironmentRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        EFFECTS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        MATERIALS.register(modEventBus);
        EnvironmentStatusPayload.register(modEventBus);
        CatastrophePayload.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(EnvironmentRegistry::registerDataRegistries);
    }

    private static void registerDataRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(PROFILES, EnvironmentProfile.CODEC);
        event.dataPackRegistry(DIMENSIONS, DimensionRules.CODEC);
    }
}
