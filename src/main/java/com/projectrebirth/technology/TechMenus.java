package com.projectrebirth.technology;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.ArmorMountingMenu;
import com.projectrebirth.technology.energy.GeneratorMenu;

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

    public static final DeferredHolder<MenuType<?>, MenuType<BodyModificationMenu>> BODY_MODIFICATION =
            MENUS.register("body_modification_station", () -> IMenuTypeExtension.create(BodyModificationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ArmorMountingMenu>> ARMOR_MOUNTING =
            MENUS.register("armor_mounting", () -> IMenuTypeExtension.create(ArmorMountingMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR =
            MENUS.register("combustion_generator", () -> IMenuTypeExtension.create(GeneratorMenu::new));

    /** 1.8.0: the window of the solar panel, the radioisotope generator and the batteries (numbers only, no slots). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.projectrebirth.technology.energy.EnergyPanelMenu>> ENERGY_PANEL =
            MENUS.register("energy_panel", () -> IMenuTypeExtension.create(com.projectrebirth.technology.energy.EnergyPanelMenu::new));

    private TechMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
