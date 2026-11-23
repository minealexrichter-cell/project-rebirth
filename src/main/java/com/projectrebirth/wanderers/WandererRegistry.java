package com.projectrebirth.wanderers;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 1.9.0, block E: the wanderers: their four kinds, the trade window, and the data of their groups and offers. */
public final class WandererRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);

    private static DeferredHolder<EntityType<?>, EntityType<WandererEntity>> type(String id, float width, float height, float eyes) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder.<WandererEntity>of(WandererEntity::new, MobCategory.CREATURE)
                .sized(width, height).eyeHeight(eyes).clientTrackingRange(10).build(id));
    }

    public static final DeferredHolder<EntityType<?>, EntityType<WandererEntity>> ARMORED = type("wanderer_armored", 1.1f, 2.6f, 2.2f);
    public static final DeferredHolder<EntityType<?>, EntityType<WandererEntity>> ANDROID = type("wanderer_android", 0.6f, 1.8f, 1.6f);
    public static final DeferredHolder<EntityType<?>, EntityType<WandererEntity>> SURVIVOR = type("wanderer_survivor", 0.6f, 1.8f, 1.62f);
    public static final DeferredHolder<EntityType<?>, EntityType<WandererEntity>> ROBOT = type("wanderer_robot", 0.9f, 1.3f, 1.0f);

    public static final DeferredHolder<MenuType<?>, MenuType<WandererTradeMenu>> TRADE_MENU = MENUS.register("wanderer_trade",
            () -> IMenuTypeExtension.create(WandererTradeMenu::new));

    private static DeferredItem<Item> egg(String id, DeferredHolder<EntityType<?>, EntityType<WandererEntity>> type, int a, int b) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, a, b, new Item.Properties()));
    }

    /** For trying them out in creative: a lone wanderer, no group (it will make itself the head). */
    public static final DeferredItem<Item> ARMORED_SPAWN_EGG = egg("wanderer_armored", ARMORED, 0x5A6470, 0xC8A040);
    public static final DeferredItem<Item> ANDROID_SPAWN_EGG = egg("wanderer_android", ANDROID, 0x8A9098, 0x5A8AB0);
    public static final DeferredItem<Item> SURVIVOR_SPAWN_EGG = egg("wanderer_survivor", SURVIVOR, 0xB08E2A, 0x5A4A3A);
    public static final DeferredItem<Item> ROBOT_SPAWN_EGG = egg("wanderer_robot", ROBOT, 0x6E7A64, 0xD8CFB5);

    private WandererRegistry() {
    }

    public static EntityType<WandererEntity> type(WandererEntity.Kind kind) {
        return switch (kind) {
            case ARMORED -> ARMORED.get();
            case ANDROID -> ANDROID.get();
            case ROBOT -> ROBOT.get();
            default -> SURVIVOR.get();
        };
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(WandererRegistry::registerAttributes);
        modEventBus.addListener(WandererRegistry::registerData);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ARMORED.get(), WandererEntity.createAttributes(60.0, 12.0).build());
        event.put(ANDROID.get(), WandererEntity.createAttributes(30.0, 6.0).build());
        event.put(SURVIVOR.get(), WandererEntity.createAttributes(24.0, 0.0).build());
        event.put(ROBOT.get(), WandererEntity.createAttributes(30.0, 8.0).build());
    }

    private static void registerData(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(WandererData.GROUPS, WandererData.GroupSpec.CODEC);
        event.dataPackRegistry(WandererData.TRADES, WandererData.Trade.CODEC);
    }
}
