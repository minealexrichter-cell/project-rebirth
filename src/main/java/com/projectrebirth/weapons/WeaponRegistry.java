package com.projectrebirth.weapons;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
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

    private WeaponRegistry() {
    }

    private static DeferredItem<WeaponItem> weapon(String id) {
        return ITEMS.register(id, () -> new WeaponItem(new Item.Properties().stacksTo(1)));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        WeaponInputPayload.register(modEventBus);
        modEventBus.addListener(WeaponRegistry::registerDataRegistry);
    }

    private static void registerDataRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Also sent to clients, as the upgrades are.
        event.dataPackRegistry(WEAPONS, WeaponDefinition.CODEC, WeaponDefinition.CODEC);
    }
}
