package com.projectrebirth.powerarmor;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything the power armor registers: the armor entity, its items and item data. */
public final class PowerArmorRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PowerArmorEntity>> POWER_ARMOR = ENTITY_TYPES.register("power_armor",
            () -> EntityType.Builder.of(PowerArmorEntity::new, MobCategory.MISC)
                    .sized(1.25f, 2.5f)
                    .clientTrackingRange(10)
                    .build("power_armor"));

    /** Integrities of a packed-up armor. Absent on a freshly crafted one (full integrity). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> STORED_STRUCTURAL = COMPONENTS.registerComponentType(
            "stored_structural", builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> STORED_CRITICAL = COMPONENTS.registerComponentType(
            "stored_critical", builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    /** One item per armor variant; the combat armor keeps the 0.1.0 id "power_armor". */
    private static final Map<ArmorVariant, DeferredItem<PowerArmorItem>> ARMOR_ITEMS = new EnumMap<>(ArmorVariant.class);

    static {
        for (ArmorVariant variant : ArmorVariant.values()) {
            ARMOR_ITEMS.put(variant, ITEMS.register(variant.itemId(), () -> new PowerArmorItem(variant, new Item.Properties().stacksTo(1))));
        }
    }

    public static final DeferredItem<PowerArmorItem> POWER_ARMOR_ITEM = ARMOR_ITEMS.get(ArmorVariant.COMBAT);
    /** Repairs armor (iron ingots for structural, armor plates for critical) and dismantles wrecks. */
    public static final DeferredItem<Item> REPAIR_WRENCH = ITEMS.register("repair_wrench",
            () -> new Item(new Item.Properties().durability(256)));
    /** Opens the hatch of a shut-down armor from outside to free the pilot. */
    public static final DeferredItem<Item> RESCUE_TOOL = ITEMS.register("rescue_tool",
            () -> new Item(new Item.Properties().durability(32)));
    /** Material for critical integrity repairs and armor crafting. */
    public static final DeferredItem<Item> ARMOR_PLATE = ITEMS.registerSimpleItem("armor_plate");

    private PowerArmorRegistry() {
    }

    public static PowerArmorItem item(ArmorVariant variant) {
        return ARMOR_ITEMS.get(variant).get();
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(PowerArmorRegistry::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(POWER_ARMOR.get(), PowerArmorEntity.createAttributes().build());
    }
}
