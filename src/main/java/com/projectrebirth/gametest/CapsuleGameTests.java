package com.projectrebirth.gametest;

import java.util.List;
import java.util.Optional;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyRegistry;
import com.projectrebirth.bodies.CapsuleNetwork;
import com.projectrebirth.bodies.TransferCapsuleBlock;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.TransferService.TransferResult;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The list of a player's bodies and transfers chosen in the capsule window. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class CapsuleGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos CAPSULE = new BlockPos(0, 2, 0);

    private static ServerPlayer playerAtCapsule(GameTestHelper helper) {
        BlockState lower = BodyBlocks.TRANSFER_CAPSULE.get().defaultBlockState()
                .setValue(TransferCapsuleBlock.FACING, Direction.NORTH).setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(CAPSULE, lower);
        helper.setBlock(CAPSULE.above(), lower.setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.UPPER));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos standAt = helper.absolutePos(new BlockPos(1, 2, 0));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
        return player;
    }

    /** A body that belongs to the player from the moment it appears, like one placed from an item. */
    private static ArtificialBodyEntity bodyOf(GameTestHelper helper, ServerPlayer owner, BodyClass bodyClass, BlockPos relative) {
        ArtificialBodyEntity body = BodyEntities.type(bodyClass).create(helper.getLevel());
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(relative));
        body.setOwner(owner.getUUID());
        body.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(body);
        return body;
    }

    private static BodyRegistry registry(GameTestHelper helper) {
        return BodyRegistry.get(helper.getLevel().getServer());
    }

    private static TransferResult choose(GameTestHelper helper, ServerPlayer player, ArtificialBodyEntity body) {
        return TransferService.transferFromCapsule(player, helper.absolutePos(CAPSULE), Direction.NORTH, body.getUUID());
    }

    private static Optional<BodyRegistry.State> stateOf(GameTestHelper helper, ServerPlayer player, java.util.UUID id) {
        return registry(helper).find(player.getUUID(), id).map(BodyRegistry.BodyRecord::state);
    }

    @GameTest(template = TEMPLATE)
    public static void listShowsBodiesAndTheirState(GameTestHelper helper) {
        ServerPlayer player = playerAtCapsule(helper);
        ArtificialBodyEntity near = bodyOf(helper, player, BodyClass.BASIC_ROBOT, new BlockPos(3, 2, 3));
        ArtificialBodyEntity destroyed = bodyOf(helper, player, BodyClass.IMPROVED_ROBOT, new BlockPos(3, 2, 1));
        ArtificialBodyEntity far = bodyOf(helper, player, BodyClass.ADVANCED_SYNTHETIC, new BlockPos(1, 2, 3));
        destroyed.kill();
        // Moved beyond the transfer radius (only its listed place matters for the window).
        far.moveTo(far.getX() + RebirthConfig.TRANSFER_RADIUS.get() + 10, far.getY(), far.getZ());
        registry(helper).updateStanding(far);
        // Checked after the death animation: a dying body must not list itself as standing again.
        helper.runAfterDelay(30, () -> {
            List<CapsuleNetwork.Entry> entries = CapsuleNetwork.entriesFor(player, helper.getLevel(), helper.absolutePos(CAPSULE));
            helper.assertTrue(entries.size() == 3, "All three bodies must be listed, got " + entries.size());
            helper.assertTrue(entries.get(0).id().equals(near.getUUID()) && entries.get(0).availability() == CapsuleNetwork.Availability.AVAILABLE,
                    "The near body comes first and is available: " + entries.get(0));
            helper.assertTrue(entries.stream().anyMatch(e -> e.id().equals(far.getUUID()) && e.availability() == CapsuleNetwork.Availability.TOO_FAR),
                    "The far body must be marked too far");
            helper.assertTrue(entries.stream().anyMatch(e -> e.id().equals(destroyed.getUUID()) && e.availability() == CapsuleNetwork.Availability.DESTROYED),
                    "The killed body must be marked destroyed");

            helper.assertTrue(choose(helper, player, far) == TransferResult.TOO_FAR, "Transfer into a far body must be refused");
            helper.assertTrue(choose(helper, player, destroyed) == TransferResult.BODY_DESTROYED, "Transfer into a destroyed body must be refused");
            helper.assertFalse(TransferService.isInArtificialBody(player), "After refusals the player is still human");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void chosenBodyNotTheNearestAndItKeepsItsPlaceInTheList(GameTestHelper helper) {
        ServerPlayer player = playerAtCapsule(helper);
        bodyOf(helper, player, BodyClass.BASIC_ROBOT, new BlockPos(1, 2, 1));
        ArtificialBodyEntity chosen = bodyOf(helper, player, BodyClass.HIGHTECH_SYNTHETIC, new BlockPos(3, 2, 3));
        java.util.UUID id = chosen.getUUID();

        helper.assertTrue(choose(helper, player, chosen) == TransferResult.SUCCESS, "The chosen body must be entered");
        helper.assertTrue(TransferService.state(player).body().orElse(null) == BodyClass.HIGHTECH_SYNTHETIC,
                "The player must be in the chosen body, not the nearest one");
        helper.assertTrue(stateOf(helper, player, id).orElse(null) == BodyRegistry.State.CONTROLLED, "The chosen body must be listed as occupied");

        helper.assertTrue(TransferService.returnToHumanBody(player) == TransferService.ReturnResult.SUCCESS, "Return must work");
        helper.assertTrue(helper.getLevel().getEntity(id) instanceof ArtificialBodyEntity, "The body left behind keeps its id");
        helper.assertTrue(stateOf(helper, player, id).orElse(null) == BodyRegistry.State.STANDING, "And is free again in the list");
        helper.assertTrue(registry(helper).bodiesOf(player.getUUID()).size() == 2, "No duplicate entries");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void othersBodiesAndVanishedBodiesCannotBeChosen(GameTestHelper helper) {
        ServerPlayer player = playerAtCapsule(helper);
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        ArtificialBodyEntity foreign = bodyOf(helper, stranger, BodyClass.BASIC_ROBOT, new BlockPos(3, 2, 3));
        ArtificialBodyEntity vanished = bodyOf(helper, player, BodyClass.BASIC_ROBOT, new BlockPos(1, 2, 3));

        helper.assertTrue(choose(helper, player, foreign) == TransferResult.BODY_UNAVAILABLE, "Somebody else's body must be refused");
        vanished.discard();
        helper.assertTrue(choose(helper, player, vanished) == TransferResult.BODY_DESTROYED,
                "A listed body missing from its loaded chunk must be reported as destroyed");
        helper.assertTrue(stateOf(helper, player, vanished.getUUID()).orElse(null) == BodyRegistry.State.DESTROYED,
                "And it is marked destroyed in the list");
        helper.assertFalse(TransferService.isInArtificialBody(player), "The player is still human");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void packingABodyRemovesItFromTheList(GameTestHelper helper) {
        ServerPlayer player = playerAtCapsule(helper);
        ArtificialBodyEntity body = bodyOf(helper, player, BodyClass.BASIC_ROBOT, new BlockPos(3, 2, 3));
        helper.assertTrue(stateOf(helper, player, body.getUUID()).isPresent(), "A placed body must be listed");
        player.setShiftKeyDown(true);
        player.interactOn(body, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(stateOf(helper, player, body.getUUID()).isEmpty(), "A packed body is an item now and leaves the list");
        helper.succeed();
    }
}
