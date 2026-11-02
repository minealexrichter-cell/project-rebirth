package com.projectrebirth.bodies;

import java.util.EnumMap;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BodyEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final Map<BodyClass, DeferredHolder<EntityType<?>, EntityType<ArtificialBodyEntity>>> TYPES = new EnumMap<>(BodyClass.class);

    static {
        for (BodyClass bodyClass : BodyClass.values()) {
            TYPES.put(bodyClass, ENTITY_TYPES.register(bodyClass.id(), () -> EntityType.Builder
                    .<ArtificialBodyEntity>of((type, level) -> new ArtificialBodyEntity(type, level, bodyClass), MobCategory.MISC)
                    .sized(0.6f, 2.0f)
                    .clientTrackingRange(10)
                    .build(bodyClass.id())));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<HumanShellEntity>> HUMAN_SHELL = ENTITY_TYPES.register("human_shell",
            () -> EntityType.Builder.of(HumanShellEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build("human_shell"));

    private BodyEntities() {
    }

    public static EntityType<ArtificialBodyEntity> type(BodyClass bodyClass) {
        return TYPES.get(bodyClass).get();
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(BodyEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        for (BodyClass bodyClass : BodyClass.values()) {
            event.put(type(bodyClass), ArtificialBodyEntity.createAttributes().build());
        }
        event.put(HUMAN_SHELL.get(), HumanShellEntity.createAttributes().build());
    }
}
