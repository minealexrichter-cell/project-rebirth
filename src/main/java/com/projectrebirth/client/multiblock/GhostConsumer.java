package com.projectrebirth.client.multiblock;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Passes vertices on with their alpha lowered: a block drawn through it is a see-through ghost. Client only. */
final class GhostConsumer implements VertexConsumer {
    private final VertexConsumer inner;
    private final float alpha;

    GhostConsumer(VertexConsumer inner, float alpha) {
        this.inner = inner;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        inner.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alphaValue) {
        inner.setColor(red, green, blue, Math.round(alphaValue * alpha));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        inner.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        inner.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        inner.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        inner.setNormal(x, y, z);
        return this;
    }
}
