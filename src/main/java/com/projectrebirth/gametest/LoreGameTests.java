package com.projectrebirth.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.codex.CodexBook;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9.0, block A: the sample entries of the Codex speak in the voice of the lore bible (docs/LORE.md): a heading of the
 * Archivist, a note of L., and the reference of the mechanics at the end, in both languages.
 */
@GameTestHolder(PlanTests.WASTELAND)
@PrefixGameTestTemplate(false)
public class LoreGameTests {
    private static final String TEMPLATE = "floor_3x3";
    private static final List<String> SAMPLES = List.of("path", "acid_rain");

    private static JsonObject lang(String name) {
        InputStream stream = LoreGameTests.class.getResourceAsStream("/assets/" + ProjectRebirth.MOD_ID + "/lang/" + name + ".json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonObject lang, CodexBook.Page page) {
        return page.text() == null || !lang.has(page.text()) ? "" : lang.get(page.text()).getAsString();
    }

    @GameTest(template = TEMPLATE)
    public static void sampleEntriesSpeakInTheArchivistVoice(GameTestHelper helper) {
        JsonObject english = lang("en_us");
        JsonObject russian = lang("ru_ru");
        for (String id : SAMPLES) {
            CodexBook.Entry entry = CodexBook.entries().get(id);
            helper.assertTrue(entry != null, "The entry " + id + " exists");
            List<CodexBook.Page> pages = entry.pages();
            CodexBook.Page first = pages.get(0);
            helper.assertTrue(text(english, first).startsWith("ARCHIVIST · ") && text(russian, first).startsWith("АРХИВАРИУС · "),
                    id + ": the first page is headed by the Archivist");
            helper.assertTrue(pages.stream().anyMatch(p -> "note".equals(p.type()) && text(english, p).startsWith("L.: ")
                    && text(russian, p).startsWith("Л.: ")), id + ": a note of L.");
            helper.assertTrue(pages.stream().anyMatch(p -> text(english, p).startsWith("REFERENCE") && text(russian, p).startsWith("СПРАВКА")),
                    id + ": the reference of the mechanics");
            for (CodexBook.Page page : pages) {
                // the voice of the lore is grave: no exclamation marks
                helper.assertFalse(text(russian, page).contains("!") || text(english, page).contains("!"), id + ": no exclamation marks");
            }
        }
        helper.succeed();
    }
}
