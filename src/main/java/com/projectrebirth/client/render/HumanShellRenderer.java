package com.projectrebirth.client.render;

import java.util.UUID;

import com.projectrebirth.bodies.HumanShellEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Draws the human body in a capsule with the player model and the owner's skin (default skin if unknown). */
public class HumanShellRenderer extends LivingEntityRenderer<HumanShellEntity, PlayerModel<HumanShellEntity>> {
    public HumanShellRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(HumanShellEntity shell) {
        UUID owner = shell.getOwnerId().orElse(shell.getUUID());
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(owner) : null;
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(owner).texture();
    }

    @Override
    protected boolean shouldShowName(HumanShellEntity shell) {
        return shell.hasCustomName() && shell == entityRenderDispatcher.crosshairPickEntity;
    }
}
