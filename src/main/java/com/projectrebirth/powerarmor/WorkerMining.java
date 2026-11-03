package com.projectrebirth.powerarmor;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Mining of a worker armor's pilot with the ability slot (hotbar slot 9) selected instead of a tool: the speed and
 * tier of an unenchanted diamond tool, and in the 3x3 mode the eight blocks around the target in the plane facing the
 * pilot break too. Every one of those blocks is checked like a block the player broke alone (faction territories,
 * spawn protection, other mods' protection).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class WorkerMining {
    /** Speed of a diamond tool on the blocks it is made for. */
    private static final float DIAMOND_SPEED = 8.0f;
    /** True while the extra blocks are broken, so breaking them does not start another 3x3. */
    private static boolean breakingExtra;

    private WorkerMining() {
    }

    /** The worker armor whose pilot mines right now (its ability slot is selected), or null. */
    @Nullable
    public static PowerArmorEntity miningArmor(Player player) {
        if (ArmorAbility.selected(player) == ArmorVariant.WORKER && player.getVehicle() instanceof PowerArmorEntity armor && armor.isActive()) {
            return armor;
        }
        return null;
    }

    /** Whether an unenchanted diamond tool of the right kind mines this block properly. */
    public static boolean diamondCanMine(BlockState state) {
        boolean toolBlock = state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE);
        return toolBlock && !state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
    }

    /** The diamond tool whose drops (and experience) the extra blocks give. */
    static ItemStack virtualTool(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return new ItemStack(Items.DIAMOND_AXE);
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return new ItemStack(Items.DIAMOND_SHOVEL);
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return new ItemStack(Items.DIAMOND_HOE);
        }
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    /**
     * The nine blocks of the 3x3 square: centred on the target, across the axis the pilot looks along most
     * (looking down mines a floor, looking ahead a wall). The centre comes first.
     */
    public static List<BlockPos> area(BlockPos center, Vec3 look) {
        Direction.Axis axis = Direction.getNearest(look.x, look.y, look.z).getAxis();
        List<BlockPos> blocks = new ArrayList<>(9);
        blocks.add(center);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                blocks.add(switch (axis) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                });
            }
        }
        return blocks;
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (miningArmor(event.getEntity()) != null) {
            // Set from scratch: the pilot rides the armor, and vanilla would slow down a player who is not on the ground.
            event.setNewSpeed(diamondCanMine(event.getState()) ? DIAMOND_SPEED : 1.0f);
        }
    }

    @SubscribeEvent
    static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (miningArmor(event.getEntity()) != null && diamondCanMine(event.getTargetBlock())) {
            event.setCanHarvest(true);
        }
    }

    /** Runs last, so the centre block has passed every protection before anything around it breaks. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BlockEvent.BreakEvent event) {
        if (breakingExtra || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PowerArmorEntity armor = miningArmor(player);
        if (armor == null || !armor.isWideMining()) {
            return;
        }
        float centerHardness = event.getState().getDestroySpeed(level, event.getPos());
        List<BlockPos> area = area(event.getPos(), player.getLookAngle());
        breakingExtra = true;
        try {
            for (BlockPos pos : area.subList(1, area.size())) {
                if (mayBreakExtra(level, player, pos, centerHardness)) {
                    breakExtra(level, player, pos);
                }
            }
        } finally {
            breakingExtra = false;
        }
    }

    /**
     * An extra block breaks if it can be broken at all (not air, a fluid, bedrock, a barrier or a portal), is not
     * harder than the target (no free obsidian next to stone) and the player may break it there.
     */
    public static boolean mayBreakExtra(ServerLevel level, ServerPlayer player, BlockPos pos, float centerHardness) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (state.isAir() || hardness < 0 || hardness > centerHardness + 0.01f || !state.getFluidState().isEmpty() && state.getBlock()
                instanceof net.minecraft.world.level.block.LiquidBlock) {
            return false;
        }
        if (!level.mayInteract(player, pos) || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
            return false;
        }
        return !CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled();
    }

    /** Breaks the block as a diamond tool would: same drops and experience, no drops in creative. */
    private static void breakExtra(ServerLevel level, ServerPlayer player, BlockPos pos) {
        // onDestroyedByPlayer runs playerWillDestroy (particles, sound, double blocks) and removes the block.
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack tool = virtualTool(state);
        // The game mode, as vanilla block breaking checks it.
        boolean creative = player.gameMode.isCreative();
        boolean canHarvest = !state.requiresCorrectToolForDrops() || diamondCanMine(state);
        if (!state.onDestroyedByPlayer(level, pos, player, !creative && canHarvest, level.getFluidState(pos))) {
            return;
        }
        state.getBlock().destroy(level, pos, state);
        if (!creative && canHarvest) {
            // Drops and experience (NeoForge adds it in the drop handling) as from this tool.
            state.getBlock().playerDestroy(level, player, pos, state, blockEntity, tool);
        }
    }
}
