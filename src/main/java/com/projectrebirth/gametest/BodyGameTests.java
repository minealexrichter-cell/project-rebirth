package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyComponents;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side checks for artificial bodies. Run with {@code .\gradlew runGameTestServer}.
 * The template is a 3x3x3 area with a stone floor; GameTest relative coordinates start one block below it.
 */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodyGameTests {
    private static final String TEMPLATE = "floor_3x3";
    private static final BlockPos FLOOR = new BlockPos(1, 1, 1);
    private static final BlockPos BODY_POS = new BlockPos(1, 2, 1);

    private static EntityType<ArtificialBodyEntity> basicType() {
        return BodyEntities.type(BodyClass.BASIC_ROBOT);
    }

    private static InteractionResult placeOnFloor(GameTestHelper helper, Player player) {
        BlockPos floor = helper.absolutePos(FLOOR);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    @GameTest(template = TEMPLATE)
    public static void bodyItemPlacesBodyAndIsConsumed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT)));

        InteractionResult result = placeOnFloor(helper, player);
        helper.assertTrue(result.consumesAction(), "Placing the body should succeed, got " + result
                + "; floor=" + helper.getBlockState(FLOOR) + ", y1=" + helper.getBlockState(BODY_POS)
                + ", y2=" + helper.getBlockState(BODY_POS.above()) + ", y3=" + helper.getBlockState(BODY_POS.above(2)));
        helper.assertEntityPresent(basicType(), BODY_POS);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "The body item must be consumed");
        ArtificialBodyEntity body = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BODY_POS))).getFirst();
        helper.assertTrue(body.getHealth() == body.getMaxHealth(), "A new body must have full integrity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void packingUpReturnsExactlyOneItemAndKeepsDamage(GameTestHelper helper) {
        ArtificialBodyEntity body = helper.spawn(basicType(), BODY_POS);
        body.setHealth(body.getMaxHealth() - 5.0f);
        float damagedHealth = body.getHealth();

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        body.interact(player, InteractionHand.MAIN_HAND);
        // A second interaction with the already removed body must not create another item.
        body.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(body.isRemoved(), "The body must be removed after packing up");
        ArtificialBodyItem item = BodyItems.item(BodyClass.BASIC_ROBOT);
        int count = player.getInventory().countItem(item);
        helper.assertTrue(count == 1, "Expected exactly one body item, got " + count);

        ItemStack packed = player.getInventory().items.stream().filter(stack -> stack.is(item)).findFirst().orElseThrow();
        Float stored = packed.get(BodyComponents.STORED_INTEGRITY.get());
        helper.assertTrue(stored != null && stored == damagedHealth, "Stored integrity should be " + damagedHealth + ", got " + stored);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void damagedBodyStaysDamagedAfterPlacing(GameTestHelper helper) {
        ItemStack stack = new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT));
        stack.set(BodyComponents.STORED_INTEGRITY.get(), 7.0f);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        placeOnFloor(helper, player);
        ArtificialBodyEntity body = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BODY_POS))).getFirst();
        helper.assertTrue(body.getHealth() == 7.0f, "Expected integrity 7, got " + body.getHealth());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bodyIsNotPlacedWithoutSpace(GameTestHelper helper) {
        helper.setBlock(BODY_POS.above(), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT)));

        placeOnFloor(helper, player);
        helper.assertEntityNotPresent(basicType());
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "The item must not be consumed when placing fails");
        helper.succeed();
    }
}
