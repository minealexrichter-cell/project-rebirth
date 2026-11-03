package com.projectrebirth.client;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.TransferAnimationPayload;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.core.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The transfer animation; it only decorates the transfer on this client. The camera rises above the old body, the
 * screen goes black at the top, then the camera comes down into the new body. Meanwhile the HUD is hidden and a lock
 * screen takes all input.
 *
 * <p>When the player asks for a transfer, the request goes to the server at the top of the rise, under the black screen,
 * so the old place is still there for the rise even if the new body is far away. When the transfer comes from the
 * server (the body was destroyed, an admin command), the player has already moved: the camera rises from the old place
 * only if that place is still loaded, otherwise the animation starts black, above the new body.
 *
 * <p>The camera is moved the vanilla way, by viewing through a marker entity that is never added to the world.
 */
public final class TransferAnimation {
    private enum Phase { NONE, RISE, BLACKEN, WAIT_SERVER, WAIT_CHUNKS, DESCEND }

    private static final float RISE_SHARE = 0.4f;
    private static final float BLACKEN_SHARE = 0.1f;
    private static final float DESCEND_SHARE = 0.5f;
    /** Part of the descent during which the black screen clears. */
    private static final float CLEAR_SHARE = 0.3f;
    private static final float TOP_PITCH = 60.0f;
    /** Longer than the server's wait for a far body's chunk to load. */
    private static final int SERVER_TIMEOUT_TICKS = 200;
    private static final int CHUNKS_TIMEOUT_TICKS = 100;
    /** Well inside the 80 ticks the server allows for hanging in the air. */
    private static final int MAX_HANGING_TICKS = 50;

    private static Phase phase = Phase.NONE;
    private static int tick;
    private static int phaseTicks;
    private static int hangingTicks;
    @Nullable
    private static Runnable request;
    private static boolean arrived;
    private static Vec3 risePos = Vec3.ZERO;
    private static float riseYaw;
    private static float risePitch;
    private static float alpha;
    private static float alphaO;
    @Nullable
    private static CameraMarker camera;

    private TransferAnimation() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(TransferAnimation::onClientTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TransferAnimation::hideHud);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TransferAnimation::hideHands);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    public static boolean isActive() {
        return phase != Phase.NONE;
    }

    /**
     * The player asks for a transfer: plays the rise and then runs {@code sendRequest}. Without the animation
     * (switched off, the player is in a vehicle or in the air) the request goes at once.
     */
    public static void requestTransfer(Runnable sendRequest) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (isActive()) {
            return;
        }
        if (player == null || !RebirthClientConfig.TRANSFER_ANIMATION_ENABLED.get() || player.isPassenger() || hanging(player)) {
            sendRequest.run();
            return;
        }
        request = sendRequest;
        arrived = false;
        beginRise(player.position(), player);
    }

    /** Something else needs the screen (the capsule window reports a failed transfer): stop at once. */
    static void cancel() {
        if (isActive()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.setCameraEntity(minecraft.player);
            }
            if (minecraft.screen instanceof LockScreen) {
                minecraft.setScreen(null);
            }
            reset();
        }
    }

    private static void reset() {
        phase = Phase.NONE;
        request = null;
        arrived = false;
        camera = null;
        alpha = 0.0f;
        alphaO = 0.0f;
    }

    private static void beginRise(Vec3 from, LocalPlayer player) {
        risePos = from;
        riseYaw = player.getYRot();
        risePitch = player.getXRot();
        alpha = 0.0f;
        alphaO = 0.0f;
        camera = null;
        enter(Phase.RISE);
    }

    private static void beginDark() {
        alpha = 1.0f;
        alphaO = 1.0f;
        camera = null;
        enter(Phase.WAIT_CHUNKS);
    }

    private static void enter(Phase next) {
        phase = next;
        tick = 0;
        hangingTicks = 0;
        float seconds = RebirthClientConfig.TRANSFER_ANIMATION_SECONDS.get().floatValue();
        float share = next == Phase.RISE ? RISE_SHARE : next == Phase.BLACKEN ? BLACKEN_SHARE : DESCEND_SHARE;
        phaseTicks = Math.max(1, Math.round(seconds * 20.0f * share));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        TransferAnimationPayload message = TransferAnimationPayload.received;
        LocalPlayer player = minecraft.player;
        if (message != null) {
            TransferAnimationPayload.received = null;
            if (player != null) {
                onServerMessage(minecraft, player, message);
            }
        }
        if (phase != Phase.NONE) {
            if (player == null || minecraft.level == null || !player.isAlive()) {
                cancel();
            } else if (holdScreen(minecraft)) {
                advance(minecraft, player);
            }
        }
    }

    private static void onServerMessage(Minecraft minecraft, LocalPlayer player, TransferAnimationPayload message) {
        if (!message.arrived()) {
            if (phase == Phase.WAIT_SERVER || phase == Phase.RISE || phase == Phase.BLACKEN) {
                request = null;
                alpha = 1.0f;
                alphaO = 1.0f;
                enter(Phase.DESCEND);
            }
            return;
        }
        arrived = true;
        request = null;
        if (phase == Phase.NONE) {
            if (!RebirthClientConfig.TRANSFER_ANIMATION_ENABLED.get()) {
                arrived = false;
                return;
            }
            Vec3 from = new Vec3(message.fromX(), message.fromY(), message.fromZ());
            if (message.sameDimension() && from.distanceTo(player.position()) < stillLoadedDistance(minecraft)) {
                beginRise(from, player);
            } else {
                beginDark();
            }
        } else if (phase != Phase.WAIT_SERVER) {
            beginDark();
        }
    }

    /** The client keeps the old chunks only while they are inside its view around the new place. */
    private static double stillLoadedDistance(Minecraft minecraft) {
        return Math.max(0, minecraft.options.getEffectiveRenderDistance() - 2) * 16.0 / 2.0;
    }

    /** Keeps the lock screen open. @return false if the animation must wait or has been stopped. */
    private static boolean holdScreen(Minecraft minecraft) {
        if (minecraft.screen instanceof LockScreen) {
            return true;
        }
        if (minecraft.screen instanceof ReceivingLevelScreen) {
            return false;
        }
        if (minecraft.screen == null || phase == Phase.RISE && tick == 0) {
            minecraft.setScreen(new LockScreen());
            return true;
        }
        // Another screen took over (for example the death screen).
        Screen other = minecraft.screen;
        cancel();
        minecraft.setScreen(other);
        return false;
    }

    /**
     * The game sends the player's position only while the player holds the camera. Without it the server would think
     * that a falling player hangs in the air and disconnect them for flying, so the position is reported here.
     */
    private static void reportPosition(LocalPlayer player) {
        player.connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.onGround()));
    }

    /** Nothing under the player, by the same test as the server's flying check. */
    private static boolean hanging(LocalPlayer player) {
        return player.level().noBlockCollision(player, player.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0));
    }

    private static void advance(Minecraft minecraft, LocalPlayer player) {
        reportPosition(player);
        // A long fall needs the player's own eyes and hands back.
        hangingTicks = hanging(player) ? hangingTicks + 1 : 0;
        if (hangingTicks > MAX_HANGING_TICKS) {
            cancel();
            return;
        }
        alphaO = alpha;
        tick++;
        float progress = Mth.clamp((float) tick / phaseTicks, 0.0f, 1.0f);
        switch (phase) {
            case RISE -> {
                place(minecraft, player, risePos.add(0.0, height() * smooth(progress), 0.0), riseYaw,
                        Mth.lerp(smooth(progress), risePitch, TOP_PITCH));
                if (tick >= phaseTicks) {
                    enter(Phase.BLACKEN);
                }
            }
            case BLACKEN -> {
                alpha = progress;
                place(minecraft, player, risePos.add(0.0, height(), 0.0), riseYaw, TOP_PITCH);
                if (tick >= phaseTicks) {
                    alpha = 1.0f;
                    if (request != null) {
                        request.run();
                        request = null;
                    }
                    if (arrived) {
                        enter(Phase.WAIT_CHUNKS);
                    } else {
                        phase = Phase.WAIT_SERVER;
                        tick = 0;
                    }
                }
            }
            case WAIT_SERVER -> {
                alpha = 1.0f;
                if (arrived) {
                    enter(Phase.WAIT_CHUNKS);
                } else if (tick >= SERVER_TIMEOUT_TICKS) {
                    enter(Phase.DESCEND);
                }
            }
            case WAIT_CHUNKS -> {
                alpha = 1.0f;
                place(minecraft, player, player.position().add(0.0, height(), 0.0), player.getYRot(), TOP_PITCH);
                if (tick >= CHUNKS_TIMEOUT_TICKS || minecraft.levelRenderer.isSectionCompiled(player.blockPosition())) {
                    playArrivalSound(minecraft, player);
                    enter(Phase.DESCEND);
                }
            }
            case DESCEND -> {
                alpha = 1.0f - Mth.clamp(progress / CLEAR_SHARE, 0.0f, 1.0f);
                float left = 1.0f - smooth(progress);
                place(minecraft, player, player.position().add(0.0, height() * left, 0.0), player.getYRot(),
                        Mth.lerp(left, player.getXRot(), TOP_PITCH));
                if (tick >= phaseTicks) {
                    finish(minecraft, player);
                }
            }
            default -> {
            }
        }
    }

    /** The server's transfer sound is too far from the raised camera to be heard, so it is played here. */
    private static void playArrivalSound(Minecraft minecraft, LocalPlayer player) {
        if (arrived) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    TransferService.isInArtificialBody(player) ? ModSounds.TRANSFER_ENTER.get() : ModSounds.TRANSFER_RETURN.get(), 1.0f));
        }
    }

    private static void finish(Minecraft minecraft, LocalPlayer player) {
        minecraft.setCameraEntity(player);
        if (minecraft.screen instanceof LockScreen) {
            minecraft.setScreen(null);
        }
        reset();
    }

    private static void place(Minecraft minecraft, LocalPlayer player, Vec3 pos, float yaw, float pitch) {
        Level level = minecraft.level;
        if (camera == null || camera.level() != level) {
            camera = new CameraMarker(level, player.getEyeHeight());
            camera.setPos(pos);
            camera.xo = pos.x;
            camera.yo = pos.y;
            camera.zo = pos.z;
            camera.setYRot(yaw);
            camera.setXRot(pitch);
            camera.yRotO = yaw;
            camera.xRotO = pitch;
        } else {
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.yRotO = camera.getYRot();
            camera.xRotO = camera.getXRot();
            camera.setPos(pos);
            camera.setYRot(yaw);
            camera.setXRot(pitch);
        }
        if (minecraft.getCameraEntity() != camera) {
            minecraft.setCameraEntity(camera);
        }
    }

    private static float height() {
        return RebirthClientConfig.TRANSFER_ANIMATION_HEIGHT.get().floatValue();
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    /** All HUD layers, the mod's and other mods' alike; F1-style, only for the animation's time. */
    private static void hideHud(RenderGuiLayerEvent.Pre event) {
        if (isActive()) {
            event.setCanceled(true);
        }
    }

    private static void hideHands(RenderHandEvent event) {
        if (isActive()) {
            event.setCanceled(true);
        }
    }

    /** Sees the world from the given point; never added to the world, so it does not exist for anything else. */
    private static class CameraMarker extends Marker {
        private final EntityDimensions eye;

        CameraMarker(Level level, float eyeHeight) {
            super(EntityType.MARKER, level);
            this.eye = EntityDimensions.fixed(0.1f, eyeHeight).withEyeHeight(eyeHeight);
            refreshDimensions();
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return eye != null ? eye : super.getDimensions(pose);
        }
    }

    /** Takes all input while the animation plays and draws the black screen. */
    private static class LockScreen extends Screen {
        LockScreen() {
            super(CommonComponents.EMPTY);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int shade = Math.round(Mth.lerp(partialTick, alphaO, alpha) * 255.0f);
            if (shade > 0) {
                graphics.fill(0, 0, width, height, shade << 24);
            }
        }
    }
}
