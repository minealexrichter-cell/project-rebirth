package com.projectrebirth.environment;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.Registry;
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

/** The data of the environment. The server decides everything, so none of this goes to clients. */
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
        ITEMS.register(modEventBus);
        modEventBus.addListener(EnvironmentRegistry::registerDataRegistries);
    }

    private static void registerDataRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(PROFILES, EnvironmentProfile.CODEC);
        event.dataPackRegistry(DIMENSIONS, DimensionRules.CODEC);
    }
}
