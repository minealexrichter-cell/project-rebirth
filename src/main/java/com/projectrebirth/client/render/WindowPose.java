package com.projectrebirth.client.render;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Models stand still in windows. A window draws them small, and there the slow idle movement makes each part jump by a
 * pixel on its own and opens gaps at the joints for a moment, so the model seems to tremble. This covers every screen,
 * also those of the game itself such as the creative inventory.
 */
public final class WindowPose {
    private static boolean drawing;

    private WindowPose() {
    }

    /** Everything drawn between these events is drawn by a screen; the world is drawn before them. A cancelled screen draws nothing. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ScreenEvent.Render.Pre.class, event -> drawing = true);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, ScreenEvent.Render.Post.class, event -> drawing = false);
    }

    /** Puts all bones back into the model's own pose if the drawing is for a window. */
    public static void apply(GeoModel<? extends GeoAnimatable> model) {
        if (!drawing) {
            return;
        }
        for (GeoBone bone : model.getAnimationProcessor().getRegisteredBones()) {
            BoneSnapshot rest = bone.getInitialSnapshot();
            bone.updateRotation(rest.getRotX(), rest.getRotY(), rest.getRotZ());
            bone.updatePosition(rest.getOffsetX(), rest.getOffsetY(), rest.getOffsetZ());
        }
    }
}
