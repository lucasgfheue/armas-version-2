package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Bola de fuego gigante a máximo brillo + un halo luminoso que late. */
public class FlareRenderer extends ThrownItemRenderer<FlareProjectile> {
    public FlareRenderer(EntityRendererProvider.Context context) {
        super(context, 3.5F, true);
    }

    @Override
    public void render(FlareProjectile entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0); // centrar en la caja de colisión (1x1)
        super.render(entity, yaw, partialTick, poseStack, buffer, light);

        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float pulse = 1.0F + 0.12F * Mth.sin((entity.tickCount + partialTick) * 0.9F);
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        halo(vc, m, 2.4F * pulse, 1.0F, 0.25F, 0.08F, 0.55F);  // halo exterior rojo-naranja
        halo(vc, m, 1.2F * pulse, 1.0F, 0.75F, 0.35F, 0.70F);  // centro amarillento
        poseStack.popPose();
    }

    /** Disco con degradado radial: centro brillante, borde transparente. */
    private static void halo(VertexConsumer vc, Matrix4f m, float radius,
                             float r, float g, float b, float centerAlpha) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * 2.0 * Math.PI / n;
            double a1 = (i + 1) * 2.0 * Math.PI / n;
            float x0 = (float) Math.cos(a0) * radius, y0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, y1 = (float) Math.sin(a1) * radius;

            vc.addVertex(m, 0, 0, 0).setColor(r, g, b, centerAlpha);
            vc.addVertex(m, x0, y0, 0).setColor(r, g, b, 0.0F);
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, 0.0F);
            vc.addVertex(m, 0, 0, 0).setColor(r, g, b, centerAlpha);

            vc.addVertex(m, 0, 0, 0).setColor(r, g, b, centerAlpha);
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, 0.0F);
            vc.addVertex(m, x0, y0, 0).setColor(r, g, b, 0.0F);
            vc.addVertex(m, 0, 0, 0).setColor(r, g, b, centerAlpha);
        }
    }
}
