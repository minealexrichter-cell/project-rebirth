package com.projectrebirth.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.core.ModSounds;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.8.0, block A: the resources of the interface library are in place. Every interface sound is registered, has
 * its file and a subtitle in both languages; every sprite of the library exists, and the ones drawn at any size carry
 * their "nine slice" or "tile" scaling; the texts of the station window are there in both languages. (The look itself is
 * checked in the game with /rbdev ui check.)
 */
@GameTestHolder(com.projectrebirth.ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class UiAssetsGameTests {
    private static final List<String> SOUNDS = List.of("ui.click", "ui.hover", "ui.toggle", "ui.error", "ui.work_start",
            "ui.work_end", "ui.alarm");
    /** Sprites drawn at any size: they need a scaling in their .mcmeta. */
    private static final List<String> SCALED = List.of("window", "screen", "divider", "hazard", "button", "button_hover",
            "button_pressed", "button_disabled", "chip_on", "chip_off", "chip_off_hover", "field", "field_focus", "row_selected",
            "row_hover", "plate_error", "plate_warning", "plate_ok", "plate_info", "track_amber", "panel", "tab_on", "tab_off",
            "tab_hover", "tooltip", "card", "card_selected", "toast");
    /** Sprites of one size. */
    private static final List<String> FIXED = List.of("slot", "slot_output", "slot_missing", "slot_disabled", "slot_upgrade",
            "slot_fuel", "toggle_on", "toggle_off", "icon/check", "icon/cross", "icon/warning", "icon/warning_amber", "icon/search",
            "icon/dot_ok", "icon/dot_warning", "icon/dot_error", "icon/dot_idle", "icon/close", "icon/help", "icon/station",
            "icon/arrow_down", "icon/arrow_up", "icon/info", "icon/alarm");
    private static final List<String> TEXTS = List.of("screen.projectrebirth.ui.help", "screen.projectrebirth.ui.close",
            "screen.projectrebirth.ui.search_recipe", "screen.projectrebirth.ui.map.chunk", "screen.projectrebirth.station.filter.can_make",
            "screen.projectrebirth.station.status.working", "screen.projectrebirth.station.plate.next", "screen.projectrebirth.station.info.automation");

    private static InputStream open(String path) {
        return UiAssetsGameTests.class.getResourceAsStream("/assets/projectrebirth/" + path);
    }

    private static JsonObject json(String path) throws Exception {
        try (InputStream in = open(path)) {
            if (in == null) {
                throw new IllegalStateException("No " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @GameTest(template = "floor_3x3")
    public static void interfaceSoundsSpritesAndTextsArePresent(GameTestHelper helper) throws Exception {
        JsonObject sounds = json("sounds.json");
        JsonObject en = json("lang/en_us.json");
        JsonObject ru = json("lang/ru_ru.json");
        for (String id : SOUNDS) {
            helper.assertTrue(sounds.has(id), "sounds.json has " + id);
            String subtitle = sounds.getAsJsonObject(id).get("subtitle").getAsString();
            helper.assertTrue(en.has(subtitle) && ru.has(subtitle), id + " has a subtitle in both languages");
            for (JsonElement sound : sounds.getAsJsonObject(id).getAsJsonArray("sounds")) {
                String name = (sound.isJsonPrimitive() ? sound.getAsString() : sound.getAsJsonObject().get("name").getAsString())
                        .replace("projectrebirth:", "");
                try (InputStream file = open("sounds/" + name + ".ogg")) {
                    helper.assertTrue(file != null, "the file of " + id + " is there");
                }
            }
        }
        helper.assertTrue(ModSounds.UI_CLICK.get().getLocation().getPath().equals("ui.click"), "the interface sounds are registered");
        for (String sprite : SCALED) {
            JsonObject meta = json("textures/gui/sprites/ui/" + sprite + ".png.mcmeta");
            String type = meta.getAsJsonObject("gui").getAsJsonObject("scaling").get("type").getAsString();
            helper.assertTrue(type.equals("nine_slice") || type.equals("tile"), sprite + " scales as nine slice or tile");
            try (InputStream in = open("textures/gui/sprites/ui/" + sprite + ".png")) {
                helper.assertTrue(in != null, "sprite " + sprite + " is there");
            }
        }
        for (String sprite : FIXED) {
            try (InputStream in = open("textures/gui/sprites/ui/" + sprite + ".png")) {
                helper.assertTrue(in != null, "sprite " + sprite + " is there");
            }
        }
        for (String key : TEXTS) {
            helper.assertTrue(en.has(key) && ru.has(key), key + " is there in both languages");
        }
        helper.succeed();
    }
}
