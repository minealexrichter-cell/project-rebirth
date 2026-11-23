package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.wanderers.WandererEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * 1.9.0, block E: how wanderers look. A human in power armor wears the model of its armor variant, an android the model of
 * its body class, a wandering robot its own model (all GeckoLib, with the held weapon in the hand); a human in protective
 * gear is a vanilla humanoid in one of the wanderer skins, its gear and armor drawn as a player's.
 */
public final class WandererRenderer {
    private WandererRenderer() {
    }

    /** The armored, android and robot wanderers. */
    public static class Geo extends GeoEntityRenderer<WandererEntity> {
        public Geo(EntityRendererProvider.Context context, float shadow) {
            super(context, new Model());
            this.shadowRadius = shadow;
            addRenderLayer(new HeldItemLayer(this));
        }

        private java.util.Map<String, Integer> armorPlating = java.util.Map.of();
        private int bodyPlating = UpgradeVisuals.NO_TINT;
        @javax.annotation.Nullable
        private PoseStack.Pose robotMuzzle;

        /** Only the parts of its own upgrades show (the models hold the parts of them all). */
        @Override
        public void preRender(PoseStack poseStack, WandererEntity wanderer, software.bernie.geckolib.cache.object.BakedGeoModel model, MultiBufferSource bufferSource,
                              VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            armorPlating = java.util.Map.of();
            bodyPlating = UpgradeVisuals.NO_TINT;
            if (wanderer.kind() == WandererEntity.Kind.ARMORED) {
                armorPlating = UpgradeVisuals.applyToArmor(getGeoModel(), wanderer.registryAccess(), wanderer.upgrades());
            } else if (wanderer.kind() == WandererEntity.Kind.ANDROID) {
                bodyPlating = UpgradeVisuals.applyToBody(getGeoModel(), wanderer.registryAccess(), wanderer.upgrades());
            }
            super.preRender(poseStack, wanderer, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }

        @Override
        public void renderRecursively(PoseStack poseStack, WandererEntity wanderer, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                      VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            if (!isReRender && wanderer.kind() == WandererEntity.Kind.ROBOT && bone.getName().equals("right_arm")
                    && MuzzleFlash.age(wanderer.getId(), partialTick) < 1.0f) {
                robotMuzzle = MuzzleFlash.capture(poseStack, bone);
            }
            int tint = bone.getName().startsWith(UpgradeVisuals.BODY_PLATING_PREFIX) ? bodyPlating : armorPlating.getOrDefault(bone.getName(), colour);
            super.renderRecursively(poseStack, wanderer, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, tint);
        }

        @Override
        public void render(WandererEntity wanderer, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            robotMuzzle = null;
            super.render(wanderer, entityYaw, partialTick, poseStack, buffers, light);
            if (robotMuzzle != null) {
                // the end of the carbine's barrel, from the shoulder the arm turns at
                PoseStack at = new PoseStack();
                at.last().pose().set(robotMuzzle.pose());
                at.last().normal().set(robotMuzzle.normal());
                at.translate(1.0f / 16.0f, -7.5f / 16.0f, -9.0f / 16.0f);
                MuzzleFlash.draw(at, buffers, MuzzleFlash.age(wanderer.getId(), partialTick), MuzzleFlash.seed(wanderer.getId()), 0.7f);
                robotMuzzle = null;
            }
        }
    }

    private static String id(WandererEntity wanderer) {
        return switch (wanderer.kind()) {
            case ARMORED -> ArmorVariant.byId(wanderer.variant()).itemId();
            case ANDROID -> wanderer.bodyClass().id();
            default -> "wanderer_robot";
        };
    }

    private static class Model extends GeoModel<WandererEntity> {
        @Override
        public ResourceLocation getModelResource(WandererEntity wanderer) {
            return ProjectRebirth.id("geo/entity/" + id(wanderer) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(WandererEntity wanderer) {
            return ProjectRebirth.id("textures/entity/" + id(wanderer) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(WandererEntity wanderer) {
            return ProjectRebirth.id("animations/entity/" + id(wanderer) + ".animation.json");
        }

        @Override
        public void setCustomAnimations(WandererEntity wanderer, long instanceId, AnimationState<WandererEntity> state) {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            GeoBone head = getAnimationProcessor().getBone("head");
            if (data != null && head != null) {
                head.setRotY(Mth.clamp(data.netHeadYaw(), -60.0f, 60.0f) * Mth.DEG_TO_RAD);
                head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
            }
            // an armor holds its weapon as an armor with a pilot does: at the hip, raised to aim in a fight
            if (wanderer.kind() == WandererEntity.Kind.ARMORED && wanderer.getMainHandItem().getItem() instanceof com.projectrebirth.weapons.WeaponItem) {
                GeoBone rightArm = getAnimationProcessor().getBone("right_arm");
                GeoBone leftArm = getAnimationProcessor().getBone("left_arm");
                if (rightArm != null && leftArm != null) {
                    ArmorWeaponPose.applyWanderer(wanderer, state.getPartialTick(), rightArm, leftArm,
                            getAnimationProcessor().getBone("right_forearm"), getAnimationProcessor().getBone("left_forearm"));
                }
            }
            // an android moves its arms as a player's body does: the swing, the bow drawn, the crossbow charged and held
            if (wanderer.kind() == WandererEntity.Kind.ANDROID) {
                GeoBone rightArm = getAnimationProcessor().getBone("right_arm");
                GeoBone leftArm = getAnimationProcessor().getBone("left_arm");
                if (rightArm != null && leftArm != null) {
                    PilotArmPose.apply(wanderer, state.getPartialTick(), rightArm, leftArm, getAnimationProcessor().getBone("right_forearm"),
                            getAnimationProcessor().getBone("left_forearm"), getAnimationProcessor().getBone("torso"));
                }
            }
        }
    }

    /** The weapon in the fist of an armor (right_hand) or the hand of an android (right_forearm); a shield in the other. */
    private static class HeldItemLayer extends BlockAndItemGeoLayer<WandererEntity> {
        /** How far the grip of a weapon lies above the middle of an armor fist, in blocks (as PowerArmorRenderer). */
        private static final float WEAPON_IN_FIST = 0.1f;

        HeldItemLayer(Geo renderer) {
            super(renderer);
        }

        private static boolean right(GeoBone bone) {
            return bone.getName().equals("right_hand") || bone.getName().equals("right_forearm");
        }

        private static boolean left(GeoBone bone) {
            return bone.getName().equals("left_hand") || bone.getName().equals("left_forearm");
        }

        @Override
        protected ItemStack getStackForBone(GeoBone bone, WandererEntity wanderer) {
            if (wanderer.kind() == WandererEntity.Kind.ROBOT || !right(bone) && !left(bone)) {
                return null;
            }
            boolean armored = wanderer.kind() == WandererEntity.Kind.ARMORED;
            if (bone.getName().endsWith(armored ? "_forearm" : "_hand")) {
                return null;
            }
            ItemStack stack = right(bone) ? wanderer.getMainHandItem() : wanderer.getOffhandItem();
            return stack.isEmpty() ? null : stack;
        }

        /** An android: the arm is already turned, only move to the elbow (as AndroidRenderer). An armor: GeckoLib's own fist transform (as PowerArmorRenderer). */
        @Override
        public void renderForBone(PoseStack poseStack, WandererEntity wanderer, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (wanderer.kind() == WandererEntity.Kind.ARMORED) {
                super.renderForBone(poseStack, wanderer, bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
                return;
            }
            ItemStack stack = getStackForBone(bone, wanderer);
            if (stack == null) {
                return;
            }
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, bone);
            renderStackForBone(poseStack, bone, stack, wanderer, bufferSource, partialTick, packedLight, packedOverlay);
            poseStack.popPose();
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, WandererEntity wanderer,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            boolean leftHand = left(bone);
            boolean armored = wanderer.kind() == WandererEntity.Kind.ARMORED;
            if (armored && stack.getItem() instanceof com.projectrebirth.weapons.WeaponItem) {
                // the weapon from the fist, pointing ahead, as an armor holds it for its pilot
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotation(-ArmorWeaponPose.weaponCorrection()));
                poseStack.translate(0.0f, WEAPON_IN_FIST, 0.0f);
                WeaponView view = WeaponView.of(wanderer, stack.getItem());
                WeaponRenderer.INSTANCE.draw(poseStack, view, bufferSource, packedLight, partialTick, WindowPose.isDrawing() ? WeaponRenderer.Space.NONE : WeaponRenderer.Space.WORLD);
                poseStack.popPose();
                return;
            }
            // GeckoLib space -> vanilla model space (upside down), then the vanilla shoulder-to-hand transform
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            float down = armored || bone.getParent() == null ? 0.0f : (bone.getParent().getPivotY() - bone.getPivotY()) / 16.0f - 0.625f;
            poseStack.translate((leftHand ? -1.0f : 1.0f) / 16.0f, 0.125f, down);
            WeaponItemRenderer.setHolder(wanderer);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(wanderer, stack,
                    leftHand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, leftHand, poseStack, bufferSource, packedLight);
            WeaponItemRenderer.setHolder(null);
        }
    }

    /** The human in protective gear: a vanilla humanoid in a wanderer skin, its gear as a player wears it. */
    public static class Survivor extends HumanoidMobRenderer<WandererEntity, HumanoidModel<WandererEntity>> {
        public Survivor(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
            addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                    new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), context.getModelManager()));
        }

        /** The arms as a player's: a drawn bow, a crossbow charged and held. */
        @Override
        public void render(WandererEntity wanderer, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            model.rightArmPose = PilotArmPose.sidePose(wanderer, net.minecraft.world.entity.HumanoidArm.RIGHT);
            model.leftArmPose = PilotArmPose.sidePose(wanderer, net.minecraft.world.entity.HumanoidArm.LEFT);
            super.render(wanderer, yaw, partialTick, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(WandererEntity wanderer) {
            String skin = wanderer.variant().isEmpty() ? "a" : wanderer.variant();
            return ProjectRebirth.id("textures/entity/wanderer_survivor_" + skin + ".png");
        }
    }
}
