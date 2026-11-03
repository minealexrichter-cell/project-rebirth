package com.projectrebirth.core;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.TechItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.projectrebirth"))
            .icon(() -> BodyItems.item(BodyClass.BASIC_ROBOT).getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (Metal metal : Metal.values()) {
                    output.accept(TechItems.ORES.get(metal).get());
                    output.accept(TechItems.DEEPSLATE_ORES.get(metal).get());
                }
                for (Metal metal : Metal.values()) {
                    output.accept(TechItems.RAW.get(metal).get());
                    output.accept(TechItems.INGOTS.get(metal).get());
                }
                output.accept(TechItems.BRONZE_INGOT.get());
                output.accept(TechItems.CIRCUIT_BOARD.get());
                output.accept(TechItems.ASSEMBLY_STATION.get());
                output.accept(BodyItems.TRANSFER_CAPSULE.get());
                for (BodyClass bodyClass : BodyClass.values()) {
                    output.accept(BodyItems.item(bodyClass));
                }
                output.accept(PowerArmorRegistry.POWER_ARMOR_ITEM.get());
                output.accept(PowerArmorRegistry.ARMOR_PLATE.get());
                output.accept(PowerArmorRegistry.REPAIR_WRENCH.get());
                output.accept(PowerArmorRegistry.RESCUE_TOOL.get());
            })
            .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
