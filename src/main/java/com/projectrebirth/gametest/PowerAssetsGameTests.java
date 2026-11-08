package com.projectrebirth.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.core.ModSounds;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.0, block H: the sounds, textures and block models of the technical limits are in place. Every new sound is
 * registered, has its file and a subtitle in both languages; the new textures exist (the running ones are animated); the
 * charging pad and the heater have models of their own, not a plain cube.
 */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class PowerAssetsGameTests {
    private static final List<String> SOUNDS = List.of("power.dust_wind", "power.emi_crackle", "power.emi_fault", "power.low_charge",
            "power.overheat_alarm", "power.charging_hum");
    private static final List<String> TEXTURES = List.of("block/charging_pad_top", "block/charging_pad_top_on", "block/charging_pad_side",
            "block/heater_side", "block/heater_side_on", "block/heater_top", "item/body_battery", "item/extra_battery", "item/cooling_module",
            "item/thermal_liner", "item/emi_shielding", "item/emi_screen", "mob_effect/heatstroke", "mob_effect/hypothermia");

    private static InputStream open(String path) {
        return PowerAssetsGameTests.class.getResourceAsStream("/assets/projectrebirth/" + path);
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
    public static void soundsTexturesAndModelsArePresent(GameTestHelper helper) throws Exception {
        JsonObject sounds = json("sounds.json");
        JsonObject en = json("lang/en_us.json");
        JsonObject ru = json("lang/ru_ru.json");
        for (String id : SOUNDS) {
            helper.assertTrue(sounds.has(id), "sounds.json has " + id);
            String subtitle = sounds.getAsJsonObject(id).get("subtitle").getAsString();
            helper.assertTrue(en.has(subtitle) && ru.has(subtitle), id + " has a subtitle in both languages");
            for (JsonElement sound : sounds.getAsJsonObject(id).getAsJsonArray("sounds")) {
                String name = sound.getAsJsonObject().get("name").getAsString().replace("projectrebirth:", "");
                try (InputStream file = open("sounds/" + name + ".ogg")) {
                    helper.assertTrue(file != null, "the file of " + id + " is there");
                }
            }
        }
        helper.assertTrue(ModSounds.CHARGING_HUM.get().getLocation().getPath().equals("power.charging_hum"), "the sounds are registered");
        for (String id : TEXTURES) {
            BufferedImage image;
            try (InputStream in = open("textures/" + id + ".png")) {
                helper.assertTrue(in != null, "texture " + id + " is there");
                image = ImageIO.read(in);
            }
            helper.assertTrue(image.getWidth() == 16, id + " is 16 wide");
            boolean running = id.endsWith("_on");
            helper.assertTrue(running == (image.getHeight() > 16), id + (running ? " is animated" : " is one frame"));
            if (running) {
                try (InputStream meta = open("textures/" + id + ".png.mcmeta")) {
                    helper.assertTrue(meta != null, id + " has its animation");
                }
            }
        }
        for (String model : List.of("charging_pad", "charging_pad_on", "heater", "heater_on")) {
            JsonObject json = json("models/block/" + model + ".json");
            helper.assertTrue(json.has("elements") && json.getAsJsonArray("elements").size() > 1, model + " is a model of its own");
        }
        helper.succeed();
    }
}
