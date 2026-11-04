package com.projectrebirth.client;

import org.lwjgl.glfw.GLFW;

import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.render.ArmorArmRenderer;
import com.projectrebirth.client.render.HeavyWeaponPose;
import com.projectrebirth.client.render.TracerParticle;
import com.projectrebirth.client.render.WeaponItemRenderer;
import com.projectrebirth.client.render.WeaponRenderer;
import com.projectrebirth.client.render.WeaponView;
import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.weapons.WeaponDefinition;
import com.projectrebirth.weapons.WeaponInputPayload;
import com.projectrebirth.weapons.WeaponItem;
import com.projectrebirth.weapons.WeaponRegistry;
import com.projectrebirth.weapons.WeaponShotPayload;
import com.projectrebirth.weapons.Weapons;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The pilot's side of the guns. Keys are only reported to the server, which decides what they do. Everything else here
 * is for the eye: zoom, recoil, the HUD and the effects of shots, also of other players' shots.
 */
public final class WeaponClient {
    private static final KeyMapping RELOAD_KEY = new KeyMapping("key.projectrebirth.weapon_reload",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.projectrebirth");
    private static final int TRACER_COLOR = 0xFFE9A0;
    private static final int FLASH_COLOR = 0xFFD070;
    private static final float TRACER_WIDTH = 0.035f;
    private static final float ZOOM_SPEED = 0.34f;
    private static final float RECOIL_PER_TICK = 0.5f;
    private static final float RECOIL_MEMORY_DECAY = 0.85f;
    private static final float DEGREES_TO_PIXELS = 2.5f;
    private static final int RETICLE_LENGTH = 4;
    private static final int RETICLE_COLOR = 0xE0FFFFFF;
    private static final int MARGIN = 6;
    private static final float CASE_SCALE = 0.35f;
    private static final float HEAVY_HAND_X = 0.62f;
    private static final float HEAVY_HAND_Y = -0.62f;
    private static final float HEAVY_HAND_Z = -0.72f;
    private static final float HEAVY_HAND_YAW = 50.0f;
    private static final float HEAVY_HAND_PITCH = -14.0f;
    private static final float HEAVY_HAND_SCALE = 0.6f;
    // First person in an armor, in the units of the view; the weapon's own lengths are in its model.
    private static final float GUN_SCALE = 0.42f;
    private static final Vector3f GUN_AT_HIP = new Vector3f(0.42f, -0.42f, -0.85f);
    private static final Vector3f GUN_AT_EYE = new Vector3f(0.0f, -0.29f, -0.72f);
    private static final float HIP_YAW = 6.0f;
    private static final float HIP_PITCH = 2.0f;
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(0.95f, -1.15f, 0.1f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(-0.75f, -1.25f, 0.0f);
    /** Grip of the weapon hand, in the weapon's own blocks. */
    private static final Vector3f GRIP = new Vector3f(0.0f, -0.15f, 0.25f);
    private static final Vector3f MAGAZINE_WELL = new Vector3f(0.0f, -0.55f, -0.15f);
    private static final Map<String, Vector3f> SUPPORT = Map.of(
            "assault_rifle", new Vector3f(0.0f, -0.05f, -0.65f),
            "battle_rifle", new Vector3f(0.0f, -0.05f, -0.75f),
            "combat_shotgun", new Vector3f(0.0f, -0.05f, -0.68f));

    private static boolean triggerDown;
    private static boolean aimDown;
    private static float zoom;
    private static float zoomOld;
    /** Degrees of recoil not yet applied to the view. */
    private static float pendingRecoil;
    /** How far a burst has built up its recoil. */
    private static float burstRecoil;
    /** The spread a burst has added, as the server counts it; only for the reticle. */
    private static float extraSpread;
    private static ArmorArmRenderer armArms;

    private WeaponClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(WeaponClient::registerKeys);
        modEventBus.addListener(WeaponClient::registerGuiLayers);
        // Before PowerArmorClient hears the attack key: a pilot with a weapon shoots instead of punching.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, WeaponClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(WeaponClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(WeaponClient::onComputeFov);
        NeoForge.EVENT_BUS.addListener(WeaponClient::onTurn);
        NeoForge.EVENT_BUS.addListener(WeaponClient::hideVanillaCrosshair);
        modEventBus.addListener(WeaponClient::registerItemExtensions);
        NeoForge.EVENT_BUS.addListener(WeaponClient::renderHeavyWeaponInHand);
        // Before PowerArmorClient draws its empty hands.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, WeaponClient::renderWeaponInArmor);
        // Last: a pilot or a body is drawn by other renderers, and they cancel this event.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderPlayerEvent.Pre.class, WeaponClient::leanCarrier);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderPlayerEvent.Post.class, WeaponClient::endLean);
        WeaponShotPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> onShot(payload));
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(RELOAD_KEY);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ProjectRebirth.id("weapon_reticle"), WeaponClient::renderReticle);
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("weapon_status"), WeaponClient::renderStatus);
    }

    private static boolean readyToShoot(Player player) {
        return player != null && Weapons.holdsWeapon(player) && Weapons.isPilotOfWorkingArmor(player);
    }

    /** Without a power armor a weapon is only carried: see HeavyWeaponPose. */
    private static boolean carries(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof WeaponItem && !(entity.getVehicle() instanceof PowerArmorEntity);
    }

    private static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private WeaponItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new WeaponItemRenderer();
                }
                return renderer;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return hand == InteractionHand.MAIN_HAND && carries(entity) ? HeavyWeaponPose.POSE.getValue() : null;
            }
        }, WeaponRegistry.ASSAULT_RIFLE.get(), WeaponRegistry.BATTLE_RIFLE.get(), WeaponRegistry.COMBAT_SHOTGUN.get());
    }

    private static void leanCarrier(RenderPlayerEvent.Pre event) {
        if (carries(event.getEntity())) {
            WeaponItemRenderer.setHolder(event.getEntity());
            event.getPoseStack().pushPose();
            HeavyWeaponPose.leanWholeFigure(event.getPoseStack(), event.getEntity(), event.getPartialTick());
        }
    }

    private static void endLean(RenderPlayerEvent.Post event) {
        if (carries(event.getEntity())) {
            event.getPoseStack().popPose();
            WeaponItemRenderer.setHolder(null);
        }
    }

    /** In first person a carried weapon hangs low at the edge of the screen; a punch only jolts it. */
    private static void renderHeavyWeaponInHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND || !carries(player)) {
            return;
        }
        event.setCanceled(true);
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        float side = left ? -1.0f : 1.0f;
        float sway = Mth.sin((player.tickCount + event.getPartialTick()) * 0.12f) * Math.min(1.0f, player.walkAnimation.speed());
        float jolt = Mth.sin(event.getSwingProgress() * Mth.PI);
        PoseStack pose = event.getPoseStack();
        WeaponRenderer.INSTANCE.beginHand(pose);
        pose.pushPose();
        pose.translate(side * (HEAVY_HAND_X + sway * 0.02f), HEAVY_HAND_Y + sway * 0.015f - event.getEquipProgress() * 0.6f + jolt * 0.08f, HEAVY_HAND_Z - jolt * 0.1f);
        pose.mulPose(Axis.YP.rotationDegrees(side * HEAVY_HAND_YAW));
        pose.mulPose(Axis.XP.rotationDegrees(HEAVY_HAND_PITCH + jolt * 12.0f));
        pose.scale(HEAVY_HAND_SCALE, HEAVY_HAND_SCALE, HEAVY_HAND_SCALE);
        WeaponView view = WeaponView.of(player, player.getMainHandItem().getItem());
        view.update(player, event.getPartialTick());
        WeaponRenderer.INSTANCE.draw(pose, view, event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick(), WeaponRenderer.Space.VIEW);
        pose.popPose();
    }

    /** A pilot's view of their weapon: both armor arms hold it, wherever it goes. */
    private static void renderWeaponInArmor(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !Weapons.holdsWeapon(player) || !(player.getVehicle() instanceof PowerArmorEntity armor) || armor.isWrecked()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        float partialTick = event.getPartialTick();
        WeaponView view = WeaponView.of(player, stack.getItem());
        view.update(player, partialTick);
        float aim = view.aimAmount();
        float kick = view.kick();
        float side = player.getMainArm() == HumanoidArm.LEFT ? -1.0f : 1.0f;
        Vector3f place = new Vector3f(GUN_AT_HIP).lerp(GUN_AT_EYE, aim);
        place.x *= side;
        place.z += kick * 0.05f + event.getEquipProgress() * 0.2f;
        place.y -= event.getEquipProgress() * 0.5f;
        Matrix4f gun = new Matrix4f().translate(place)
                .rotateY(side * HIP_YAW * (1.0f - aim) * Mth.DEG_TO_RAD)
                .rotateX((HIP_PITCH * (1.0f - aim) + kick * 2.5f) * Mth.DEG_TO_RAD)
                .scale(GUN_SCALE);

        PoseStack pose = event.getPoseStack();
        WeaponRenderer.INSTANCE.beginHand(pose);
        pose.pushPose();
        pose.mulPose(gun);
        WeaponRenderer.INSTANCE.draw(pose, view, event.getMultiBufferSource(), event.getPackedLight(), partialTick, WeaponRenderer.Space.VIEW);
        pose.popPose();

        Vector3f mainFist = gun.transformPosition(new Vector3f(GRIP));
        Vector3f support = SUPPORT.getOrDefault(view.id(), SUPPORT.get("assault_rifle"));
        Vector3f otherFist = gun.transformPosition(new Vector3f(support));
        if (view.isReloading()) {
            long end = armor.weaponReloadEnd();
            RebirthConfig.WeaponStats stats = Weapons.statsOf(stack.getItem());
            float done = stats == null ? 0.0f : Mth.clamp(1.0f - (end - player.level().getGameTime() - partialTick) / stats.reloadTicks().get(), 0.0f, 1.0f);
            Vector3f well = gun.transformPosition(new Vector3f(MAGAZINE_WELL));
            otherFist.lerp(well, Mth.sin(done * Mth.PI));
        }
        HumanoidArm mainSide = player.getMainArm();
        HumanoidArm otherSide = mainSide.getOpposite();
        if (armArms == null) {
            armArms = new ArmorArmRenderer();
        }
        Vector3f mainShoulder = new Vector3f(RIGHT_SHOULDER);
        Vector3f otherShoulder = new Vector3f(LEFT_SHOULDER);
        mainShoulder.x *= side;
        otherShoulder.x *= side;
        armArms.renderReaching(pose, event.getMultiBufferSource(), event.getPackedLight(), mainSide, armor.getVariant(),
                PowerArmorClient.plateColor(armor, mainSide == HumanoidArm.RIGHT), mainFist, mainShoulder);
        armArms.renderReaching(pose, event.getMultiBufferSource(), event.getPackedLight(), otherSide, armor.getVariant(),
                PowerArmorClient.plateColor(armor, otherSide == HumanoidArm.RIGHT), otherFist, otherShoulder);
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !Weapons.holdsWeapon(player)) {
            return;
        }
        boolean takenByWeapon = readyToShoot(player) && (event.isAttack() || event.isUseItem());
        boolean secondHand = event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND;
        if (takenByWeapon || secondHand) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        boolean ready = readyToShoot(player) && minecraft.screen == null;
        // A carried weapon also reports its keys, so that the server can say it is too heavy; but not while working on a block with it in hand.
        boolean carrying = player != null && minecraft.screen == null && carries(player)
                && (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK);
        boolean trigger = (ready || carrying) && minecraft.options.keyAttack.isDown();
        boolean aim = (ready || carrying) && minecraft.options.keyUse.isDown();
        if (player != null && trigger != triggerDown) {
            send(trigger ? WeaponInputPayload.Input.TRIGGER_DOWN : WeaponInputPayload.Input.TRIGGER_UP);
        }
        if (player != null && aim != aimDown) {
            send(aim ? WeaponInputPayload.Input.AIM_DOWN : WeaponInputPayload.Input.AIM_UP);
        }
        triggerDown = player != null && trigger;
        aimDown = player != null && aim;
        while (RELOAD_KEY.consumeClick()) {
            if (ready || player != null && minecraft.screen == null && carries(player)) {
                send(WeaponInputPayload.Input.RELOAD);
            }
        }
        zoomOld = zoom;
        zoom = Mth.approach(zoom, scoped(player) ? 1.0f : 0.0f, ZOOM_SPEED);
        if (player != null) {
            tickRecoil(player);
        }
    }

    private static void send(WeaponInputPayload.Input input) {
        PacketDistributor.sendToServer(new WeaponInputPayload(input));
    }

    /** The recoil lifts the view over a few ticks; the pilot pulls it back down themselves. */
    private static void tickRecoil(Player player) {
        float step = pendingRecoil * RECOIL_PER_TICK;
        pendingRecoil -= step;
        if (step > 0.01f) {
            player.setXRot(Mth.clamp(player.getXRot() - step, -90.0f, 90.0f));
        }
        burstRecoil *= RECOIL_MEMORY_DECAY;
        RebirthConfig.WeaponStats stats = Weapons.statsOf(player.getMainHandItem().getItem());
        extraSpread = stats == null ? 0.0f : Math.max(0.0f, extraSpread - stats.spreadRecoveryPerTick().get().floatValue());
    }

    private static boolean scoped(Player player) {
        if (player == null || !readyToShoot(player) || !Minecraft.getInstance().options.getCameraType().isFirstPerson()
                || !(player.getVehicle() instanceof PowerArmorEntity armor) || !armor.isAimingWeapon()) {
            return false;
        }
        return Weapons.definition(player.registryAccess(), player.getMainHandItem().getItem()).map(WeaponDefinition::scope).orElse(false);
    }

    private static float zoomFactor(double partialTick) {
        float amount = Mth.lerp((float) partialTick, zoomOld, zoom);
        return Mth.lerp(amount, 1.0f, RebirthClientConfig.WEAPON_SCOPE_FOV.get().floatValue());
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov() && (zoom > 0.0f || zoomOld > 0.0f)) {
            event.setFOV(event.getFOV() * zoomFactor(event.getPartialTick()));
        }
    }

    /** Through a scope the view turns as much slower as it is magnified. */
    private static void onTurn(CalculatePlayerTurnEvent event) {
        if (zoom > 0.0f) {
            event.setMouseSensitivity(event.getMouseSensitivity() * zoomFactor(1.0));
        }
    }

    private static void onShot(WeaponShotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(level.getEntity(payload.shooter()) instanceof Player shooter)) {
            return;
        }
        boolean own = shooter == minecraft.player;
        boolean firstPerson = own && minecraft.options.getCameraType().isFirstPerson();
        WeaponView view = WeaponView.of(shooter, shooter.getMainHandItem().getItem());
        if (payload.pellets().isEmpty()) {
            view.onEmptyClick();
            return;
        }
        view.onShot(level.getGameTime());
        Vec3 muzzle = view.muzzle() != null ? view.muzzle() : muzzle(shooter, firstPerson);
        Vec3 port = view.ejection() != null ? view.ejection() : muzzle;
        Vec3 look = shooter.getLookAngle();
        // The flash: a short wide streak; flame and smoke only for those who do not have them right before their eyes.
        if (!firstPerson) {
            level.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, look.x * 0.05, look.y * 0.05 + 0.02, look.z * 0.05);
            level.addParticle(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, look.x * 0.08, look.y * 0.08, look.z * 0.08);
        }
        // The case flies out to the right of the gun.
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Particle shellCase = minecraft.particleEngine.createParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)),
                port.x, port.y, port.z, side.x * 0.12, 0.12, side.z * 0.12);
        if (shellCase != null) {
            shellCase.scale(CASE_SCALE);
        }
        for (WeaponShotPayload.Pellet pellet : payload.pellets()) {
            Vec3 end = pellet.end();
            minecraft.particleEngine.add(new TracerParticle(level, muzzle, end, TRACER_WIDTH, 3, TRACER_COLOR));
            if (pellet.ending() == WeaponShotPayload.Ending.BLOCK) {
                sparks(level, end);
            } else if (pellet.ending() == WeaponShotPayload.Ending.ENTITY) {
                level.addParticle(ParticleTypes.CRIT, end.x, end.y, end.z, 0.0, 0.1, 0.0);
            }
        }
        if (own) {
            addRecoil(shooter);
        }
    }

    private static void sparks(ClientLevel level, Vec3 at) {
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (level.random.nextDouble() - 0.5) * 0.3, level.random.nextDouble() * 0.2,
                    (level.random.nextDouble() - 0.5) * 0.3);
        }
        BlockPos hit = BlockPos.containing(at);
        if (!level.getBlockState(hit).isAir()) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(hit)), at.x, at.y, at.z, 0.0, 0.05, 0.0);
        }
    }

    /** Where the barrel ends, near enough before the model has been drawn. */
    private static Vec3 muzzle(Entity shooter, boolean firstPerson) {
        Vec3 look = shooter.getLookAngle();
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 up = side.cross(look).normalize();
        double forward = firstPerson ? 0.9 : 1.3;
        double right = firstPerson ? 0.45 : 0.45;
        double down = firstPerson ? 0.4 : 0.35;
        return shooter.getEyePosition().add(look.scale(forward)).add(side.scale(right)).subtract(up.scale(down));
    }

    private static void addRecoil(Player player) {
        RebirthConfig.WeaponStats stats = Weapons.statsOf(player.getMainHandItem().getItem());
        if (stats == null) {
            return;
        }
        float kick = stats.recoilDegrees().get().floatValue() + burstRecoil;
        pendingRecoil += kick;
        burstRecoil = Math.min(burstRecoil + stats.recoilBuildup().get().floatValue(), stats.maxRecoilBuildup().get().floatValue());
        extraSpread = Math.min(extraSpread + stats.spreadPerShot().get().floatValue(), stats.maxExtraSpread().get().floatValue());
    }

    private static void hideVanillaCrosshair(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && readyToShoot(Minecraft.getInstance().player) && HudLayout.enabled()) {
            event.setCanceled(true);
        }
    }

    /** Four strokes around the point of aim; they stand as far apart as the shots may scatter. */
    private static void renderReticle(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!readyToShoot(player) || !HudLayout.enabled() || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        RebirthConfig.WeaponStats stats = Weapons.statsOf(player.getMainHandItem().getItem());
        if (stats == null) {
            return;
        }
        boolean aiming = player.getVehicle() instanceof PowerArmorEntity armor && armor.isAimingWeapon();
        double spread = (stats.spreadDegrees().get() + extraSpread) * (aiming ? stats.aimSpreadMultiplier().get() : 1.0);
        int gap = 2 + Math.round((float) spread * DEGREES_TO_PIXELS);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        graphics.fill(x, y, x + 1, y + 1, RETICLE_COLOR);
        graphics.fill(x - gap - RETICLE_LENGTH, y, x - gap, y + 1, RETICLE_COLOR);
        graphics.fill(x + 1 + gap, y, x + 1 + gap + RETICLE_LENGTH, y + 1, RETICLE_COLOR);
        graphics.fill(x, y - gap - RETICLE_LENGTH, x + 1, y - gap, RETICLE_COLOR);
        graphics.fill(x, y + 1 + gap, x + 1, y + 1 + gap + RETICLE_LENGTH, RETICLE_COLOR);
    }

    /** Beside the hotbar: rounds in the magazine, rounds in the inventory, and the reload. */
    private static void renderStatus(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!readyToShoot(player) || !HudLayout.enabled() || minecraft.options.hideGui || !(player.getVehicle() instanceof PowerArmorEntity armor)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        RebirthConfig.WeaponStats stats = Weapons.statsOf(stack.getItem());
        WeaponDefinition definition = Weapons.definition(player.registryAccess(), stack.getItem()).orElse(null);
        if (stats == null || definition == null) {
            return;
        }
        Font font = minecraft.font;
        int rounds = Weapons.ammoIn(stack);
        int reserve = player.getInventory().countItem(definition.ammo().value());
        int x = graphics.guiWidth() / 2 + 91 + MARGIN;
        int y = graphics.guiHeight() - 2 * font.lineHeight - 2;
        graphics.drawString(font, Component.translatable("hud.projectrebirth.weapon.magazine", rounds, stats.magazine().get()), x, y, rounds > 0 ? 0xFFFFFF : 0xFF5555);
        y += font.lineHeight + 1;
        if (rounds == 0 && reserve > 0) {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.weapon.reload_hint", RELOAD_KEY.getTranslatedKeyMessage()), x, y, 0xFFD070);
        } else {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.weapon.reserve", reserve), x, y, reserve > 0 ? 0xDDDDDD : 0xFF5555);
        }
        long left = armor.weaponReloadEnd() - player.level().getGameTime();
        if (armor.weaponReloadEnd() > 0 && left > 0) {
            float done = 1.0f - Math.min(1.0f, left / (float) stats.reloadTicks().get());
            GuiAtlas.bar(graphics, x, y - font.lineHeight - 7, 60, 4, done, 0xFFFFD070);
        }
    }
}
