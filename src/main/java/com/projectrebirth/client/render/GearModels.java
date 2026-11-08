package com.projectrebirth.client.render;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.environment.EnvironmentRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The 3D models of the protective gear: the vanilla armor model with extra cubes (masks, filters, straps, belts, gloves,
 * boots) read from assets/projectrebirth/gear/*.json. The JSON and the textures are written together by
 * blockbench/tools/build_gear.js, so their layouts agree. Each piece of gear gets the model of its layer: the leggings the
 * inner one, everything else the outer one, as in vanilla.
 */
public final class GearModels {
    private enum Kind {
        RESPIRATOR("respirator"),
        HAZMAT_OUTER("hazmat_layer_1"),
        HAZMAT_INNER("hazmat_layer_2"),
        HEAVY_OUTER("heavy_suit_layer_1"),
        HEAVY_INNER("heavy_suit_layer_2");

        private final String file;
        private final ModelLayerLocation layer;

        Kind(String file) {
            this.file = file;
            this.layer = new ModelLayerLocation(ProjectRebirth.id("gear_" + file), "main");
        }
    }

    private static final Map<Kind, HumanoidModel<LivingEntity>> MODELS = new EnumMap<>(Kind.class);

    private GearModels() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GearModels::registerLayers);
        modEventBus.addListener(GearModels::registerExtensions);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (Kind kind : Kind.values()) {
            event.registerLayerDefinition(kind.layer, () -> create(kind.file));
        }
        MODELS.clear();
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(extension(Kind.RESPIRATOR, Kind.RESPIRATOR), EnvironmentRegistry.RESPIRATOR.get());
        event.registerItem(extension(Kind.HAZMAT_OUTER, Kind.HAZMAT_INNER), EnvironmentRegistry.HAZMAT_HELMET.get(),
                EnvironmentRegistry.HAZMAT_SUIT.get(), EnvironmentRegistry.HAZMAT_TROUSERS.get(), EnvironmentRegistry.HAZMAT_BOOTS.get());
        event.registerItem(extension(Kind.HEAVY_OUTER, Kind.HEAVY_INNER), EnvironmentRegistry.HEAVY_SUIT_HELMET.get(),
                EnvironmentRegistry.HEAVY_SUIT.get(), EnvironmentRegistry.HEAVY_SUIT_TROUSERS.get(), EnvironmentRegistry.HEAVY_SUIT_BOOTS.get());
    }

    private static IClientItemExtensions extension(Kind outer, Kind inner) {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return model(slot == EquipmentSlot.LEGS ? inner : outer);
            }
        };
    }

    private static HumanoidModel<LivingEntity> model(Kind kind) {
        return MODELS.computeIfAbsent(kind, key -> new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(key.layer)));
    }

    /** The vanilla armor parts, inflated as the JSON says, and the extra cubes as children of their parts. */
    static LayerDefinition create(String file) {
        JsonObject json = read(file);
        JsonArray texture = json.getAsJsonArray("texture");
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(json.get("inflate").getAsFloat()), 0.0f);
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> bones = new HashMap<>();
        for (String bone : new String[] {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            bones.put(bone, root.getChild(bone));
        }
        for (JsonElement element : json.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            float[] uv = floats(part, "uv");
            float[] origin = floats(part, "origin");
            float[] size = floats(part, "size");
            float[] pivot = floats(part, "pivot");
            float[] rotation = floats(part, "rotation");
            PartDefinition bone = bones.get(part.get("bone").getAsString());
            if (bone == null) {
                throw new IllegalStateException("Unknown bone in gear model " + file + ": " + part.get("bone"));
            }
            bone.addOrReplaceChild(part.get("name").getAsString(),
                    CubeListBuilder.create().texOffs((int) uv[0], (int) uv[1])
                            .addBox(origin[0], origin[1], origin[2], size[0], size[1], size[2], new CubeDeformation(part.get("grow").getAsFloat())),
                    PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2],
                            rotation[0] * Mth.DEG_TO_RAD, rotation[1] * Mth.DEG_TO_RAD, rotation[2] * Mth.DEG_TO_RAD));
        }
        return LayerDefinition.create(mesh, texture.get(0).getAsInt(), texture.get(1).getAsInt());
    }

    private static float[] floats(JsonObject part, String key) {
        JsonArray array = part.getAsJsonArray(key);
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    static JsonObject read(String file) {
        try (InputStream in = GearModels.class.getResourceAsStream("/assets/projectrebirth/gear/" + file + ".json")) {
            if (in == null) {
                throw new IllegalStateException("No gear model " + file);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read gear model " + file, e);
        }
    }
}
