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
    // 1.7.0, block I: the garrison
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_WELDING = register("garrison.welding");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_MANIPULATOR = register("garrison.manipulator");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_ELECTRONICS = register("garrison.electronics");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_CALIBRATION = register("garrison.calibration");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_CORE_BOOT = register("garrison.core_boot");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_ASSEMBLY_DONE = register("garrison.assembly_done");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_MACHINE_BEEP = register("garrison.machine_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_TARGET_LOCK = register("garrison.target_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_MACHINE_SHUTDOWN = register("garrison.machine_shutdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARRISON_CONSOLE_ALARM = register("garrison.console_alarm");
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
    public static final DeferredHolder<SoundEvent, SoundEvent> ENVIRONMENT_SIREN = register("environment.siren");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENVIRONMENT_ACID_HISS = register("environment.acid_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOSIMETER_CLICK = register("environment.dosimeter_click");
    // 1.6.0, block G
    public static final DeferredHolder<SoundEvent, SoundEvent> SOURCE_HUM = register("scouting.source_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANALYZER_BEEP = register("scouting.analyzer_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAP_PRINT = register("scouting.map_print");
    public static final DeferredHolder<SoundEvent, SoundEvent> FILTRATION_HUM = register("shelter.filtration_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRLOCK_HISS = register("shelter.airlock_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> DECONTAMINATION_SHOWER = register("shelter.decontamination_shower");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAK_ALARM = register("shelter.leak_alarm");
    // 1.5.0: technical limits
    public static final DeferredHolder<SoundEvent, SoundEvent> DUST_WIND = register("power.dust_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMI_CRACKLE = register("power.emi_crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMI_FAULT = register("power.emi_fault");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOW_CHARGE = register("power.low_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> OVERHEAT_ALARM = register("power.overheat_alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGING_HUM = register("power.charging_hum");
    // 1.8.0, block A: interface sounds (played only on the client, see client/ui/UiSounds)
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = register("ui.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_HOVER = register("ui.hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_TOGGLE = register("ui.toggle");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_ERROR = register("ui.error");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_WORK_START = register("ui.work_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_WORK_END = register("ui.work_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_ALARM = register("ui.alarm");

    /** 1.9.0, block D: the new mutants (synthesized, assets_src/sounds/mutants). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CRAWLER_CHITTER = register("mutant.crawler_chitter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAMER_SCREAM = register("mutant.screamer_scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAMER_MOAN = register("mutant.screamer_moan");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAT_SQUEAK = register("mutant.rat_squeak");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUNTER_GROWL = register("mutant.hunter_growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNER_GURGLE = register("mutant.drowner_gurgle");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ProjectRebirth.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
