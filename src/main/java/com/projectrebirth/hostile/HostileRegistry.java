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

    public static final DeferredHolder<EntityType<?>, EntityType<MutatedHoundEntity>> MUTATED_HOUND = ENTITY_TYPES.register("mutated_hound",
            () -> EntityType.Builder.of(MutatedHoundEntity::new, MobCategory.MONSTER).sized(0.7f, 0.85f).eyeHeight(0.7f).clientTrackingRange(10).build("mutated_hound"));

    public static final DeferredHolder<EntityType<?>, EntityType<GlowingGhoulEntity>> GLOWING_GHOUL = ENTITY_TYPES.register("glowing_ghoul",
            () -> EntityType.Builder.of(GlowingGhoulEntity::new, MobCategory.MONSTER).sized(0.6f, 1.9f).eyeHeight(1.7f).clientTrackingRange(10).build("glowing_ghoul"));

    public static final DeferredHolder<EntityType<?>, EntityType<BruteEntity>> BRUTE = ENTITY_TYPES.register("brute",
            () -> EntityType.Builder.of(BruteEntity::new, MobCategory.MONSTER).sized(1.3f, 2.5f).eyeHeight(2.2f).clientTrackingRange(10).build("brute"));

    public static final DeferredHolder<EntityType<?>, EntityType<RemainsEntity>> REMAINS = ENTITY_TYPES.register("remains",
            () -> EntityType.Builder.<RemainsEntity>of(RemainsEntity::new, MobCategory.MISC).sized(1.5f, 0.8f).clientTrackingRange(10).build("remains"));

    public static final DeferredHolder<EntityType<?>, EntityType<ToxicSpitEntity>> TOXIC_SPIT = ENTITY_TYPES.register("toxic_spit",
            () -> EntityType.Builder.<ToxicSpitEntity>of(ToxicSpitEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(2).noSave().build("toxic_spit"));

    /** For trying it out in creative; the ruins place their own. */
    public static final DeferredItem<Item> GUARD_TURRET_SPAWN_EGG = ITEMS.register("guard_turret_spawn_egg",
            () -> new DeferredSpawnEggItem(GUARD_TURRET, 0xA3ACB6, 0xC4382C, new Item.Properties()));

    public static final DeferredItem<Item> PATROL_ROBOT_SPAWN_EGG = ITEMS.register("patrol_robot_spawn_egg",
            () -> new DeferredSpawnEggItem(PATROL_ROBOT, 0x6E7680, 0xE0A030, new Item.Properties()));

    public static final DeferredItem<Item> MECH_SPAWN_EGG = ITEMS.register("mech_spawn_egg",
            () -> new DeferredSpawnEggItem(MECH, 0x4F5A4A, 0xC4382C, new Item.Properties()));

    public static final DeferredItem<Item> MUTATED_HOUND_SPAWN_EGG = ITEMS.register("mutated_hound_spawn_egg",
            () -> new DeferredSpawnEggItem(MUTATED_HOUND, 0x6B4A3A, 0x9BC24A, new Item.Properties()));

    public static final DeferredItem<Item> GLOWING_GHOUL_SPAWN_EGG = ITEMS.register("glowing_ghoul_spawn_egg",
            () -> new DeferredSpawnEggItem(GLOWING_GHOUL, 0x7A8A6A, 0x7CFF4A, new Item.Properties()));

    public static final DeferredItem<Item> BRUTE_SPAWN_EGG = ITEMS.register("brute_spawn_egg",
            () -> new DeferredSpawnEggItem(BRUTE, 0x8A6A5A, 0x5A3A2A, new Item.Properties()));

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
        event.put(MUTATED_HOUND.get(), MutatedHoundEntity.createAttributes().build());
        event.put(GLOWING_GHOUL.get(), GlowingGhoulEntity.createAttributes().build());
        event.put(BRUTE.get(), BruteEntity.createAttributes().build());
    }
}
