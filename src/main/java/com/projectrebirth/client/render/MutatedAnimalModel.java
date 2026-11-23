package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.hostile.MutatedAnimalEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 1.9.0, block C: the mutated farm animals, a cow, a pig and a chicken changed by the Wasteland: the build of the animal with
 * growths, a crooked third horn, tusks, a second small head. Built here as vanilla models are (no GeckoLib: they move as the
 * animals do); their textures are painted by blockbench/tools/build_textures.js (textures/entity/mutated_&lt;kind&gt;.png, 64×64).
 * The growths are on separate boxes, offset from the faces they sit on, so nothing flickers.
 */
public class MutatedAnimalModel extends HierarchicalModel<MutatedAnimalEntity> {
    public static final ModelLayerLocation COW = new ModelLayerLocation(ProjectRebirth.id("mutated_cow"), "main");
    public static final ModelLayerLocation PIG = new ModelLayerLocation(ProjectRebirth.id("mutated_pig"), "main");
    public static final ModelLayerLocation CHICKEN = new ModelLayerLocation(ProjectRebirth.id("mutated_chicken"), "main");
    private static final float HALF_PI = (float) (Math.PI / 2);

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart[] legs;
    private final ModelPart[] wings;
    private final boolean bird;

    public MutatedAnimalModel(ModelPart root, boolean bird) {
        this.root = root;
        this.head = root.getChild("head");
        this.bird = bird;
        if (bird) {
            legs = new ModelPart[]{root.getChild("right_leg"), root.getChild("left_leg")};
            wings = new ModelPart[]{root.getChild("right_wing"), root.getChild("left_wing")};
        } else {
            legs = new ModelPart[]{root.getChild("right_hind_leg"), root.getChild("left_hind_leg"), root.getChild("right_front_leg"), root.getChild("left_front_leg")};
            wings = new ModelPart[0];
        }
    }

    public static LayerDefinition createCow() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, -6.0f, 8.0f, 8.0f, 6.0f)
                        .texOffs(22, 0).addBox(-5.0f, -5.0f, -4.0f, 1.0f, 3.0f, 1.0f)
                        .texOffs(22, 0).addBox(4.0f, -5.0f, -4.0f, 1.0f, 3.0f, 1.0f)
                        // a crooked third horn and a lump on the jaw
                        .texOffs(0, 32).addBox(-0.5f, -7.0f, -3.5f, 1.0f, 3.5f, 1.0f)
                        .texOffs(4, 32).addBox(1.5f, 1.5f, -6.5f, 3.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, 4.0f, -8.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(18, 4).addBox(-6.0f, -10.0f, -7.0f, 12.0f, 18.0f, 10.0f)
                        .texOffs(52, 0).addBox(-2.0f, 2.0f, -8.0f, 4.0f, 6.0f, 1.0f)
                        // growths on the back and the side
                        .texOffs(0, 40).addBox(-3.0f, -6.0f, -9.5f, 5.0f, 6.0f, 3.0f)
                        .texOffs(16, 40).addBox(-7.5f, -2.0f, -5.0f, 2.0f, 5.0f, 4.0f)
                        .texOffs(28, 40).addBox(1.0f, 2.0f, -9.0f, 3.0f, 3.0f, 2.5f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 2.0f, HALF_PI, 0.0f, 0.0f));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 16).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-4.0f, 12.0f, 7.0f));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(4.0f, 12.0f, 7.0f));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-4.0f, 12.0f, -6.0f));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(4.0f, 12.0f, -6.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition createPig() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, -8.0f, 8.0f, 8.0f, 8.0f)
                        .texOffs(16, 16).addBox(-2.0f, 0.0f, -9.0f, 4.0f, 3.0f, 1.0f)
                        // tusks and a swollen brow
                        .texOffs(0, 32).addBox(-3.5f, 1.0f, -10.0f, 1.0f, 3.0f, 2.5f)
                        .texOffs(0, 32).addBox(2.5f, 1.0f, -10.0f, 1.0f, 3.0f, 2.5f)
                        .texOffs(4, 32).addBox(-3.0f, -5.5f, -7.0f, 4.0f, 2.0f, 3.0f),
                PartPose.offset(0.0f, 12.0f, -6.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(28, 8).addBox(-5.0f, -10.0f, -7.0f, 10.0f, 16.0f, 8.0f)
                        // a row of bumps along the spine and a lump on the flank
                        .texOffs(0, 40).addBox(-1.5f, -8.0f, -9.0f, 3.0f, 12.0f, 2.5f)
                        .texOffs(20, 40).addBox(4.5f, -4.0f, -4.0f, 2.0f, 4.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 11.0f, 2.0f, HALF_PI, 0.0f, 0.0f));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 16).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-3.0f, 18.0f, 7.0f));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(3.0f, 18.0f, 7.0f));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-3.0f, 18.0f, -5.0f));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(3.0f, 18.0f, -5.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition createChicken() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0f, -6.0f, -2.0f, 4.0f, 6.0f, 3.0f)
                        .texOffs(14, 0).addBox(-2.0f, -4.0f, -4.0f, 4.0f, 2.0f, 2.0f)
                        .texOffs(14, 4).addBox(-1.0f, -2.0f, -3.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offset(0.0f, 15.0f, -4.0f));
        // a second, smaller head grown from the side of the neck
        head.addOrReplaceChild("second_head", CubeListBuilder.create()
                        .texOffs(0, 32).addBox(0.0f, -4.0f, -1.5f, 3.0f, 4.0f, 3.0f)
                        .texOffs(12, 32).addBox(0.5f, -3.0f, -3.0f, 2.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(2.2f, -0.5f, 0.0f, 0.0f, 0.0f, 0.45f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 9).addBox(-3.0f, -4.0f, -3.0f, 6.0f, 8.0f, 6.0f)
                        .texOffs(0, 40).addBox(-2.0f, -2.0f, -4.5f, 4.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 16.0f, 0.0f, HALF_PI, 0.0f, 0.0f));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(26, 0).addBox(-1.0f, 0.0f, -3.0f, 3.0f, 5.0f, 3.0f);
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-2.0f, 19.0f, 1.0f));
        root.addOrReplaceChild("left_leg", leg, PartPose.offset(1.0f, 19.0f, 1.0f));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(24, 13).addBox(0.0f, 0.0f, -3.0f, 1.0f, 4.0f, 6.0f), PartPose.offset(-4.0f, 13.0f, 0.0f));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(24, 13).addBox(-1.0f, 0.0f, -3.0f, 1.0f, 4.0f, 6.0f), PartPose.offset(4.0f, 13.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(MutatedAnimalEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        // a twitch of the head now and then: something is wrong with it
        head.zRot = Mth.sin(ageInTicks * 0.13f) * 0.06f;
        float swing = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        for (int i = 0; i < legs.length; i++) {
            legs[i].xRot = i % 2 == (i < 2 ? 0 : 1) ? swing : -swing;
        }
        if (bird) {
            float flap = entity.onGround() ? Mth.sin(ageInTicks * 0.3f) * 0.08f : Mth.sin(ageInTicks * 1.4f) * 0.9f + 0.9f;
            wings[0].zRot = flap;
            wings[1].zRot = -flap;
        }
    }
}
