package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.ContaminatedFood;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.Protection;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.hostile.FleeFireGoal;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.hostile.MutantEntity;
import com.projectrebirth.hostile.MutantSpawnRule;
import com.projectrebirth.hostile.MutantSpawner;
import com.projectrebirth.hostile.MutatedAnimalEntity;
import com.projectrebirth.hostile.Mutations;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9.0, block C: the ecology of mutants. Spawn rules from data, the limits by stage, the mutation of animals, the mutated
 * animals and their contaminated meat, what mutants hunt, the fear of fire, the loot and what is made of it.
 */
@GameTestHolder(PlanTests.WASTELAND)
@PrefixGameTestTemplate(false)
public class MutantEcologyGameTests {
    private static final String TEMPLATE = "floor_9x9";

    @GameTest(template = TEMPLATE)
    public static void theOldMutantsComeByRulesOfData(GameTestHelper helper) {
        List<MutantSpawnRule> rules = MutantSpawner.rules(helper.getLevel());
        MutantSpawnRule hound = rules.stream().filter(rule -> rule.entity() == HostileRegistry.MUTATED_HOUND.get() && rule.contaminated().orElse(false))
                .findFirst().orElse(null);
        helper.assertTrue(hound != null && hound.groupMin() == 3 && hound.groupMax() == 5 && hound.weight() == 50, "Hounds in packs of 3 to 5, weight 50");
        helper.assertTrue(rules.stream().anyMatch(rule -> rule.entity() == HostileRegistry.GLOWING_GHOUL.get() && rule.weight() == 40), "Ghouls, weight 40");
        helper.assertTrue(rules.stream().anyMatch(rule -> rule.entity() == HostileRegistry.BRUTE.get() && rule.weight() == 10), "Brutes, weight 10");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRuleFitsItsTimeStageCatastropheAndGround(GameTestHelper helper) {
        MutantSpawnRule night = new MutantSpawnRule(HostileRegistry.MUTATED_HOUND.get(), java.util.Optional.empty(), java.util.Optional.of(false),
                "night", 15, -64, 320, MutantSpawnRule.GROUND, List.of(), 2, 4, 2, 4, 10, 1.0f, 6);
        helper.assertTrue(night.fitsNow(true, 2, null, false), "At night, stage II, clean ground");
        helper.assertFalse(night.fitsNow(false, 2, null, false), "Not by day");
        helper.assertFalse(night.fitsNow(true, 1, null, false), "Not at stage I");
        helper.assertFalse(night.fitsNow(true, 2, null, true), "Not on contaminated ground");
        MutantSpawnRule storm = new MutantSpawnRule(HostileRegistry.GLOWING_GHOUL.get(), java.util.Optional.empty(), java.util.Optional.empty(),
                "any", 15, -64, 320, MutantSpawnRule.GROUND, List.of("toxic_storm"), 1, 4, 1, 1, 1, 1.0f, 0);
        helper.assertTrue(storm.fitsNow(false, 1, "toxic_storm", false) && !storm.fitsNow(false, 1, null, true), "Only in its catastrophe");
        helper.assertTrue(MutantSpawner.stageFactor(4) > MutantSpawner.stageFactor(1), "More mutants at a later stage");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void cleanGroundAtStageOneBringsNoMutants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
        com.projectrebirth.environment.ContaminationMap.get(level).clearAround(centre, 1);
        // by day, or at stage I by night, only the rules of contaminated ground exist
        if (com.projectrebirth.environment.Wasteland.stage(level) == 1) {
            helper.assertTrue(MutantSpawner.fitting(level, centre).isEmpty(), "No rule fits clean ground at stage I");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anAnimalMutatesAndItsMeatIsContaminated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(Mutations.into(level, EntityType.COW).orElse(null) == HostileRegistry.MUTATED_COW.get(), "A cow becomes a mutated cow");
        helper.assertTrue(Mutations.into(level, EntityType.WOLF).orElse(null) == HostileRegistry.MUTATED_HOUND.get(), "A wolf becomes a hound");
        Cow calf = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        calf.setBaby(true);
        Cow named = helper.spawn(EntityType.COW, new BlockPos(6, 2, 2));
        named.setCustomName(Component.literal("Daisy"));
        helper.assertFalse(Mutations.canMutate(calf) || Mutations.canMutate(named), "Calves and named animals do not mutate");
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 4));
        helper.assertTrue(Mutations.canMutate(cow), "An ordinary cow does");
        Mob mutant = Mutations.mutate(level, cow, HostileRegistry.MUTATED_COW.get());
        helper.assertTrue(mutant instanceof MutatedAnimalEntity animal && animal.kind() == MutatedAnimalEntity.Kind.COW && !cow.isAlive(), "It turned");
        mutant.kill();
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(mutant.blockPosition()).inflate(3.0));
            helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Items.BEEF) && ContaminatedFood.isContaminated(drop.getItem())),
                    "Its beef is contaminated");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            calf.discard();
            named.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void mutantsHuntVillagersAnimalsAndMachinesButNotEachOther(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(6, 2, 2));
        MutantEntity hound = helper.spawnWithNoFreeWill(HostileRegistry.MUTATED_HOUND.get(), new BlockPos(4, 2, 6));
        MutantEntity pig = helper.spawnWithNoFreeWill(HostileRegistry.MUTATED_PIG.get(), new BlockPos(2, 2, 6));
        helper.assertTrue(MutantEntity.isPrey(villager) && MutantEntity.isPrey(cow), "Villagers and animals are prey");
        helper.assertFalse(MutantEntity.isPrey(hound) || MutantEntity.isPrey(pig), "Mutants and mutated animals are not");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mutantsSeeFireNearby(GameTestHelper helper) {
        MutantEntity hound = helper.spawnWithNoFreeWill(HostileRegistry.MUTATED_HOUND.get(), new BlockPos(4, 2, 4));
        helper.assertTrue(FleeFireGoal.fireNear(hound) == null, "No fire yet");
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.CAMPFIRE.defaultBlockState());
        helper.assertTrue(FleeFireGoal.fireNear(hound) != null, "A lit campfire is seen");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theLootOfMutantsIsUseful(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(new ItemStack(HostileRegistry.RADIOACTIVE_BONE.get()).is(RadiationSources.RADIOACTIVE_ITEMS), "The bone radiates");
        float filter = Protection.filterLeft(new ItemStack(EnvironmentRegistry.FILTER.get()));
        float biofilter = Protection.filterLeft(new ItemStack(EnvironmentRegistry.BIOFILTER.get()));
        helper.assertTrue(Math.abs(biofilter - filter * WastelandConfig.BIOFILTER_LIFE.get()) < 0.01, "A biofilter lasts longer: " + biofilter);
        Exposure dirty = Exposure.builder().add(Hazard.POLLUTION, 50.0f).build();
        HumanBody.setBiology(player, Biology.CLEAN);
        HumanBody.onSecond(player, dirty);
        float plain = HumanBody.biology(player).toxin();
        HumanBody.setBiology(player, Biology.CLEAN);
        EnvironmentRegistry.ANTITOXIN.get().take(new ItemStack(EnvironmentRegistry.ANTITOXIN.get()), player);
        HumanBody.onSecond(player, dirty);
        float resisted = HumanBody.biology(player).toxin();
        helper.assertTrue(resisted < plain * 0.75f, "The antitoxin lets fewer toxins in: " + resisted + " / " + plain);
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[]{"antitoxin", "antiseptic_from_mutagen", "biofilter", "chitin_plating", "decontamination_solution_from_mutagen"}) {
            helper.assertTrue(recipes.byKey(com.projectrebirth.ProjectRebirth.id(id)).isPresent(), "Recipe " + id);
        }
        helper.assertTrue(RebirthConfig.plating("chitin") != null, "Chitin plating has its numbers");
        helper.succeed();
    }
}
