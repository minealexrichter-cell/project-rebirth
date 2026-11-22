package com.projectrebirth.water;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.ContaminationZones;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.EventPhase;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.shelter.Contamination;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Polluted water (1.6.0, block C). Not a liquid of its own: water is polluted where the ground is (a contaminated chunk of block A)
 * and, during a toxic storm or acid rain, wherever it lies under the open sky. Swimming in it poisons a person and soils whatever
 * swims; a glass bottle or a bucket filled there holds polluted water, which a water purifier makes clean again.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class WaterPollution {
    private WaterPollution() {
    }

    public static boolean enabled() {
        return RebirthConfig.SERVER_SPEC.isLoaded() && Environment.isEnabled() && RebirthConfig.WATER_ENABLED.get();
    }

    /** Whether the water at the place is polluted (false where there is no water). */
    public static boolean isPolluted(ServerLevel level, BlockPos pos) {
        if (!enabled() || !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        if (ContaminationZones.mask(level, pos) != 0) {
            return true;
        }
        if (!RebirthConfig.WATER_STORMS.get() || !Environment.hasCatastrophes(level.registryAccess(), level.dimension())) {
            return false;
        }
        EnvironmentState state = EnvironmentState.get(level);
        boolean storm = state.phase() == EventPhase.ACTIVE
                && (Environment.TOXIC_STORM.equals(state.event()) || Environment.ACID_RAIN.equals(state.event()));
        return storm && underOpenSky(level, pos);
    }

    /** Nothing but water and air between the place and the sky: the rain falls into this water. */
    public static boolean underOpenSky(ServerLevel level, BlockPos pos) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos.MutableBlockPos at = pos.mutable();
        for (int y = pos.getY() + 1; y < top; y++) {
            at.setY(y);
            BlockState state = level.getBlockState(at);
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- swimming

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Environment.isSecondOf(player.level().getGameTime(), player.getId())
                || !player.gameMode.isSurvival() || !player.isInWater() || !enabled()) {
            return;
        }
        BlockPos at = BlockPos.containing(player.getX(), player.getY() + 0.3, player.getZ());
        if (isPolluted(player.serverLevel(), at)) {
            swimSecond(player);
        }
    }

    /** One second in polluted water: toxins for a living person (less through a suit), contamination on whoever swims. Public for tests. */
    public static void swimSecond(ServerPlayer player) {
        if (!TransferService.isInArtificialBody(player)) {
            addToxin(player, RebirthConfig.WATER_SWIM_TOXIN.get().floatValue() * (1.0f - HumanBody.protection(player, Hazard.POLLUTION)));
        }
        if (!(player.getVehicle() instanceof com.projectrebirth.powerarmor.PowerArmorEntity)) {
            Contamination.set(player, Contamination.of(player) + RebirthConfig.WATER_SWIM_CONTAMINATION.get().floatValue());
        }
    }

    static void addToxin(Player player, float load) {
        if (load <= 0.0f) {
            return;
        }
        Biology now = HumanBody.biology(player);
        HumanBody.setBiology(player, now.withToxin(now.toxin() + load));
    }

    // ---------------------------------------------------------------- filling bottles and buckets

    /**
     * A glass bottle or an empty bucket used on polluted water fills with polluted water instead of the clean kind. The server
     * decides (only it knows the contaminated chunks); the client's guess is put right by the inventory update.
     */
    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || !enabled()) {
            return;
        }
        ItemStack held = event.getItemStack();
        boolean bottle = held.is(Items.GLASS_BOTTLE), bucket = held.is(Items.BUCKET);
        if (!bottle && !bucket) {
            return;
        }
        BlockHitResult hit = pick(player, bottle ? ClipContext.Fluid.ANY : ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !isPolluted(level, hit.getBlockPos())) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        ItemStack filled;
        if (bottle) {
            filled = new ItemStack(WaterRegistry.POLLUTED_WATER_BOTTLE.get());
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 1.0f);
        } else {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BucketPickup pickup) || pickup.pickupBlock(player, level, pos, state).isEmpty()) {
                return;
            }
            filled = new ItemStack(WaterRegistry.POLLUTED_WATER_BUCKET.get());
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(held, player, filled));
        player.inventoryMenu.sendAllDataToRemote();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** What the player looks at, as an item reaching for a fluid sees it. */
    static BlockHitResult pick(Player player, ClipContext.Fluid fluid) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluid, player));
    }
}
