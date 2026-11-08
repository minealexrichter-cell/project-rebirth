package com.projectrebirth.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.shelter.ShelterRegistry;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.1: the 3D models of the protective gear and the see-through walls behind the shelter blocks. Every gear model
 * has its file and its texture of the size it declares; every extra cube lies on the texture, outside the vanilla armor
 * layout and apart from the other cubes. The shelter blocks with models smaller than a block do not hide the faces next to
 * them.
 */
@GameTestHolder(PlanTests.ATMOSPHERE)
@PrefixGameTestTemplate(false)
public class GearModelGameTests {
    private static final List<String> MODELS = List.of("respirator", "hazmat_layer_1", "hazmat_layer_2", "heavy_suit_layer_1", "heavy_suit_layer_2");

    @GameTest(template = "floor_5x5")
    public static void gearModelsFitTheirTextures(GameTestHelper helper) throws Exception {
        for (String model : MODELS) {
            JsonObject json;
            try (InputStream in = GearModelGameTests.class.getResourceAsStream("/assets/projectrebirth/gear/" + model + ".json")) {
                helper.assertTrue(in != null, "gear model " + model + " is there");
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            int width = json.getAsJsonArray("texture").get(0).getAsInt();
            int height = json.getAsJsonArray("texture").get(1).getAsInt();
            String texture = model.equals("respirator") ? "respirator_layer_1" : model;
            try (InputStream in = GearModelGameTests.class.getResourceAsStream("/assets/projectrebirth/textures/models/armor/" + texture + ".png")) {
                BufferedImage image = ImageIO.read(in);
                helper.assertTrue(image.getWidth() == width && image.getHeight() == height, model + " texture is " + width + "x" + height);
            }
            List<int[]> rects = new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("parts")) {
                JsonObject part = element.getAsJsonObject();
                JsonArray uv = part.getAsJsonArray("uv");
                JsonArray size = part.getAsJsonArray("size");
                int u = uv.get(0).getAsInt(), v = uv.get(1).getAsInt();
                int w = size.get(0).getAsInt(), h = size.get(1).getAsInt(), d = size.get(2).getAsInt();
                int[] rect = {u, v, 2 * (w + d), d + h};
                helper.assertTrue(u + rect[2] <= width && v + rect[3] <= height, model + "/" + part.get("name").getAsString() + " lies on the texture");
                helper.assertTrue(u >= 64 || v >= 32, model + "/" + part.get("name").getAsString() + " keeps off the vanilla armor layout");
                // twins share their texture on purpose
                String key = u + "," + v;
                if (!seen.contains(key)) {
                    for (int[] other : rects) {
                        boolean apart = u >= other[0] + other[2] || other[0] >= u + rect[2] || v >= other[1] + other[3] || other[1] >= v + rect[3];
                        helper.assertTrue(apart, model + "/" + part.get("name").getAsString() + " does not overlap another cube on the texture");
                    }
                    rects.add(rect);
                    seen.add(key);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "floor_5x5")
    public static void shelterBlocksDoNotHideTheirNeighbours(GameTestHelper helper) {
        helper.assertFalse(ShelterRegistry.AIR_FILTRATION_UNIT.get().defaultBlockState().canOcclude(), "air filtration unit does not occlude");
        helper.assertFalse(ShelterRegistry.SHELTER_CONTROL_PANEL.get().defaultBlockState().canOcclude(), "control panel does not occlude");
        helper.assertFalse(ShelterRegistry.BACKUP_BATTERY.get().defaultBlockState().canOcclude(), "backup battery does not occlude");
        helper.assertFalse(com.projectrebirth.environment.TemperatureRegistry.HEATER.get().defaultBlockState().canOcclude(), "heater does not occlude");
        helper.succeed();
    }
}
