package com.projectrebirth.bodies;

import com.mojang.serialization.Codec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BodyComponents {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);

    /** Integrity of a packed-up damaged body. Absent on undamaged (freshly crafted) bodies. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> STORED_INTEGRITY = COMPONENTS.registerComponentType(
            "stored_integrity", builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    private BodyComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
