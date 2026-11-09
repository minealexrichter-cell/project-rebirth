package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.2: the combat armor opens before its pilot gets in and out. Get in: right click, the shell opens, the player
 * steps in, it closes. Get out: sneak, it opens, the pilot steps out in front, it closes. While it is not shut the armor
 * does not walk. The mobile armor opens the same way (its chest plate and muzzle); the worker still lets its pilot in and out
 * at once.
 */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorShellGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos ARMOR_POS = new BlockPos(2, 2, 2);
    private static final int OPEN = PowerArmorEntity.SHELL_OPEN_TICKS;
    private static final int OPEN_AND_HOLD = PowerArmorEntity.SHELL_OPEN_TICKS + PowerArmorEntity.SHELL_HOLD_TICKS;
    private static final int WHOLE = OPEN_AND_HOLD + PowerArmorEntity.SHELL_CLOSE_TICKS;

    private static PowerArmorEntity armor(GameTestHelper helper, ArmorVariant variant) {
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), ARMOR_POS);
        armor.setVariant(variant);
        return armor;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerBeside(GameTestHelper helper, PowerArmorEntity armor) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = armor.position().add(0.0, 0.0, -1.5);
        player.moveTo(at.x, at.y, at.z);
        return player;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void theArmorOpensLetsThePlayerInAndCloses(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer player = playerBeside(helper, armor);
        armor.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_OPENING, "Right click opens the shell");
        helper.assertFalse(player.isPassenger(), "The player waits until it is open");
        helper.runAtTickTime(OPEN - 4, () -> helper.assertFalse(player.isPassenger(), "Still opening: not in yet"));
        helper.runAtTickTime(OPEN_AND_HOLD + 3, () -> {
            helper.assertTrue(player.getVehicle() == armor, "Open: the player has stepped in");
            helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_CLOSING, "With the pilot in, it closes");
        });
        helper.runAtTickTime(WHOLE + 5, () -> {
            helper.assertTrue(armor.isShellClosed(), "Shut again");
            helper.assertTrue(player.getVehicle() == armor, "with the pilot inside");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void thePilotSneaksAndTheArmorOpensToLetThemOut(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer pilot = playerBeside(helper, armor);
        helper.assertTrue(pilot.startRiding(armor, true), "The pilot gets in");
        pilot.setShiftKeyDown(true);
        pilot.stopRiding();
        helper.assertTrue(pilot.getVehicle() == armor, "Sneaking does not drop the pilot out at once");
        helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_OPENING, "It opens first");
        helper.runAtTickTime(OPEN_AND_HOLD + 3, () -> {
            helper.assertFalse(pilot.isPassenger(), "Open: the pilot has stepped out");
            helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_CLOSING, "Empty, it closes");
        });
        helper.runAtTickTime(WHOLE + 5, () -> {
            helper.assertTrue(armor.isShellClosed(), "Shut again, empty");
            helper.assertFalse(armor.isVehicle(), "Nobody inside");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aPlayerWhoWalksAwayIsNotPulledIn(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer player = playerBeside(helper, armor);
        armor.interact(player, InteractionHand.MAIN_HAND);
        ServerPlayer other = playerBeside(helper, armor);
        armor.interact(other, InteractionHand.MAIN_HAND);
        helper.assertFalse(other.isPassenger(), "Another player waits while it opens for the first");
        helper.runAtTickTime(4, () -> player.moveTo(player.getX() + 30.0, player.getY(), player.getZ()));
        helper.runAtTickTime(WHOLE + 5, () -> {
            helper.assertFalse(player.isPassenger(), "Too far: not pulled in");
            helper.assertTrue(armor.isShellClosed(), "It closes again empty");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void theMobileArmorOpensToo(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.MOBILE);
        ServerPlayer player = playerBeside(helper, armor);
        armor.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_OPENING, "Right click opens the mobile armor");
        helper.assertFalse(player.isPassenger(), "The player waits until it is open");
        helper.runAtTickTime(WHOLE + 5, () -> {
            helper.assertTrue(player.getVehicle() == armor, "In");
            helper.assertTrue(armor.isShellClosed(), "and shut");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void otherArmorsStillLetThePilotInAtOnce(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.WORKER);
        ServerPlayer player = playerBeside(helper, armor);
        armor.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getVehicle() == armor, "A worker armor (no opening model yet) takes the pilot at once");
        helper.assertTrue(armor.isShellClosed(), "and never opens");
        helper.succeed();
    }

    /**
     * Version 1.5.2: no two faces of the combat or the mobile armor lie in one plane over each other, so no texture flickers.
     * The upgrade parts are left out (one shows at a time), and so are the cuts at the knees and elbows (two halves of one
     * cube).
     */
    @GameTest(template = TEMPLATE)
    public static void theDetailedModelsHaveNoFlickeringFaces(GameTestHelper helper) throws Exception {
        checkFaces(helper, "power_armor");
        checkFaces(helper, "mobile_power_armor");
        checkFaces(helper, "worker_power_armor");
        helper.succeed();
    }

    /**
     * Version 1.5.2: the worker's pilot sits in its glass cab and can be seen (the client draws a seated pilot there); the
     * eyes are high in the cab, under its roof.
     */
    @GameTest(template = TEMPLATE)
    public static void theWorkerPilotSitsInTheCab(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.WORKER);
        ServerPlayer pilot = playerBeside(helper, armor);
        armor.interact(pilot, InteractionHand.MAIN_HAND);
        helper.assertTrue(pilot.getVehicle() == armor, "The worker takes the pilot at once (no opening shell)");
        helper.assertTrue(armor.shouldRiderSit(), "The pilot sits in the cab");
        helper.runAtTickTime(3, () -> {
            double eyes = pilot.getEyeY() - armor.getY();
            helper.assertTrue(eyes > 2.0 && eyes < 2.45, "The eyes are high in the cab: " + eyes);
            helper.succeed();
        });
    }

    /** Version 1.5.2: the worker hammers while its pilot breaks a block, and stops when the pilot stops swinging. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void theWorkerJackhammerRunsWhileMining(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.WORKER);
        ServerPlayer pilot = playerBeside(helper, armor);
        helper.assertTrue(pilot.startRiding(armor, true), "The pilot gets in");
        armor.setMining(true);
        helper.assertTrue(armor.isMining(), "Breaking a block starts the jackhammer");
        PowerArmorEntity combat = armor(helper, ArmorVariant.COMBAT);
        combat.setMining(true);
        helper.assertFalse(combat.isMining(), "Only the worker has one");
        helper.runAtTickTime(30, () -> {
            helper.assertFalse(armor.isMining(), "No swing for a while: it stops");
            helper.succeed();
        });
    }

    private static void checkFaces(GameTestHelper helper, String id) throws Exception {
        JsonObject geometry;
        try (InputStream in = ArmorShellGameTests.class.getResourceAsStream("/assets/projectrebirth/geo/entity/" + id + ".geo.json")) {
            geometry = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        }
        Map<String, String> joints = Map.of("right_shin", "right_leg", "left_shin", "left_leg", "right_forearm", "right_arm", "left_forearm", "left_arm");
        List<String> bones = new ArrayList<>();
        List<double[]> boxes = new ArrayList<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            if (name.startsWith("slot_") || name.startsWith("upgrade_") || !bone.has("cubes")) {
                continue;
            }
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                JsonObject cube = c.getAsJsonObject();
                if (cube.has("rotation")) {
                    continue;
                }
                double[] box = new double[6];
                for (int k = 0; k < 3; k++) {
                    box[k] = cube.getAsJsonArray("origin").get(k).getAsDouble();
                    box[k + 3] = box[k] + cube.getAsJsonArray("size").get(k).getAsDouble();
                }
                bones.add(name);
                boxes.add(box);
            }
        }
        List<String> found = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                double[] a = boxes.get(i), b = boxes.get(j);
                boolean cut = bones.get(i).equals(joints.get(bones.get(j))) || bones.get(j).equals(joints.get(bones.get(i)));
                for (int axis = 0; axis < 3; axis++) {
                    int u = (axis + 1) % 3, v = (axis + 2) % 3;
                    if (Math.min(a[u + 3], b[u + 3]) - Math.max(a[u], b[u]) <= 0.001 || Math.min(a[v + 3], b[v + 3]) - Math.max(a[v], b[v]) <= 0.001) {
                        continue;
                    }
                    for (double pa : new double[] {a[axis], a[axis + 3]}) {
                        for (double pb : new double[] {b[axis], b[axis + 3]}) {
                            // a cube cut at a joint: its two halves meet in the joint plane, inside the cube
                            boolean jointCut = cut && axis == 1 && Math.abs(pa - pb) < 0.001 && (pa == a[4] && pb == b[1] || pa == a[1] && pb == b[4]);
                            if (Math.abs(pa - pb) < 0.001 && !jointCut) {
                                found.add(bones.get(i) + " / " + bones.get(j) + " at " + "xyz".charAt(axis) + "=" + pa);
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(found.isEmpty(), id + ": " + found.size() + " faces lie over each other: " + found.subList(0, Math.min(5, found.size())));
        // and its texture is painted (a texture saved before Blockbench had drawn it came out empty once)
        try (InputStream in = ArmorShellGameTests.class.getResourceAsStream("/assets/projectrebirth/textures/entity/" + id + ".png")) {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(in);
            int painted = 0;
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    painted += (image.getRGB(x, y) >>> 24) > 0 ? 1 : 0;
                }
            }
            helper.assertTrue(painted > 2000, id + ": the texture is painted: " + painted + " pixels");
        }
    }
}
