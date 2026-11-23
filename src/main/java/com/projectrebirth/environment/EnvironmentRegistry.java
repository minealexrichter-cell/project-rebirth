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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
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

    /** Whether this player was told once that the environment is hostile now; saved with the player, kept on death. */
    public static final Supplier<AttachmentType<Boolean>> NOTICE_SEEN = ATTACHMENTS.register("environment_notice_seen",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL, seen -> seen).copyOnDeath().build());

    /** Whether this player was told once (1.4.0) that a roof protects less now and a sealed room is needed; kept on death. */
    public static final Supplier<AttachmentType<Boolean>> SHELTER_NOTICE_SEEN = ATTACHMENTS.register("shelter_notice_seen",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL, seen -> seen).copyOnDeath().build());

    /** The dose and the toxic load of a person; saved with the player. */
    public static final Supplier<AttachmentType<Biology>> BIOLOGY = ATTACHMENTS.register("biology",
            () -> AttachmentType.builder(() -> Biology.CLEAN).serialize(Biology.CODEC, biology -> !biology.equals(Biology.CLEAN)).build());

    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> RADIATION_SICKNESS = EFFECTS.register("radiation_sickness", () -> new SymptomEffect(0x8fd14f));
    public static final DeferredHolder<MobEffect, MobEffect> TOXIC_POISONING = EFFECTS.register("toxic_poisoning", () -> new SymptomEffect(0xb0a030));
    /** 1.9.0: a wound infected by a mutant ({@link WoundInfection}). */
    public static final DeferredHolder<MobEffect, MobEffect> WOUND_INFECTION = EFFECTS.register("wound_infection", () -> new SymptomEffect(0x7a8a2a));

    public static final DeferredItem<MedicineItem> ANTIRADIN = ITEMS.register("antiradin", () -> new MedicineItem(new Item.Properties().stacksTo(16),
            player -> {
                Biology old = HumanBody.biology(player);
                HumanBody.setBiology(player, old.withDose(old.dose() - RebirthConfig.HUMAN_ANTIRADIN_DOSE.get().floatValue())
                        .withResidual(old.residual() - com.projectrebirth.config.WastelandConfig.DOSE_ANTIRADIN_RESIDUAL.get().floatValue()));
            }));
    public static final DeferredItem<MedicineItem> SORBENT = ITEMS.register("sorbent", () -> new MedicineItem(new Item.Properties().stacksTo(16),
            player -> {
                Biology old = HumanBody.biology(player);
                HumanBody.setBiology(player, old.withToxin(old.toxin() - RebirthConfig.HUMAN_SORBENT_TOXIN.get().floatValue()));
                WoundInfection.cure(player);
            }));

    /** 1.9.0, block C: for a while the body takes in toxins more slowly. */
    public static final DeferredHolder<MobEffect, MobEffect> TOXIN_RESISTANCE = EFFECTS.register("toxin_resistance",
            () -> new MobEffect(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, 0x9ccc65) {
            });
    /** 1.9.0, block C: antitoxin, of a toxic gland: [wasteland.mutant_loot] antitoxinSeconds of toxin resistance and a wound cured. */
    public static final DeferredItem<MedicineItem> ANTITOXIN = ITEMS.register("antitoxin", () -> new MedicineItem(new Item.Properties().stacksTo(16),
            player -> {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(TOXIN_RESISTANCE,
                        com.projectrebirth.config.WastelandConfig.ANTITOXIN_SECONDS.get() * 20, 0, false, true, true));
                WoundInfection.cure(player);
            }));

    /** 1.9.0: stops a wound infection and takes away a little of the toxic load. */
    public static final DeferredItem<MedicineItem> ANTISEPTIC = ITEMS.register("antiseptic", () -> new MedicineItem(new Item.Properties().stacksTo(16),
            player -> {
                Biology old = HumanBody.biology(player);
                HumanBody.setBiology(player, old.withToxin(old.toxin() - com.projectrebirth.config.WastelandConfig.ANTISEPTIC_TOXIN.get().floatValue()));
                WoundInfection.cure(player);
            }));

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);
    /** Seconds' worth of filter left, in a filter or in the head piece it was put into. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> FILTER_LIFE = COMPONENTS.registerComponentType("filter_life",
            builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    /** 1.9.0: food that carries toxins: the toxic load it gives when eaten ({@link ContaminatedFood}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> CONTAMINATED = COMPONENTS.registerComponentType("contaminated",
            builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, ProjectRebirth.MOD_ID);
    /** The worn look: textures/models/armor/&lt;id&gt;_layer_1.png and _layer_2.png (blockbench/tools/build_textures.js). */
    private static List<ArmorMaterial.Layer> look(String id) {
        return List.of(new ArmorMaterial.Layer(ProjectRebirth.id(id), "", false));
    }
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RESPIRATOR_MATERIAL = MATERIALS.register("respirator", () -> new ArmorMaterial(
            defense(1, 0, 0, 0), 5, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.STRING), look("respirator"), 0.0f, 0.0f));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HAZMAT_MATERIAL = MATERIALS.register("hazmat", () -> new ArmorMaterial(
            defense(1, 2, 2, 1), 8, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.SLIME_BALL), look("hazmat"), 0.0f, 0.0f));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HEAVY_SUIT_MATERIAL = MATERIALS.register("heavy_suit", () -> new ArmorMaterial(
            defense(2, 5, 4, 2), 6, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT), look("heavy_suit"), 1.0f, 0.05f));

    private static java.util.Map<ArmorItem.Type, Integer> defense(int head, int chest, int legs, int feet) {
        java.util.Map<ArmorItem.Type, Integer> map = new java.util.EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.HELMET, head);
        map.put(ArmorItem.Type.CHESTPLATE, chest);
        map.put(ArmorItem.Type.LEGGINGS, legs);
        map.put(ArmorItem.Type.BOOTS, feet);
        map.put(ArmorItem.Type.BODY, chest);
        return map;
    }

    private static DeferredItem<ProtectiveArmorItem> gear(String id, Holder<ArmorMaterial> material, ArmorItem.Type type, int durability) {
        return ITEMS.register(id, () -> new ProtectiveArmorItem(material, type, new Item.Properties().durability(type.getDurability(durability))));
    }

    public static final DeferredItem<ProtectiveArmorItem> RESPIRATOR = gear("respirator", RESPIRATOR_MATERIAL, ArmorItem.Type.HELMET, 10);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_HELMET = gear("hazmat_helmet", HAZMAT_MATERIAL, ArmorItem.Type.HELMET, 10);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_SUIT = gear("hazmat_suit", HAZMAT_MATERIAL, ArmorItem.Type.CHESTPLATE, 10);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_TROUSERS = gear("hazmat_trousers", HAZMAT_MATERIAL, ArmorItem.Type.LEGGINGS, 10);
    public static final DeferredItem<ProtectiveArmorItem> HAZMAT_BOOTS = gear("hazmat_boots", HAZMAT_MATERIAL, ArmorItem.Type.BOOTS, 10);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT_HELMET = gear("heavy_suit_helmet", HEAVY_SUIT_MATERIAL, ArmorItem.Type.HELMET, 25);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT = gear("heavy_suit", HEAVY_SUIT_MATERIAL, ArmorItem.Type.CHESTPLATE, 25);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT_TROUSERS = gear("heavy_suit_trousers", HEAVY_SUIT_MATERIAL, ArmorItem.Type.LEGGINGS, 25);
    public static final DeferredItem<ProtectiveArmorItem> HEAVY_SUIT_BOOTS = gear("heavy_suit_boots", HEAVY_SUIT_MATERIAL, ArmorItem.Type.BOOTS, 25);
    public static final DeferredItem<FilterItem> FILTER = ITEMS.register("filter", () -> new FilterItem(new Item.Properties().stacksTo(16)));
    /** 1.9.0, block C: a filter of toxic glands of mutants that lasts longer ([wasteland.mutant_loot] biofilterLife). */
    public static final DeferredItem<FilterItem> BIOFILTER = ITEMS.register("biofilter", () -> new FilterItem(new Item.Properties().stacksTo(16), 2.0f));
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
