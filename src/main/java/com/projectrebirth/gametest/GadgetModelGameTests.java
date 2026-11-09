package com.projectrebirth.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.0, block F: the new gadgets and upgrades have their parts in every model, and the textures keep the sizes the
 * first-person arms read them by (bodies 64, armors 128).
 */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class GadgetModelGameTests {
    private static final List<String> BODY_PARTS = List.of("upgrade_battery", "upgrade_cooling", "upgrade_shielding");
    private static final List<String> ARMOR_PARTS = List.of("slot_chest_emi", "slot_back_emi");

    private static JsonObject geometry(String id) throws Exception {
        try (InputStream in = GadgetModelGameTests.class.getResourceAsStream("/assets/projectrebirth/geo/entity/" + id + ".geo.json")) {
            if (in == null) {
                throw new IllegalStateException("No model " + id);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        }
    }

    private static void check(GameTestHelper helper, String id, List<String> parts, int textureSize) throws Exception {
        JsonObject geometry = geometry(id);
        Set<String> bones = new HashSet<>();
        for (JsonElement bone : geometry.getAsJsonArray("bones")) {
            bones.add(bone.getAsJsonObject().get("name").getAsString());
        }
        for (String part : parts) {
            helper.assertTrue(bones.contains(part), id + " has the part " + part);
        }
        int width = geometry.getAsJsonObject("description").get("texture_width").getAsInt();
        helper.assertTrue(width == textureSize, id + " keeps its texture size " + textureSize + ", got " + width);
    }

    private static boolean armHasUv(String id, int u, int v) throws Exception {
        for (JsonElement element : geometry(id).getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            if (!name.equals("right_arm") && !name.equals("right_forearm") || !bone.has("cubes")) {
                continue;
            }
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                JsonObject cube = c.getAsJsonObject();
                if (cube.has("uv") && cube.get("uv").isJsonArray() && cube.getAsJsonArray("uv").get(0).getAsInt() == u
                        && cube.getAsJsonArray("uv").get(1).getAsInt() == v) {
                    return true;
                }
            }
        }
        return false;
    }

    @GameTest(template = "floor_5x5")
    public static void everyModelHasTheNewParts(GameTestHelper helper) throws Exception {
        for (BodyClass bodyClass : BodyClass.values()) {
            check(helper, bodyClass.id(), BODY_PARTS, 64);
        }
        // all three armors have the detailed models of 1.5.2 on 256 textures (their first-person arms read that size too)
        for (String armor : List.of("power_armor", "mobile_power_armor", "worker_power_armor")) {
            check(helper, armor, ARMOR_PARTS, 256);
            // 1.5.2: the shoulder cap and the forearm plate keep the texture places the first-person arm reads (ArmorArmRenderer)
            helper.assertTrue(armHasUv(armor, 140, 100) && armHasUv(armor, 190, 100), armor + ": the arm plates are at their fixed texture places");
        }
        helper.succeed();
    }
}
