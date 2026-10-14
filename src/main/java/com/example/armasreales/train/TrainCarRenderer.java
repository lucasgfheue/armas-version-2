package com.example.armasreales.train;

import com.example.armasreales.ModItems;
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

/** Dibuja la locomotora o el vagón (modelos 3D hechos con cubos) a escala x2. */
public class TrainCarRenderer extends EntityRenderer<TrainCar> {
    private static final float SCALE = 2.0f;
    private final ItemRenderer itemRenderer;
    private final ItemStack locomotive = new ItemStack(ModItems.TREN_LOCOMOTORA.get());
    private final ItemStack wagon = new ItemStack(ModItems.TREN_VAGON.get());

    public TrainCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(TrainCar car, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        float rot = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-rot));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0.0, 0.5, 0.0);
        ItemStack stack = car.getKind() == TrainCar.LOCOMOTIVE ? locomotive : wagon;
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, car.level(), car.getId());
        poseStack.popPose();
        super.render(car, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainCar car) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
