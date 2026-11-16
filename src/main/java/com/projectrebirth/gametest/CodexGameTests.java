package com.projectrebirth.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.codex.CodexBook;
import com.projectrebirth.codex.CodexRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Version 1.4.1: the Rebirth Codex is given once, can be made again, and every page of it points at things that exist. */
@GameTestHolder(PlanTests.CODEX)
@PrefixGameTestTemplate(false)
public class CodexGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final Set<String> TYPES = Set.of("text", "item", "block", "entity", "recipe", "note", "multiblock");

    private static JsonObject lang(String name) {
        InputStream stream = CodexGameTests.class.getResourceAsStream("/assets/" + ProjectRebirth.MOD_ID + "/lang/" + name + ".json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = TEMPLATE)
    public static void theBookIsGivenOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.setData(CodexRegistry.CODEX_GIVEN, false);
        helper.assertTrue(CodexRegistry.giveOnce(player), "The first time");
        helper.assertTrue(player.getInventory().contains(stack -> stack.is(CodexRegistry.REBIRTH_CODEX.get())), "The book is in the inventory");
        helper.assertFalse(CodexRegistry.giveOnce(player), "Not a second time");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectRebirth.id("rebirth_codex")).isPresent(), "A lost one is made again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyPagePointsAtThingsThatExist(GameTestHelper helper) {
        List<CodexBook.Category> categories = CodexBook.categories();
        helper.assertFalse(categories.isEmpty(), "The book has categories");
        Map<String, CodexBook.Entry> entries = CodexBook.entries();
        JsonObject english = lang("en_us");
        JsonObject russian = lang("ru_ru");
        for (CodexBook.Category category : categories) {
            helper.assertTrue(english.has(category.titleKey()) && russian.has(category.titleKey()), "Title of " + category.id());
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(category.icon())), "Icon of " + category.id());
        }
        for (CodexBook.Entry entry : entries.values()) {
            String where = "entry " + entry.id() + ": ";
            helper.assertTrue(english.has(entry.titleKey()) && russian.has(entry.titleKey()), where + "title in both languages");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(entry.icon())), where + "icon " + entry.icon());
            helper.assertFalse(entry.pages().isEmpty(), where + "pages");
            for (String parent : entry.parents()) {
                helper.assertTrue(entries.containsKey(parent), where + "comes after " + parent + ", which exists");
            }
            for (CodexBook.Page page : entry.pages()) {
                helper.assertTrue(TYPES.contains(page.type()), where + "page type " + page.type());
                if (page.text() != null) {
                    helper.assertTrue(english.has(page.text()) && russian.has(page.text()), where + "text " + page.text() + " in both languages");
                }
                String value = page.value();
                switch (page.type()) {
                    case "item" -> helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(value)), where + "item " + value);
                    case "block" -> helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(value)), where + "block " + value);
                    case "entity" -> helper.assertTrue(EntityType.byString(value).isPresent(), where + "entity " + value);
                    case "recipe" -> helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(value)).isPresent(),
                            where + "recipe " + value);
                    case "multiblock" -> helper.assertTrue(com.projectrebirth.multiblock.MultiblockRegistry.schema(helper.getLevel().registryAccess(),
                            ResourceLocation.parse(value)) != null, where + "multiblock " + value);
                    default -> helper.assertTrue(page.text() != null, where + "a text page has text");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void entriesDoNotShareAPlaceOnTheMap(GameTestHelper helper) {
        for (CodexBook.Category category : CodexBook.categories()) {
            Set<String> places = new java.util.HashSet<>();
            for (CodexBook.Entry entry : category.entries()) {
                helper.assertTrue(places.add(entry.x() + "," + entry.y()), category.id() + ": two entries at " + entry.x() + "," + entry.y());
            }
        }
        helper.succeed();
    }
}
