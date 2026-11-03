package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for resources and production. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class TechGameTests {
    private static final String TEMPLATE = "floor_3x3";

    /** Every ore drops its raw material to a fitting pickaxe and nothing to bare hands. */
    @GameTest(template = TEMPLATE)
    public static void oresDropRawMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (Metal metal : Metal.values()) {
            for (Block ore : List.of(TechBlocks.ORES.get(metal).get(), TechBlocks.DEEPSLATE_ORES.get(metal).get())) {
                BlockState state = ore.defaultBlockState();
                List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, pickaxe);
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(TechItems.RAW.get(metal).get()),
                        ore + " should drop raw " + metal.id() + ", dropped " + drops);
                helper.assertTrue(pickaxe.isCorrectToolForDrops(state), "Iron pickaxe must mine " + ore);
                helper.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),
                        "Wooden pickaxe must not mine " + ore);
            }
        }
        helper.succeed();
    }

    /** Raw material and ore smelt into the ingot. */
    @GameTest(template = TEMPLATE)
    public static void rawMaterialsSmeltIntoIngots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Metal metal : Metal.values()) {
            for (ItemStack input : List.of(new ItemStack(TechItems.RAW.get(metal).get()), new ItemStack(TechItems.ORES.get(metal).get()))) {
                ItemStack result = level.getRecipeManager()
                        .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                        .map(recipe -> recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess()))
                        .orElse(ItemStack.EMPTY);
                helper.assertTrue(result.is(TechItems.INGOTS.get(metal).get()), input + " should smelt into the " + metal.id() + " ingot, got " + result);
            }
        }
        helper.succeed();
    }

    /** Three copper and one tin make four bronze on a normal crafting table. */
    @GameTest(template = TEMPLATE)
    public static void bronzeFromCopperAndTin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.COPPER_INGOT), new ItemStack(TechItems.INGOTS.get(Metal.TIN).get())));
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(TechItems.BRONZE_INGOT.get()) && result.getCount() == 4, "Expected 4 bronze, got " + result);
        helper.succeed();
    }
}
