package com.projectrebirth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * The look of the Overworld with acid rain falling. In vanilla, rain is snow in cold biomes and nothing at all in deserts
 * and savannas; acid rain is a catastrophe, not weather, so while it falls it is drawn the same in every biome: greenish
 * streaks, splashes and the sound of rain. At any other time everything is vanilla.
 */
final class AcidRainEffects extends DimensionSpecialEffects.OverworldEffects {
    private static final ResourceLocation RAIN = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final float RED = 0.72f;
    private static final float GREEN = 1.0f;
    private static final float BLUE = 0.5f;
    private final float[] sizeX = new float[1024];
    private final float[] sizeZ = new float[1024];
    private int soundTime;

    AcidRainEffects() {
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                float x = j - 16;
                float z = i - 16;
                float length = Mth.sqrt(x * x + z * z);
                sizeX[i << 5 | j] = -z / length;
                sizeZ[i << 5 | j] = x / length;
            }
        }
    }

    private static boolean falling(ClientLevel level, float partialTick) {
        return CatastropheClient.acidRainFalling() && level.getRainLevel(partialTick) > 0.0f;
    }

    /** The streaks: the vanilla rain, without its check of the biome, and tinted green. */
    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        if (!falling(level, partialTick)) {
            return false;
        }
        float rain = level.getRainLevel(partialTick);
        lightTexture.turnOnLightLayer();
        int cx = Mth.floor(camX);
        int cy = Mth.floor(camY);
        int cz = Mth.floor(camZ);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, RAIN);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        boolean any = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = cz - radius; z <= cz + radius; z++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int index = (z - cz + 16) * 32 + x - cx + 16;
                double dx = sizeX[index] * 0.5;
                double dz = sizeZ[index] * 0.5;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottom = Math.max(cy - radius, ground);
                int top = Math.max(cy + radius, ground);
                if (bottom == top) {
                    continue;
                }
                int lightY = Math.max(ground, cy);
                RandomSource random = RandomSource.create(x * x * 3121L + x * 45238971L ^ z * z * 418711L + z * 13761L);
                int offset = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 0xFF;
                float speed = 3.0f + random.nextFloat();
                float v = -((float) ((ticks & 131071) + offset) + partialTick) / 32.0f * speed % 32.0f;
                double ox = x + 0.5 - camX;
                double oz = z + 0.5 - camZ;
                float far = (float) Math.sqrt(ox * ox + oz * oz) / radius;
                float alpha = ((1.0f - far * far) * 0.5f + 0.5f) * rain;
                pos.set(x, lightY, z);
                int light = LevelRenderer.getLightColor(level, pos);
                buffer.addVertex((float) (x - camX - dx + 0.5), (float) (top - camY), (float) (z - camZ - dz + 0.5))
                        .setUv(0.0f, bottom * 0.25f + v).setColor(RED, GREEN, BLUE, alpha).setLight(light);
                buffer.addVertex((float) (x - camX + dx + 0.5), (float) (top - camY), (float) (z - camZ + dz + 0.5))
                        .setUv(1.0f, bottom * 0.25f + v).setColor(RED, GREEN, BLUE, alpha).setLight(light);
                buffer.addVertex((float) (x - camX + dx + 0.5), (float) (bottom - camY), (float) (z - camZ + dz + 0.5))
                        .setUv(1.0f, top * 0.25f + v).setColor(RED, GREEN, BLUE, alpha).setLight(light);
                buffer.addVertex((float) (x - camX - dx + 0.5), (float) (bottom - camY), (float) (z - camZ - dz + 0.5))
                        .setUv(0.0f, top * 0.25f + v).setColor(RED, GREEN, BLUE, alpha).setLight(light);
                any = true;
            }
        }
        if (any) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            buffer.build();
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        lightTexture.turnOffLightLayer();
        return true;
    }

    /** The splashes and the sound of rain, the vanilla way, in every biome. */
    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        if (!falling(level, 1.0f)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ParticleStatus particles = minecraft.options.particles().get();
        float rain = level.getRainLevel(1.0f) / (Minecraft.useFancyGraphics() ? 1.0f : 2.0f);
        RandomSource random = RandomSource.create(ticks * 312987231L);
        BlockPos eyes = BlockPos.containing(camera.getPosition());
        BlockPos last = null;
        int count = (int) (100.0f * rain * rain) / (particles == ParticleStatus.DECREASED ? 2 : 1);
        for (int i = 0; i < count; i++) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, eyes.offset(random.nextInt(21) - 10, 0, random.nextInt(21) - 10));
            if (top.getY() <= level.getMinBuildHeight() || Math.abs(top.getY() - eyes.getY()) > 10) {
                continue;
            }
            last = top.below();
            if (particles == ParticleStatus.MINIMAL) {
                break;
            }
            double px = random.nextDouble();
            double pz = random.nextDouble();
            BlockState state = level.getBlockState(last);
            FluidState fluid = level.getFluidState(last);
            double height = Math.max(state.getCollisionShape(level, last).max(net.minecraft.core.Direction.Axis.Y, px, pz), fluid.getHeight(level, last));
            level.addParticle(ParticleTypes.RAIN, last.getX() + px, last.getY() + height, last.getZ() + pz, 0.0, 0.0, 0.0);
        }
        if (last != null && random.nextInt(3) < soundTime++) {
            soundTime = 0;
            boolean above = last.getY() > eyes.getY() + 1 && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, eyes).getY() > eyes.getY();
            level.playLocalSound(last, above ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN, SoundSource.WEATHER,
                    above ? 0.1f : 0.2f, above ? 0.5f : 1.0f, false);
        }
        return true;
    }
}
