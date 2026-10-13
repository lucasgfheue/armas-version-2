package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dibuja el cuchillo (modelo 3D del ítem) apuntando hacia donde vuela y girando en el aire. */
public class KnifeRenderer extends EntityRenderer<ThrownKnife> {
    private final ItemRenderer itemRenderer;

    public KnifeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownKnife knife, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, knife.yRotO, knife.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, knife.xRotO, knife.getXRot())));

        boolean flying = knife.getDeltaMovement().lengthSqr() > 0.04;
        if (flying) {
            poseStack.mulPose(Axis.ZP.rotationDegrees((knife.tickCount + partialTick) * 45.0F));
        }
        poseStack.scale(1.4F, 1.4F, 1.4F);

        this.itemRenderer.renderStatic(
                new ItemStack(ModItems.CUCHILLO.get()), ItemDisplayContext.NONE, light,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, knife.level(), knife.getId());
        poseStack.popPose();
        super.render(knife, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownKnife knife) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
