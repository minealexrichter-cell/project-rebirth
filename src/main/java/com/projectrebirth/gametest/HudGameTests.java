package com.projectrebirth.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.client.HudLayout;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.0, block G: the HUD's new parts are there. The alarms and marks are painted in the widget texture, every alarm
 * has its name in both languages, and the fullest human panel and the body's lines stay above the catastrophe line.
 */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class HudGameTests {
    /** The human panel at its fullest: dose, toxins, temperature, filter, air, shelter, contamination. */
    private static final int HUMAN_LINES = 7;

    private static JsonObject lang(String code) throws Exception {
        try (InputStream in = HudGameTests.class.getResourceAsStream("/assets/projectrebirth/lang/" + code + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static int painted(BufferedImage image, int u, int v, int size) {
        int count = 0;
        for (int x = u; x < u + size; x++) {
            for (int y = v; y < v + size; y++) {
                if ((image.getRGB(x, y) >>> 24) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    @GameTest(template = "floor_3x3")
    public static void hudPartsArePresent(GameTestHelper helper) throws Exception {
        for (String code : new String[] {"en_us", "ru_ru"}) {
            JsonObject lang = lang(code);
            for (GuiAtlas.Alarm alarm : GuiAtlas.Alarm.values()) {
                String key = "hud.projectrebirth.body.alarm." + alarm.name().toLowerCase(Locale.ROOT);
                helper.assertTrue(lang.has(key), code + " names the alarm " + key);
            }
            helper.assertTrue(lang.has("hud.projectrebirth.human.stage_short"), code + " has the short stage");
        }
        BufferedImage widgets;
        try (InputStream in = HudGameTests.class.getResourceAsStream("/assets/projectrebirth/textures/gui/widgets.png")) {
            widgets = ImageIO.read(in);
        }
        // the dust and EMI marks, the six alarms, the three small marks
        for (int u : new int[] {122, 132}) {
            helper.assertTrue(painted(widgets, u, 34, 9) == 81, "hazard mark at " + u + " is painted");
        }
        for (int u = 80; u <= 130; u += 10) {
            helper.assertTrue(painted(widgets, u, 46, 9) == 81, "alarm at " + u + " is painted");
        }
        for (int u : new int[] {80, 86, 92}) {
            helper.assertTrue(painted(widgets, u, 56, 5) > 5, "small mark at " + u + " is painted");
        }
        int lastHumanLine = HudLayout.ROW * HUMAN_LINES;
        helper.assertTrue(lastHumanLine + HudLayout.ROW <= HudLayout.CATASTROPHE_ROW,
                "the fullest human panel ends above the catastrophe line");
        helper.assertTrue(HudLayout.CATASTROPHE_ROW + 10 <= HudLayout.PANEL_HEIGHT, "the catastrophe line is inside the panel");
        helper.succeed();
    }
}
