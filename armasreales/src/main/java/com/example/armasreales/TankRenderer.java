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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dibuja el tanque: casco, torreta (gira con la mirada del conductor) y cañón (sube y baja). */
public class TankRenderer extends EntityRenderer<TankEntity> {
    private static final float SCALE = 2.0f;
    private final ItemRenderer itemRenderer;
    private final ItemStack hull = new ItemStack(ModItems.TANQUE_CASCO.get());
    private final ItemStack turret = new ItemStack(ModItems.TANQUE_TORRETA.get());
    private final ItemStack cannon = new ItemStack(ModItems.TANQUE_CANON.get());

    public TankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 1.6f;
    }

    @Override
    public void render(TankEntity tank, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        float hullYaw = Mth.rotLerp(partialTick, tank.yRotO, tank.getYRot());
        float turretYaw = hullYaw;
        float pitch = 0.0f;
        LivingEntity driver = tank.getControllingPassenger();
        if (driver != null) {
            turretYaw = driver.getViewYRot(partialTick);
            pitch = Mth.clamp(driver.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-hullYaw));

        // Casco
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0.0, 0.5, 0.0);
        this.draw(hull, tank, light, poseStack, buffer);
        poseStack.popPose();

        // Torreta
        poseStack.translate(0.0, TankEntity.TURRET_HEIGHT, TankEntity.TURRET_FORWARD);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(turretYaw - hullYaw)));
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0.0, 0.5, 0.0);
        this.draw(turret, tank, light, poseStack, buffer);
        poseStack.popPose();

        // Cañón (pivota en el centro de su modelo)
        poseStack.translate(0.0, TankEntity.BARREL_UP, TankEntity.BARREL_FORWARD);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        this.draw(cannon, tank, light, poseStack, buffer);
        poseStack.popPose();

        poseStack.popPose();
        super.render(tank, yaw, partialTick, poseStack, buffer, light);
    }

    private void draw(ItemStack stack, TankEntity tank, int light, PoseStack poseStack, MultiBufferSource buffer) {
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, tank.level(), tank.getId());
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity tank) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
