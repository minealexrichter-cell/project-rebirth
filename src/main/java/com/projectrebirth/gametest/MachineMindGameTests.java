package com.projectrebirth.gametest;

import java.util.List;
import java.util.UUID;

import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.RebirthGameRules;
import com.projectrebirth.environment.Wasteland;
import com.projectrebirth.machinemind.MindBase;
import com.projectrebirth.machinemind.MindComputerBlockEntity;
import com.projectrebirth.machinemind.MindDamage;
import com.projectrebirth.machinemind.MindDroneEntity;
import com.projectrebirth.machinemind.MindModule;
import com.projectrebirth.machinemind.MindModuleBlockEntity;
import com.projectrebirth.machinemind.MindNodes;
import com.projectrebirth.machinemind.MindPartBlock;
import com.projectrebirth.machinemind.MindRegistry;
import com.projectrebirth.machinemind.MindStage;
import com.projectrebirth.monitoring.Snapshot;
import com.projectrebirth.structures.StructureSwitches;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9.0, block F: the machine mind. A node wakes within the limit of the world, has a base by the data of its stages that its
 * drones build and put back, grows with time (made up for after a gap), is set back by damage, and ends with its computer. Each
 * test runs in a batch of its own: a node works on the land about it.
 */
@GameTestHolder(PlanTests.WASTELAND)
@PrefixGameTestTemplate(false)
public class MachineMindGameTests {
    private static final String WIDE = "floor_24x24";
    private static final String SMALL = "floor_9x9";
    private static final long DAY = 24000L;

    private static MindComputerBlockEntity node(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(at);
        level.setBlockAndUpdate(pos, MindRegistry.COMPUTER.get().defaultBlockState());
        MindComputerBlockEntity computer = (MindComputerBlockEntity) level.getBlockEntity(pos);
        computer.forceAwake();
        // its first second: it wakes
        while (Math.floorMod(level.getGameTime() + pos.asLong(), 20L) != 0L) {
            return wakeNow(level, computer);
        }
        computer.serverTick(level, pos);
        return computer;
    }

    private static MindComputerBlockEntity wakeNow(ServerLevel level, MindComputerBlockEntity computer) {
        CompoundTag tag = computer.saveWithoutMetadata(level.registryAccess());
        MindNodes.get(level.getServer()).wake(computer.nodeId(), level, computer.getBlockPos(), 1, 0, true);
        tag.putBoolean("awake", true);
        tag.putBoolean("asked", true);
        tag.putLong("last_time", level.getGameTime());
        computer.loadWithComponents(tag, level.registryAccess());
        return computer;
    }

    private static void end(GameTestHelper helper, MindComputerBlockEntity computer) {
        ServerLevel level = helper.getLevel();
        AABB box = new AABB(computer.getBlockPos()).inflate(48);
        level.removeBlock(computer.getBlockPos(), false);
        MindNodes.get(level.getServer()).forget(computer.nodeId());
        level.getEntitiesOfClass(MindDroneEntity.class, box).forEach(MindDroneEntity::discard);
        level.getEntitiesOfClass(ItemEntity.class, box).forEach(ItemEntity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(computer.getBlockPos().offset(-11, -1, -11), computer.getBlockPos().offset(11, 7, 11))) {
            if (level.getBlockState(pos).is(MindBase.MIND_BLOCKS) || level.getBlockState(pos).is(MindRegistry.PART.get())
                    || level.getBlockEntity(pos) instanceof MindModuleBlockEntity) {
                level.removeBlock(pos, false);
            }
        }
    }

    @GameTest(template = SMALL)
    public static void theFiveStagesComeFromData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<MindStage> stages = MindStage.upTo(level.registryAccess(), 5);
        helper.assertTrue(stages.size() == 5, "Five stages: " + stages.size());
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        int last = 0;
        for (int stage = 1; stage <= 5; stage++) {
            List<MindBase.Cell> plan = MindBase.plan(level, at, net.minecraft.core.Direction.NORTH, stage);
            helper.assertTrue(plan.size() > last, "The base grows with stage " + stage + ": " + plan.size());
            last = plan.size();
            // nothing of the plan lies in the box of a module but its own block, or the box would never fill
            for (MindBase.Cell cell : plan) {
                for (BlockPos part : MindModule.COMPUTER.box(at, MindModule.tier(stage))) {
                    helper.assertFalse(part.equals(cell.pos()), "The box of the computer is free at stage " + stage);
                }
                if (cell.state().getBlock() instanceof com.projectrebirth.machinemind.MindModuleBlock module) {
                    for (BlockPos part : module.module().box(cell.pos(), 1)) {
                        helper.assertTrue(part.equals(cell.pos()) || plan.stream().noneMatch(other -> other.pos().equals(part)),
                                "The box of " + module.module().getSerializedName() + " is free at stage " + stage);
                    }
                }
            }
            helper.assertTrue(MindBase.plan(level, at, net.minecraft.core.Direction.EAST, stage).size() == plan.size(), "Turned with the computer");
        }
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void nodesWakeWithinTheLimitAndAreKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MindNodes nodes = new MindNodes();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        helper.assertTrue(nodes.wake(a, level, BlockPos.ZERO, 1, 2, false) && nodes.wake(b, level, new BlockPos(500, 64, 0), 1, 2, false), "Two wake");
        helper.assertFalse(nodes.wake(c, level, BlockPos.ZERO, 1, 2, false), "The third does not");
        nodes.update(a, 3, false);
        helper.assertFalse(nodes.wake(c, level, BlockPos.ZERO, 1, 2, false), "Nor instead of a destroyed one");
        helper.assertTrue(nodes.wake(c, level, BlockPos.ZERO, 1, 2, true), "Only by the command");
        MindNodes copy = MindNodes.load(nodes.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(copy.all().size() == 3 && !copy.alive(a) && copy.byId(a).stage() == 3 && copy.byNumber(2).id().equals(b), "Kept with the world");
        helper.assertTrue(copy.near(level, 490, 0, 32).size() == 1 && copy.near(level, 0, 0, 32).size() == 1, "Only the living are seen, within range");
        helper.assertFalse(StructureSwitches.outsideSafeArea(StructureSwitches.MIND, 60, 60), "No node near the middle of the world");
        helper.assertTrue(StructureSwitches.outsideSafeArea(StructureSwitches.MIND, 120, 0), "Far from it there may be");
        helper.succeed();
    }

    @GameTest(template = WIDE, batch = "mind_rule")
    public static void theGameRuleAndTheStageOfTheWastelandHoldItBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var on = server.getGameRules().getRule(RebirthGameRules.MACHINE_MIND);
        var max = server.getGameRules().getRule(RebirthGameRules.MACHINE_MIND_MAX_STAGE);
        boolean before = on.get();
        int maxBefore = max.get();
        MindComputerBlockEntity computer = node(helper, new BlockPos(12, 7, 12));
        try {
            on.set(true, server);
            helper.assertTrue(computer.active(), "A node wakes");
            helper.assertTrue(MindComputerBlockEntity.cap(level) == Math.min(Wasteland.stage(level) + WastelandConfig.MIND_AHEAD.get(), maxBefore), "A stage above the Wasteland");
            max.set(1, server);
            helper.assertTrue(MindComputerBlockEntity.cap(level) == 1, "And no higher than the rule");
            computer.catchUp(level, 30L * DAY);
            helper.assertTrue(computer.stage() == 1, "It stays at its cap");
            on.set(false, server);
            helper.assertFalse(computer.active(), "The rule puts it to sleep");
            helper.assertFalse(MindDamage.hit(level, computer.getBlockPos(), 10.0f), "A sleeping one is an ordinary block");
        } finally {
            on.set(before, server);
            max.set(maxBefore, server);
            end(helper, computer);
        }
        helper.succeed();
    }

    @GameTest(template = WIDE, batch = "mind_catch_up")
    public static void theTimeWithoutPlayersIsMadeUpFor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MindComputerBlockEntity computer = node(helper, new BlockPos(12, 7, 12));
        int all = computer.lookAll(level);
        helper.assertTrue(all > 10, "The base is missing at first: " + all);
        computer.catchUp(level, 60L * 5L);
        helper.assertTrue(computer.missing() == all - 5, "A short time builds a few blocks: " + computer.missing());
        // the line stands at the least: its module is the node's
        computer.catchUp(level, DAY);
        helper.assertTrue(computer.lookAll(level) == 0, "A day builds the rest: " + computer.missing());
        MindModuleBlockEntity line = (MindModuleBlockEntity) level.getBlockEntity(computer.getBlockPos().offset(0, 0, 6));
        helper.assertTrue(line != null && line.active() && line.computer() == computer, "The assembly line belongs to the node");
        int drones = computer.units(level, MindRegistry.DRONE.get());
        helper.assertTrue(drones == 2, "Its drones, no more than the cap: " + drones);
        // half the time left to the next stage, then all of it
        long need = computer.needed() - computer.grown();
        helper.assertTrue(computer.stage() == 1 && computer.grown() >= DAY - 60L * all, "It grew: " + computer.grown());
        computer.catchUp(level, need + 60L);
        helper.assertTrue(computer.stage() == Math.min(2, MindComputerBlockEntity.cap(level)), "Then it is stage II");
        helper.assertTrue(MindNodes.get(level.getServer()).byId(computer.nodeId()).stage() == computer.stage(), "The list knows");
        end(helper, computer);
        helper.succeed();
    }

    @GameTest(template = WIDE, batch = "mind_build", timeoutTicks = 900)
    public static void dronesBuildAndPutBackWhatIsBroken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MindComputerBlockEntity computer = node(helper, new BlockPos(12, 7, 12));
        computer.catchUp(level, DAY / 2);
        helper.assertTrue(computer.lookAll(level) == 0, "The base stands: " + computer.missing());
        BlockPos cable = computer.getBlockPos().offset(0, 0, 3);
        helper.assertTrue(level.getBlockState(cable).is(MindRegistry.CABLE.get()), "Its cables");
        level.removeBlock(cable, false);
        int before = computer.resources();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (!done[0] && level.getBlockState(cable).is(MindRegistry.CABLE.get())) {
                done[0] = true;
                helper.assertTrue(computer.units(level, MindRegistry.DRONE.get()) > 0, "By a drone");
                end(helper, computer);
                helper.succeed();
            }
        });
        helper.runAfterDelay(880, () -> {
            if (!done[0]) {
                String state = "drones " + computer.units(level, MindRegistry.DRONE.get()) + ", missing " + computer.missing() + ", resources " + before + " to " + computer.resources();
                end(helper, computer);
                helper.fail("The cable was not put back: " + state);
            }
        });
    }

    @GameTest(template = WIDE, batch = "mind_land")
    public static void nothingOfPlayersIsBuiltOver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MindComputerBlockEntity computer = node(helper, new BlockPos(12, 7, 12));
        BlockPos wall = computer.getBlockPos().offset(0, 0, 3), chest = computer.getBlockPos().offset(0, 0, 4);
        level.setBlockAndUpdate(wall, Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        computer.catchUp(level, DAY);
        helper.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS) && level.getBlockState(chest).is(Blocks.CHEST), "Planks and a chest stay");
        // what drones take: a natural tree and flat stone, not a house
        BlockPos floor = helper.absolutePos(new BlockPos(19, 7, 14));
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(1, 2, 1))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        helper.assertTrue(MindDroneEntity.isResource(level, floor.above(2)), "Stone in the ground");
        helper.assertFalse(MindDroneEntity.isResource(level, floor.offset(1, 2, 0)), "Not the edge of a wall");
        BlockPos log = helper.absolutePos(new BlockPos(3, 7, 12));
        level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
        helper.assertFalse(MindDroneEntity.isResource(level, log), "Not a log without leaves (a house)");
        level.setBlockAndUpdate(log.above(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        helper.assertFalse(MindDroneEntity.isResource(level, log), "Nor with leaves a player put");
        level.setBlockAndUpdate(log.above(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        helper.assertTrue(MindDroneEntity.isResource(level, log), "A natural tree");
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(1, 2, 1))) {
            level.removeBlock(pos, false);
        }
        level.removeBlock(log.above(), false);
        level.removeBlock(log, false);
        level.removeBlock(wall, false);
        level.removeBlock(chest, false);
        end(helper, computer);
        helper.succeed();
    }

    @GameTest(template = WIDE, batch = "mind_damage")
    public static void damageSetsTheNodeBackAndEndsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MindComputerBlockEntity computer = node(helper, new BlockPos(12, 7, 12));
        computer.catchUp(level, DAY / 2);
        BlockPos pos = computer.getBlockPos();
        long need = computer.needed();
        // a drone, a module of the factory: the next stage is put off
        MindDroneEntity drone = level.getEntitiesOfClass(MindDroneEntity.class, new AABB(pos).inflate(40)).get(0);
        drone.hurt(level.damageSources().generic(), 1000.0f);
        helper.assertTrue(computer.needed() == need + WastelandConfig.MIND_DRONE_DELAY.get() * 1200L, "A lost drone puts it off");
        BlockPos linePos = pos.offset(0, 0, 6);
        MindModuleBlockEntity line = (MindModuleBlockEntity) level.getBlockEntity(linePos);
        line.serverTick(level, linePos);
        level.setBlockAndUpdate(linePos.offset(1, 0, 0), MindRegistry.PART.get().defaultBlockState());
        helper.assertTrue(MindPartBlock.owner(level, linePos.offset(1, 0, 0), null) == line, "Its box is the module");
        helper.assertTrue(MindDamage.hit(level, linePos.offset(1, 0, 0), 10.0f) && line.strength() == line.maxStrength() - 10.0f, "A hit on the box hurts it");
        need = computer.needed();
        line.damage(level, 10_000.0f);
        helper.assertTrue(level.isEmptyBlock(linePos) && level.isEmptyBlock(linePos.offset(1, 0, 0)), "A broken module is gone with its box");
        helper.assertTrue(computer.needed() == need + WastelandConfig.MIND_MODULE_DELAY.get() * 1200L, "And puts it off more");
        computer.lookAll(level);
        helper.assertTrue(computer.missing() > 0, "To be built again");
        // the two scales of the computer
        float structure = computer.structure();
        MindDamage.hit(level, pos, structure - 10.0f);
        helper.assertTrue(computer.core() == computer.maxCore() && Math.abs(computer.structure() - 10.0f) < 0.01f, "First the structure");
        MindDamage.hit(level, pos, 30.0f);
        helper.assertTrue(computer.structure() == 0.0f && Math.abs(computer.core() - (computer.maxCore() - 20.0f)) < 0.01f, "Then the core");
        // kept over a save
        MindComputerBlockEntity copy = new MindComputerBlockEntity(pos, computer.getBlockState());
        copy.loadWithComponents(computer.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
        helper.assertTrue(copy.nodeId().equals(computer.nodeId()) && copy.core() == computer.core() && copy.stage() == computer.stage()
                && copy.grown() == computer.grown() && copy.needed() == computer.needed(), "Kept over a save");
        MindDroneEntity last = MindRegistry.DRONE.get().spawn(level, pos.above(3), MobSpawnType.EVENT);
        last.setNode(computer.nodeId());
        UUID id = computer.nodeId();
        MindDamage.hit(level, pos, 10_000.0f);
        helper.assertTrue(level.getBlockState(pos).is(MindRegistry.WRECK.get()), "The computer is a wreck");
        helper.assertFalse(last.isAlive(), "Its machines go dead");
        helper.assertFalse(MindNodes.get(level.getServer()).alive(id), "The node is over");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6)).stream().anyMatch(e -> e.getItem().is(MindRegistry.CHIP.get())), "A mind chip");
        level.removeBlock(pos, false);
        end(helper, computer);
        helper.succeed();
    }

    @GameTest(template = WIDE, batch = "mind_tnt")
    public static void anExplosionHurtsButDoesNotBreakIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MindComputerBlockEntity computer = node(helper, new BlockPos(12, 7, 12));
        BlockPos pos = computer.getBlockPos();
        float before = computer.structure();
        level.explode(null, pos.getX() + 1.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0f, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(level.getBlockEntity(pos) == computer && computer.structure() <= before - WastelandConfig.MIND_EXPLOSION_DAMAGE.get() + 0.01
                && computer.structure() >= before - WastelandConfig.MIND_EXPLOSION_DAMAGE.get() * 2.0, "Hurt once: " + before + " to " + computer.structure());
        helper.assertTrue(Snapshot.mindNodes(level, pos.getX() >> 4, pos.getZ() >> 4, 2).size() == 1, "Seen by the monitoring station and the sensors");
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-5, -1, -5), pos.offset(5, -1, 5))) {
            if (level.isEmptyBlock(at)) {
                level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
            }
        }
        end(helper, computer);
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void wanderersAndMachinesFightDrones(GameTestHelper helper) {
        var drone = helper.spawnWithNoFreeWill(MindRegistry.DRONE.get(), new BlockPos(4, 3, 4));
        helper.assertTrue(com.projectrebirth.wanderers.WandererEntity.isFoe(drone), "A foe of the wanderers");
        helper.assertTrue(drone instanceof net.minecraft.world.entity.monster.Enemy && EntityType.getKey(drone.getType()).getPath().equals("mind_drone"), "A monster for the garrison");
        drone.discard();
        helper.succeed();
    }
}
