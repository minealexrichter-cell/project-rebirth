package com.projectrebirth.client.multiblock;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorItem;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.GeoModel;

/**
 * The machine under assembly in the zone of a multiblock (1.7.0, block B): the model of the body or armor being built, with only
 * the parts of the stages reached. Which bone comes with which stage is in {@code assets/projectrebirth/assembly_stages/<model>.json}
 * ({@code {"bones": {"torso": 1, "head": 3}}}); a bone not listed is always there. The models are shared by all entities, so the
 * bones hidden for a preview are shown again before any other entity is drawn. Client only.
 */
public final class AssemblyPreview {
    private static final Map<Item, Entity> ENTITIES = new HashMap<>();
    private static final Map<Entity, Integer> STAGES = new WeakHashMap<>();
    private static final Map<GeoModel<?>, Set<String>> HIDDEN = new IdentityHashMap<>();
    private static final Map<String, Map<String, Integer>> BONES = new HashMap<>();

    private AssemblyPreview() {
    }

    /** The entity that shows a result, made once per item; null for things that are no body and no armor. */
    @Nullable
    public static Entity entityFor(ItemStack result, ClientLevel level) {
        Item item = result.getItem();
        Entity entity = ENTITIES.get(item);
        if (entity != null && entity.level() == level) {
            return entity;
        }
        if (item instanceof ArtificialBodyItem body) {
            entity = BodyEntities.type(body.bodyClass()).create(level);
        } else if (item instanceof PowerArmorItem armor) {
            PowerArmorEntity created = PowerArmorRegistry.POWER_ARMOR.get().create(level);
            if (created != null) {
                created.setVariant(armor.variant());
            }
            entity = created;
        } else if (item instanceof com.projectrebirth.garrison.PackedMachineItem machine && machine != com.projectrebirth.garrison.GarrisonRegistry.TEST_MACHINE_ITEM.get()) {
            // 1.7.0, block G: turrets (and later the mech)
            entity = machine.entityType().create(level);
        } else {
            entity = null;
        }
        if (entity != null) {
            ENTITIES.put(item, entity);
        }
        return entity;
    }

    /** Draws the machine at the pose's origin, turned that way, with the parts of the given model stage. */
    public static void render(Entity entity, int stage, float yaw, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
        STAGES.put(entity, stage);
        try {
            Minecraft.getInstance().getEntityRenderDispatcher().render(entity, 0, 0, 0, yaw, partialTick, pose, buffers, light);
        } finally {
            STAGES.remove(entity);
        }
    }

    /**
     * Called by the renderers of bodies and armor before a model is drawn: shows again what a preview hid, then, for a preview,
     * hides the bones of the stages not reached yet.
     */
    public static void apply(GeoModel<?> model, Entity entity) {
        Set<String> hidden = HIDDEN.remove(model);
        if (hidden != null) {
            for (String bone : hidden) {
                model.getBone(bone).ifPresent(found -> found.setHidden(false));
            }
        }
        Integer stage = STAGES.get(entity);
        if (stage == null) {
            return;
        }
        Set<String> now = new HashSet<>();
        for (Map.Entry<String, Integer> entry : bones(entity).entrySet()) {
            if (entry.getValue() > stage) {
                model.getBone(entry.getKey()).ifPresent(found -> {
                    found.setHidden(true);
                    now.add(entry.getKey());
                });
            }
        }
        HIDDEN.put(model, now);
    }

    private static Map<String, Integer> bones(Entity entity) {
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String name = entity instanceof PowerArmorEntity armor ? armor.getVariant().id() + "_power_armor" : type.getPath();
        return BONES.computeIfAbsent(name, AssemblyPreview::load);
    }

    private static Map<String, Integer> load(String name) {
        Map<String, Integer> out = new HashMap<>();
        for (String file : new String[] {name, "default"}) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(ProjectRebirth.id("assembly_stages/" + file + ".json"));
            if (resource.isEmpty()) {
                continue;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject bones = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("bones");
                for (String bone : bones.keySet()) {
                    out.put(bone, bones.get(bone).getAsInt());
                }
                return out;
            } catch (Exception e) {
                ProjectRebirth.LOGGER.warn("Bad assembly stages {}: {}", file, e.toString());
            }
        }
        return out;
    }

    /** A resource reload may bring other stages. */
    public static void clear() {
        BONES.clear();
        ENTITIES.clear();
    }
}
