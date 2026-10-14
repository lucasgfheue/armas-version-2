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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dibuja el casco y, aparte, la torreta con el cañón girando hacia donde mira el conductor. */
public class TankRenderer extends EntityRenderer<TankEntity> {
    private final ItemRenderer itemRenderer;
    private final ItemStack hull = new ItemStack(ModItems.TANQUE_CASCO.get());
    private final ItemStack turret = new ItemStack(ModItems.TANQUE_TORRETA.get());

    public TankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 1.3f;
    }

    @Override
    public void render(TankEntity tank, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        float hullYaw = Mth.rotLerp(partialTick, tank.yRotO, tank.getYRot());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - hullYaw));

        // Casco (el modelo mira hacia -Z; se centra en la entidad)
        poseStack.pushPose();
        poseStack.translate(-0.5, 0.0, -0.5);
        this.itemRenderer.renderStatic(this.hull, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, tank.level(), tank.getId());
        poseStack.popPose();

        // Torreta: sigue la mirada del conductor
        Entity driver = tank.getControllingPassenger();
        float turretYaw;
        float pitch = 0.0f;
        if (driver != null) {
            turretYaw = Mth.rotLerp(partialTick, driver.yRotO, driver.getYRot());
            pitch = Mth.lerp(partialTick, driver.xRotO, driver.getXRot());
            tank.restTurretYaw = turretYaw;
        } else {
            turretYaw = Float.isNaN(tank.restTurretYaw) ? hullYaw : tank.restTurretYaw;
        }
        float elevation = Mth.clamp(-pitch, -8.0f, 30.0f);

        poseStack.pushPose();
        poseStack.translate(0.0, 13.0 / 16.0, -0.35);
        poseStack.mulPose(Axis.YP.rotationDegrees(hullYaw - turretYaw));
        poseStack.translate(0.0, 0.3, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(elevation));
        poseStack.translate(0.0, -0.3, 0.0);
        poseStack.translate(-0.5, 0.0, -0.5);
        this.itemRenderer.renderStatic(this.turret, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, tank.level(), tank.getId() + 1);
        poseStack.popPose();

        poseStack.popPose();
        super.render(tank, entityYaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity tank) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
