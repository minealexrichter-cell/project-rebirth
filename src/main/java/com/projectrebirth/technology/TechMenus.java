package com.projectrebirth.technology;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TechMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> STATION =
            MENUS.register("station", () -> IMenuTypeExtension.create(StationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MaintenanceMenu>> MAINTENANCE =
            MENUS.register("maintenance_station", () -> IMenuTypeExtension.create(MaintenanceMenu::new));

    private TechMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
