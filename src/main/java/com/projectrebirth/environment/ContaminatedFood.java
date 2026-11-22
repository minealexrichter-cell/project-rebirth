package com.projectrebirth.environment;

import java.util.Set;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * 1.9.0, block B: contaminated food. A harvest under the open sky during acid rain, a toxic storm or a radiation burst, or in a
 * contaminated zone, and the meat of animals killed in a zone carry toxins (the component {@code projectrebirth:contaminated},
 * the toxic load it gives when eaten). Cooking keeps only a share of it (see the mixin of the cooking recipes); the water
 * purifier and the decontamination chamber wash it off. Food from a sealed room is clean: nothing falls on it there.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class ContaminatedFood {
    /** The catastrophes that fall on a harvest under the open sky. */
    private static final Set<String> FALLING = Set.of(Environment.ACID_RAIN, Environment.TOXIC_STORM, Environment.RADIATION_BURST);

    private ContaminatedFood() {
    }

    public static boolean enabled() {
        return Environment.isEnabled() && WastelandConfig.FOOD_ENABLED.get();
    }

    /** The toxic load this food gives when eaten; 0 if it is clean. */
    public static float toxin(ItemStack stack) {
        Float value = stack.get(EnvironmentRegistry.CONTAMINATED.get());
        return value == null ? 0.0f : value;
    }

    public static boolean isContaminated(ItemStack stack) {
        return toxin(stack) > 0.0f;
    }

    /** Marks food (and only food) with a toxic load; a higher one already there stays. */
    public static void mark(ItemStack stack, float toxin) {
        if (toxin > 0.0f && stack.has(DataComponents.FOOD) && toxin > toxin(stack)) {
            stack.set(EnvironmentRegistry.CONTAMINATED.get(), toxin);
        }
    }

    /** A clean copy: what washing gives. */
    public static ItemStack washed(ItemStack stack) {
        ItemStack clean = stack.copy();
        clean.remove(EnvironmentRegistry.CONTAMINATED.get());
        return clean;
    }

    /** What cooking leaves: the result takes the share [wasteland.food] cookedShare of the load of what went in. */
    public static void afterCooking(ItemStack input, ItemStack result) {
        float load = toxin(input);
        if (load > 0.0f && !result.isEmpty()) {
            mark(result, load * WastelandConfig.FOOD_COOKED_SHARE.get().floatValue());
        }
    }

    /** Whether what grows or dies at this place now takes in the contamination. Public for tests. */
    public static boolean contaminatesHere(ServerLevel level, BlockPos pos) {
        if (!enabled() || Shelters.sealedRoomAt(level, pos) != null) {
            return false;
        }
        var ground = ContaminationZones.readingAt(level, pos);
        if (ground.radiation() + ground.toxin() >= WastelandConfig.FOOD_ZONE_THRESHOLD.get()) {
            return true;
        }
        EnvironmentState state = EnvironmentState.get(level);
        String event = state.event();
        if (event == null || !FALLING.contains(event) || state.strengthNow() <= 0.0f || !level.canSeeSky(pos.above())) {
            return false;
        }
        return Environment.profileFor(level.registryAccess(), level.getBiome(pos)).map(profile -> profile.eventMultiplier(event) > 0.0f).orElse(false);
    }

    /** Only in a zone: an animal there has eaten and drunk what the zone holds. */
    public static boolean zoneHere(ServerLevel level, BlockPos pos) {
        if (!enabled() || Shelters.sealedRoomAt(level, pos) != null) {
            return false;
        }
        var ground = ContaminationZones.readingAt(level, pos);
        return ground.radiation() + ground.toxin() >= WastelandConfig.FOOD_ZONE_THRESHOLD.get();
    }

    static boolean isHarvest(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.is(BlockTags.CROPS) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT);
    }

    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (isHarvest(event.getState()) && contaminatesHere(event.getLevel(), event.getPos())) {
            float load = WastelandConfig.FOOD_CROP_TOXIN.get().floatValue();
            for (ItemEntity drop : event.getDrops()) {
                mark(drop.getItem(), load);
            }
        }
    }

    @SubscribeEvent
    static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Animal animal && animal.level() instanceof ServerLevel level && zoneHere(level, animal.blockPosition())) {
            float load = WastelandConfig.FOOD_MEAT_TOXIN.get().floatValue();
            for (ItemEntity drop : event.getDrops()) {
                mark(drop.getItem(), load);
            }
        }
    }

    @SubscribeEvent
    static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            eaten(player, event.getItem());
        }
    }

    /** A person has eaten this food. Public for tests. */
    public static void eaten(ServerPlayer player, ItemStack food) {
        float load = toxin(food);
        if (load <= 0.0f || !enabled() || TransferService.isInArtificialBody(player) || !player.gameMode.isSurvival()) {
            return;
        }
        Biology old = HumanBody.biology(player);
        HumanBody.setBiology(player, old.withToxin(old.toxin() + load));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projectrebirth.environment.contaminated_food"), true);
    }
}
