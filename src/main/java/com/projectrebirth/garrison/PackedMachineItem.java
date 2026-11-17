package com.projectrebirth.garrison;

import java.util.List;
import java.util.function.Supplier;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A packed machine (1.7.0): right-click a block to set it up there, with the integrity, wear, owner, name and upgrades it was
 * packed with. Refused on land where the player may not use machines, past the limits of the owner, their faction or the server,
 * and to a player who may not command the owner's machines.
 */
public class PackedMachineItem extends Item {
    private final MachineKind kind;
    private final Supplier<? extends EntityType<? extends MachineEntity>> type;

    public PackedMachineItem(MachineKind kind, Supplier<? extends EntityType<? extends MachineEntity>> type, Properties properties) {
        super(properties);
        this.kind = kind;
        this.type = type;
    }

    public MachineKind kind() {
        return kind;
    }

    public static MachineCore.Packed packed(ItemStack stack) {
        MachineCore.Packed packed = stack.get(GarrisonRegistry.PACKED_MACHINE.get());
        return packed != null ? packed : stack.getItem() instanceof PackedMachineItem item ? item.fresh() : MachineCore.Packed.fresh(MachineKind.TEST);
    }

    /** A new machine of this item: whole, nobody's yet. */
    public MachineCore.Packed fresh() {
        return MachineCore.Packed.fresh(kind);
    }

    /** The entity this item sets up (the multiblock preview shows it). */
    public EntityType<? extends MachineEntity> entityType() {
        return type.get();
    }

    /**
     * A machine just built in a multiblock (block G): it belongs to the owner of the AI core that went into it, if the core is bound
     * to someone.
     */
    public static void bindToCore(ItemStack result, java.util.List<ItemStack> used) {
        if (!(result.getItem() instanceof PackedMachineItem item) || result.has(GarrisonRegistry.PACKED_MACHINE.get())) {
            return;
        }
        for (ItemStack stack : used) {
            GarrisonRegistry.CoreOwner bound = stack.get(GarrisonRegistry.CORE_OWNER.get());
            if (stack.getItem() instanceof AiCoreItem && bound != null) {
                MachineCore.Packed fresh = item.fresh();
                result.set(GarrisonRegistry.PACKED_MACHINE.get(), new MachineCore.Packed(fresh.kind(), fresh.structural(), fresh.critical(), 0.0f,
                        java.util.Optional.of(bound.id()), bound.name(), ""));
                return;
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(context.getClickedFace());
        MachineEntity machine = place(level, player, context.getItemInHand(), pos, Mth.wrapDegrees(player.getYRot() + 180.0f));
        return machine != null ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Sets the machine up at that place (server); tells the player why not and returns null if it may not. Public for tests. */
    public MachineEntity place(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos, float yaw) {
        if (!RebirthConfig.GARRISON_ENABLED.get() || !RebirthConfig.MACHINES_ENABLED.get()) {
            say(player, "disabled");
            return null;
        }
        if (!FactionService.mayUse(player, pos, FactionService.Use.CONTAINER)) {
            say(player, "foreign_land");
            return null;
        }
        MachineEntity machine = type.get().create(level);
        if (machine == null) {
            return null;
        }
        MachineCore core = machine.machineCore();
        core.unpack(packed(stack));
        if (core.owner() == null) {
            core.setOwner(player.getUUID(), player.getGameProfile().getName());
        } else if (!core.mayManage(player)) {
            say(player, "not_yours");
            return null;
        }
        Component limit = MachineRegistry.get(level.getServer()).limitProblem(level.getServer(), core.owner(), kind);
        if (limit != null) {
            player.displayClientMessage(limit, true);
            return null;
        }
        machine.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        machine.setYHeadRot(yaw);
        machine.setYBodyRot(yaw);
        if (!level.noCollision(machine)) {
            say(player, "no_space");
            return null;
        }
        machine.setUpgrades(Upgrades.ofStack(stack));
        machine.readPacked(stack);
        level.addFreshEntity(machine);
        MachineRegistry.get(level.getServer()).update(machine, core);
        ConsoleLink.linkNearest(level, machine, core, player);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        level.playSound(null, pos, com.projectrebirth.core.ModSounds.GARRISON_CORE_BOOT.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
        stack.consume(1, player);
        return machine;
    }

    /**
     * 1.7.1: a machine just built in the heavy dock stands up there by itself and rolls out of the gate. It belongs to the owner of
     * its AI core, else to the dock's owner, and links to a console near it if that owner is online. Returns null when it cannot
     * (a turret, which rolls out in a crate; no room; over the owner's limit): it then goes to the output slot.
     */
    @javax.annotation.Nullable
    public static MachineEntity deployFromDock(ServerLevel level, ItemStack stack, net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 exit,
                                               float yaw, @javax.annotation.Nullable java.util.UUID dockOwner) {
        if (!(stack.getItem() instanceof PackedMachineItem item) || stack.getItem() instanceof TurretItem
                || !RebirthConfig.GARRISON_ENABLED.get() || !RebirthConfig.MACHINES_ENABLED.get()) {
            return null;
        }
        MachineEntity machine = item.type.get().create(level);
        if (machine == null) {
            return null;
        }
        MachineCore core = machine.machineCore();
        core.unpack(packed(stack));
        if (core.owner() == null && dockOwner != null) {
            var cache = level.getServer().getProfileCache();
            String name = cache == null ? "" : cache.get(dockOwner).map(com.mojang.authlib.GameProfile::getName).orElse("");
            core.setOwner(dockOwner, name);
        }
        if (core.owner() != null && MachineRegistry.get(level.getServer()).limitProblem(level.getServer(), core.owner(), item.kind) != null) {
            return null;
        }
        machine.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        machine.setYHeadRot(yaw);
        machine.setYBodyRot(yaw);
        if (!level.noCollision(machine)) {
            return null;
        }
        machine.setUpgrades(Upgrades.ofStack(stack));
        machine.readPacked(stack);
        level.addFreshEntity(machine);
        MachineRegistry.get(level.getServer()).update(machine, core);
        ServerPlayer owner = core.owner() == null ? null : level.getServer().getPlayerList().getPlayer(core.owner());
        if (owner != null) {
            ConsoleLink.linkNearest(level, machine, core, owner);
        }
        level.playSound(null, machine.blockPosition(), com.projectrebirth.core.ModSounds.GARRISON_CORE_BOOT.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
        machine.rollOutTo(exit);
        return machine;
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.projectrebirth.machine." + key), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MachineCore.Packed packed = stack.get(GarrisonRegistry.PACKED_MACHINE.get());
        if (packed == null) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.machine.integrity", Mth.ceil(packed.structural()), Mth.ceil(packed.critical()))
                .withStyle(ChatFormatting.GRAY));
        if (packed.wear() > 0.0f) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.machine.wear", Mth.ceil(packed.wear())).withStyle(ChatFormatting.GRAY));
        }
        if (!packed.ownerName().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.machine.owner", packed.ownerName()).withStyle(ChatFormatting.AQUA));
        }
    }
}
