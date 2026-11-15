package com.projectrebirth.hostile;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Hostile mechanisms and creatures of 1.6.0 (part B3). They do not spawn naturally: the ruins place them. */
public final class HostileRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<GuardTurretEntity>> GUARD_TURRET = ENTITY_TYPES.register("guard_turret",
            () -> EntityType.Builder.of(GuardTurretEntity::new, MobCategory.MONSTER)
                    .sized(0.95f, 1.9f)
                    .eyeHeight(1.5f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("guard_turret"));

    public static final DeferredHolder<EntityType<?>, EntityType<PatrolRobotEntity>> PATROL_ROBOT = ENTITY_TYPES.register("patrol_robot",
            () -> EntityType.Builder.of(PatrolRobotEntity::new, MobCategory.MONSTER)
                    .sized(0.85f, 2.1f)
                    .eyeHeight(1.8f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("patrol_robot"));

    public static final DeferredHolder<EntityType<?>, EntityType<MechEntity>> MECH = ENTITY_TYPES.register("mech",
            () -> EntityType.Builder.of(MechEntity::new, MobCategory.MONSTER)
                    .sized(2.2f, 3.4f)
                    .eyeHeight(2.6f)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build("mech"));

    public static final DeferredHolder<EntityType<?>, EntityType<MechRocketEntity>> MECH_ROCKET = ENTITY_TYPES.register("mech_rocket",
            () -> EntityType.Builder.<MechRocketEntity>of(MechRocketEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .build("mech_rocket"));

    /** For trying it out in creative; the ruins place their own. */
    public static final DeferredItem<Item> GUARD_TURRET_SPAWN_EGG = ITEMS.register("guard_turret_spawn_egg",
            () -> new DeferredSpawnEggItem(GUARD_TURRET, 0xA3ACB6, 0xC4382C, new Item.Properties()));

    public static final DeferredItem<Item> PATROL_ROBOT_SPAWN_EGG = ITEMS.register("patrol_robot_spawn_egg",
            () -> new DeferredSpawnEggItem(PATROL_ROBOT, 0x6E7680, 0xE0A030, new Item.Properties()));

    public static final DeferredItem<Item> MECH_SPAWN_EGG = ITEMS.register("mech_spawn_egg",
            () -> new DeferredSpawnEggItem(MECH, 0x4F5A4A, 0xC4382C, new Item.Properties()));

    private HostileRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        TurretShotPayload.register(modEventBus);
        modEventBus.addListener(HostileRegistry::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(GUARD_TURRET.get(), GuardTurretEntity.createAttributes().build());
        event.put(PATROL_ROBOT.get(), PatrolRobotEntity.createAttributes().build());
        event.put(MECH.get(), MechEntity.createAttributes().build());
    }
}
