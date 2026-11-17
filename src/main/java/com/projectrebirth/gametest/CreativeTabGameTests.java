package com.projectrebirth.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.core.ModCreativeTabs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.7.1: every item of the mod is in exactly one creative tab (but the few that only the mod itself hands out). */
@GameTestHolder(PlanTests.CODEX)
@PrefixGameTestTemplate(false)
public class CreativeTabGameTests {
    /** Items not shown: the armor ability items (given while piloting) and the machine of the tests. */
    private static final Set<String> HIDDEN = Set.of("combat_armor_ability", "mobile_armor_ability", "worker_armor_ability", "test_machine");

    @GameTest(template = "floor_5x5")
    public static void everyItemIsInOneTab(GameTestHelper helper) {
        Map<Item, ModCreativeTabs.Kind> seen = new HashMap<>();
        for (ModCreativeTabs.Kind kind : ModCreativeTabs.Kind.values()) {
            helper.assertFalse(ModCreativeTabs.contents(kind).isEmpty(), kind + " must not be empty");
            for (ItemStack stack : ModCreativeTabs.contents(kind)) {
                ModCreativeTabs.Kind before = seen.put(stack.getItem(), kind);
                helper.assertTrue(before == null, stack.getItem() + " is in " + before + " and " + kind);
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(ProjectRebirth.MOD_ID) && !HIDDEN.contains(id.getPath())) {
                helper.assertTrue(seen.containsKey(item), id + " is in no creative tab");
            }
        }
        helper.succeed();
    }
}
