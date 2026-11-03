package com.projectrebirth.bodies;

import java.util.EnumMap;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BodyItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final Map<BodyClass, DeferredItem<ArtificialBodyItem>> BODY_ITEMS = new EnumMap<>(BodyClass.class);

    static {
        for (BodyClass bodyClass : BodyClass.values()) {
            BODY_ITEMS.put(bodyClass, ITEMS.register(bodyClass.id(),
                    () -> new ArtificialBodyItem(bodyClass, new Item.Properties().stacksTo(1))));
        }
    }

    public static final DeferredItem<DoubleHighBlockItem> TRANSFER_CAPSULE = ITEMS.register("transfer_capsule",
            () -> new DoubleHighBlockItem(BodyBlocks.TRANSFER_CAPSULE.get(), new Item.Properties()));

    public static final DeferredItem<RepairKitItem> REPAIR_KIT = ITEMS.register("repair_kit",
            () -> new RepairKitItem(new Item.Properties().stacksTo(16)));

    private BodyItems() {
    }

    public static ArtificialBodyItem item(BodyClass bodyClass) {
        return BODY_ITEMS.get(bodyClass).get();
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
