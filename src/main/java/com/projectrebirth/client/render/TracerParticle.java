package com.projectrebirth.client.render;

import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A glowing streak between two points that fades in a moment: the trail of a bullet, or a muzzle flash when it is short and wide. */
public class TracerParticle extends Particle {
    private static final ResourceLocation SPRITE = ResourceLocation.withDefaultNamespace("generic_7");

    private final Vec3 from;
    private final Vec3 to;
    private final float halfWidth;
    private final float u;
    private final float v;
    private final float red;
    private final float green;
    private final float blue;

    public TracerParticle(ClientLevel level, Vec3 from, Vec3 to, float width, int lifetime, int color) {
        super(level, from.x, from.y, from.z);
        this.from = from;
        this.to = to;
        this.halfWidth = width / 2.0f;
        this.lifetime = lifetime;
        this.red = (color >> 16 & 255) / 255.0f;
        this.green = (color >> 8 & 255) / 255.0f;
        this.blue = (color & 255) / 255.0f;
        // The middle of a small sprite is one even colour, so the streak needs no texture of its own.
        TextureAtlasSprite sprite = ((TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES)).getSprite(SPRITE);
        this.u = (sprite.getU0() + sprite.getU1()) / 2.0f;
        this.v = (sprite.getV0() + sprite.getV1()) / 2.0f;
        setBoundingBox(new AABB(from, to).inflate(width));
    }

    @Override
    public void tick() {
        if (age++ >= lifetime) {
            remove();
        }
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 eye = camera.getPosition();
        Vec3 a = from.subtract(eye);
        Vec3 b = to.subtract(eye);
        // A flat ribbon that always shows its face to the camera.
        Vec3 side = b.subtract(a).cross(a).normalize().scale(halfWidth);
        float alpha = Math.max(0.0f, 0.9f * (1.0f - (age + partialTick) / lifetime));
        int light = LightTexture.FULL_BRIGHT;
        Vec3 p1 = a.add(side);
        Vec3 p2 = a.subtract(side);
        Vec3 p3 = b.subtract(side);
        Vec3 p4 = b.add(side);
        vertex(buffer, p1, alpha, light);
        vertex(buffer, p2, alpha, light);
        vertex(buffer, p3, alpha, light);
        vertex(buffer, p4, alpha, light);
        // And its back, whichever way the faces are culled.
        vertex(buffer, p4, alpha, light);
        vertex(buffer, p3, alpha, light);
        vertex(buffer, p2, alpha, light);
        vertex(buffer, p1, alpha, light);
    }

    private void vertex(VertexConsumer buffer, Vec3 position, float alpha, int light) {
        buffer.addVertex((float) position.x, (float) position.y, (float) position.z).setUv(u, v).setColor(red, green, blue, alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
