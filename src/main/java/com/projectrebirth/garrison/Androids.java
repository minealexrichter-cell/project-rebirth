package com.projectrebirth.garrison;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyComponents;
import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The androids of the five body classes (1.7.0, block E): their entity types, and setting one up from a packed body that carries
 * an AI core and a module ({@link GarrisonRegistry.AndroidParts}).
 */
public final class Androids {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final Map<BodyClass, DeferredHolder<EntityType<?>, EntityType<AndroidEntity>>> TYPES = new EnumMap<>(BodyClass.class);

    static {
        for (BodyClass bodyClass : BodyClass.values()) {
            String id = "android_" + bodyClass.id();
            TYPES.put(bodyClass, ENTITY_TYPES.register(id, () -> EntityType.Builder
                    .<AndroidEntity>of((type, level) -> new AndroidEntity(type, level, bodyClass), MobCategory.MISC)
                    .sized(0.6f, 2.0f)
                    .clientTrackingRange(10)
                    .build(id)));
        }
    }

    private Androids() {
    }

    public static EntityType<AndroidEntity> type(BodyClass bodyClass) {
        return TYPES.get(bodyClass).get();
    }

    static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(Androids::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        for (BodyClass bodyClass : BodyClass.values()) {
            event.put(type(bodyClass), AndroidEntity.createAttributes().build());
        }
    }

    public static boolean isAndroid(ItemStack stack) {
        return stack.has(GarrisonRegistry.ANDROID_PARTS.get());
    }

    /**
     * Sets an android up from its packed body (server): on land the player may use, within the limits of the core's owner (or of
     * the player for a core bound to nobody), by a player who may command that owner's machines. Returns it, or null after telling
     * the player why not.
     */
    @Nullable
    public static AndroidEntity place(ServerLevel level, ServerPlayer player, ItemStack stack, BodyClass bodyClass, BlockPos pos, float yaw) {
        if (!RebirthConfig.GARRISON_ENABLED.get() || !RebirthConfig.MACHINES_ENABLED.get()) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.machine.disabled"), true);
            return null;
        }
        if (!FactionService.mayUse(player, pos, FactionService.Use.CONTAINER)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.machine.foreign_land"), true);
            return null;
        }
        GarrisonRegistry.AndroidParts parts = stack.get(GarrisonRegistry.ANDROID_PARTS.get());
        AndroidEntity android = type(bodyClass).create(level);
        if (parts == null || android == null) {
            return null;
        }
        GarrisonRegistry.CoreOwner bound = parts.core().get(GarrisonRegistry.CORE_OWNER.get());
        MachineCore.Packed packed = stack.get(GarrisonRegistry.PACKED_MACHINE.get());
        UUID owner = packed != null && packed.owner().isPresent() ? packed.owner().get() : bound != null ? bound.id() : player.getUUID();
        String ownerName = packed != null && packed.owner().isPresent() ? packed.ownerName() : bound != null ? bound.name() : player.getGameProfile().getName();
        android.setUp(parts, owner, ownerName);
        if (!android.machineCore().mayManage(player)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.machine.not_yours"), true);
            return null;
        }
        Component limit = MachineRegistry.get(level.getServer()).limitProblem(level.getServer(), owner, MachineKind.ANDROID);
        if (limit != null) {
            player.displayClientMessage(limit, true);
            return null;
        }
        android.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        android.setYHeadRot(yaw);
        android.setYBodyRot(yaw);
        if (!level.noCollision(android)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.body.no_space"), true);
            return null;
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            android.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }
        android.setUpgrades(Upgrades.ofStack(stack));
        Float charge = stack.get(BodyPowerRegistry.STORED_CHARGE.get());
        if (charge != null) {
            android.setCharge(charge);
        }
        Float wear = stack.get(BodyPowerRegistry.STORED_WEAR.get());
        if (wear != null) {
            android.setWear(wear);
        }
        android.applyLimits();
        MachineCore core = android.machineCore();
        if (packed != null) {
            // the body integrity is the structural bar: the maintenance station mends it on the item like any body
            Float stored = stack.get(BodyComponents.STORED_INTEGRITY.get());
            float structural = stored != null ? stored : core.maxStructural();
            core.unpack(new MachineCore.Packed(MachineKind.ANDROID, structural, Math.min(packed.critical(), core.maxCritical()), packed.wear(), Optional.of(owner), ownerName,
                    packed.name()));
        } else {
            Float integrity = stack.get(BodyComponents.STORED_INTEGRITY.get());
            core.setIntegrity(integrity != null ? integrity : core.maxStructural(), core.maxCritical());
        }
        level.addFreshEntity(android);
        MachineRegistry.get(level.getServer()).update(android, core);
        ConsoleLink.linkNearest(level, android, core, player);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        stack.consume(1, player);
        return android;
    }
}
