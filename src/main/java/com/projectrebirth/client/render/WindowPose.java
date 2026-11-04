package com.projectrebirth.client.render;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Models stand still in windows. A window draws them small, and there the slow idle movement makes each part jump by a
 * pixel on its own and opens gaps at the joints for a moment, so the model seems to tremble.
 */
public final class WindowPose {
    private static boolean drawing;

    private WindowPose() {
    }

    public static void begin() {
        drawing = true;
    }

    public static void end() {
        drawing = false;
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
