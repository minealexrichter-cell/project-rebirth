package com.projectrebirth.environment;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
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
        modEventBus.addListener(EnvironmentRegistry::registerDataRegistries);
    }

    private static void registerDataRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(PROFILES, EnvironmentProfile.CODEC);
        event.dataPackRegistry(DIMENSIONS, DimensionRules.CODEC);
    }
}
