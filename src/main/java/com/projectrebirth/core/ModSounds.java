package com.projectrebirth.core;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's sound events. Which files they play, at what volume and pitch, and their subtitles are in
 * {@code assets/projectrebirth/sounds.json}; the files themselves and their sources are listed in docs/CREDITS.md.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_STEP = register("body.robot_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_HURT = register("body.robot_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_DESTROYED = register("body.robot_destroyed");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_STEP = register("armor.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_HURT = register("armor.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_SHUTDOWN = register("armor.shutdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_WRECKED = register("armor.wrecked");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_ENTER = register("armor.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_EXIT = register("armor.exit");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_WORKING = register("station.working");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_DONE = register("station.done");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_RUNNING = register("generator.running");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFER_ENTER = register("transfer.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFER_RETURN = register("transfer.return");

    public static final DeferredHolder<SoundEvent, SoundEvent> UPGRADE_INSTALL = register("upgrade.install");
    public static final DeferredHolder<SoundEvent, SoundEvent> UPGRADE_REMOVE = register("upgrade.remove");
    public static final DeferredHolder<SoundEvent, SoundEvent> JETPACK_THRUST = register("jetpack.thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOODLIGHT_ON = register("floodlight.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOODLIGHT_OFF = register("floodlight.off");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ProjectRebirth.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
