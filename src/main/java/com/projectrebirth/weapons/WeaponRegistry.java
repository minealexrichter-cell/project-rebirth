package com.projectrebirth.weapons;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Items, data and registries of the guns of power armor. */
public final class WeaponRegistry {
    public static final ResourceKey<Registry<WeaponDefinition>> WEAPONS = ResourceKey.createRegistryKey(ProjectRebirth.id("weapon"));
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectRebirth.id("bullet"));

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);

    /** Rounds in the magazine of a weapon; absent means empty. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MAGAZINE = COMPONENTS.registerComponentType(
            "magazine", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** What the player is doing with a weapon right now. Not saved: after a restart nobody holds a trigger. */
    public static final Supplier<AttachmentType<WeaponState>> STATE = ATTACHMENTS.register("weapon_state",
            () -> AttachmentType.builder(WeaponState::new).build());

    public static final DeferredItem<WeaponItem> ASSAULT_RIFLE = weapon("assault_rifle");
    public static final DeferredItem<WeaponItem> BATTLE_RIFLE = weapon("battle_rifle");
    public static final DeferredItem<WeaponItem> COMBAT_SHOTGUN = weapon("combat_shotgun");
    public static final DeferredItem<Item> ASSAULT_RIFLE_AMMO = ITEMS.registerSimpleItem("assault_rifle_ammo");
    public static final DeferredItem<Item> BATTLE_RIFLE_AMMO = ITEMS.registerSimpleItem("battle_rifle_ammo");
    public static final DeferredItem<Item> SHOTGUN_SHELL = ITEMS.registerSimpleItem("shotgun_shell");

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ProjectRebirth.MOD_ID);
    private static final Map<String, WeaponSounds> WEAPON_SOUNDS = new HashMap<>();

    static {
        // How far shots are heard, in blocks; what they play is in assets/projectrebirth/sounds.json.
        weaponSounds("assault_rifle", 96.0f);
        weaponSounds("battle_rifle", 128.0f);
        weaponSounds("combat_shotgun", 72.0f);
    }

    /** The three sounds of a weapon. */
    public record WeaponSounds(Supplier<SoundEvent> shot, Supplier<SoundEvent> reload, Supplier<SoundEvent> empty) {
    }

    private static void weaponSounds(String id, float shotRange) {
        String prefix = "weapon." + id + ".";
        WEAPON_SOUNDS.put(id, new WeaponSounds(
                SOUNDS.register(prefix + "shot", name -> SoundEvent.createFixedRangeEvent(name, shotRange)),
                SOUNDS.register(prefix + "reload", SoundEvent::createVariableRangeEvent),
                SOUNDS.register(prefix + "empty", SoundEvent::createVariableRangeEvent)));
    }

    /** The sounds of a weapon, or null for one the mod has no sounds for (added by a data pack). */
    public static WeaponSounds sounds(String id) {
        return WEAPON_SOUNDS.get(id);
    }

    private WeaponRegistry() {
    }

    private static DeferredItem<WeaponItem> weapon(String id) {
        return ITEMS.register(id, () -> new WeaponItem(new Item.Properties().stacksTo(1)));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        SOUNDS.register(modEventBus);
        WeaponInputPayload.register(modEventBus);
        WeaponShotPayload.register(modEventBus);
        modEventBus.addListener(WeaponRegistry::registerDataRegistry);
    }

    private static void registerDataRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Also sent to clients, as the upgrades are.
        event.dataPackRegistry(WEAPONS, WeaponDefinition.CODEC, WeaponDefinition.CODEC);
    }
}
