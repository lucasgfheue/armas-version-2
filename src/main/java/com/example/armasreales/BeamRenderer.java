package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja los rayos como geometría brillante (mezcla aditiva, igual que los relámpagos):
 *  - Láser: línea recta roja con núcleo casi blanco y halo rojo.
 *  - Plasma: rayo eléctrico irregular que cambia cada tick, con rayos secundarios,
 *    ramificaciones y chispas en el punto de impacto.
 */
public class BeamRenderer extends EntityRenderer<BeamEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    // Capas del rayo eléctrico, de fina a gruesa (el orden importa por el buffer de profundidad)
    private static final double[] LAYER_WIDTH = {0.012, 0.03, 0.07};
    private static final float[][] LAYER_COLOR = {
            {0.95f, 0.98f, 1.0f, 0.95f},
            {0.45f, 0.80f, 1.0f, 0.50f},
            {0.30f, 0.45f, 1.0f, 0.20f}
    };

    private record Bolt(Vec3[] pts, double widthScale, float alphaScale, int layers) {}

    public BeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BeamEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(BeamEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // el rayo es largo: nunca lo descartes por estar "fuera de pantalla"
    }

    @Override
    public void render(BeamEntity beam, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        Entity owner = beam.level().getEntity(beam.getOwnerId());
        float length = beam.getLength();
        if (owner == null || length < 0.5f) {
            return;
        }

        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();

        Vec3 aim = eye.add(look.scale(length));
        Vec3 start = eye.add(look.scale(0.9)).add(right.scale(0.2)).add(0, -0.22, 0);
        Vec3 origin = beam.getPosition(partialTick);
        Vec3 a = start.subtract(origin);
        Vec3 b = aim.subtract(origin);

        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        float time = beam.tickCount + partialTick;

        if (beam.getStyle() == BeamEntity.STYLE_PLASMA) {
            drawPlasma(vc, m, a, b, beam);
        } else {
            drawLaser(vc, m, a, b, time);
        }
    }

    // ------------------------------------------------------------------ Láser rojo

    private void drawLaser(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float time) {
        float pulse = 1.0f + 0.1f * Mth.sin(time * 1.7f);
        tube(vc, m, a, b, 0.013 * pulse, 1.0f, 0.88f, 0.88f, 0.95f);  // núcleo casi blanco
        tube(vc, m, a, b, 0.032 * pulse, 1.0f, 0.12f, 0.08f, 0.50f);  // rojo intenso
        tube(vc, m, a, b, 0.070 * pulse, 1.0f, 0.00f, 0.00f, 0.20f);  // halo
        cube(vc, m, b, 0.11 * pulse, 1.0f, 0.35f, 0.25f, 0.60f);      // punto de impacto
        cube(vc, m, a, 0.05, 1.0f, 0.30f, 0.20f, 0.60f);              // boca del arma
    }

    // ------------------------------------------------------------------ Plasma eléctrico

    private void drawPlasma(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, BeamEntity beam) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.2) {
            return;
        }
        Vec3 dn = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dn.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u);

        // La forma cambia cada tick -> parpadeo eléctrico
        RandomSource rng = RandomSource.create(beam.getId() * 7919L + beam.tickCount * 104729L);
        List<Bolt> bolts = new ArrayList<>();

        Vec3[] main = jagged(a, b, u, v, Math.max(2, (int) (len / 0.6)), 0.14, rng);
        bolts.add(new Bolt(main, 1.0, 1.0f, 3));
        bolts.add(new Bolt(jagged(a, b, u, v, Math.max(2, (int) (len / 0.9)), 0.32, rng), 0.6, 0.7f, 2));
        bolts.add(new Bolt(jagged(a, b, u, v, Math.max(2, (int) (len / 0.9)), 0.32, rng), 0.6, 0.7f, 2));

        // Ramificaciones que salen del rayo principal
        for (int i = 2; i < main.length - 2; i++) {
            if (rng.nextFloat() < 0.16f) {
                Vec3 dir = main[i + 1].subtract(main[i]).normalize();
                Vec3 side = u.scale(rng.nextDouble() * 2 - 1).add(v.scale(rng.nextDouble() * 2 - 1)).normalize();
                Vec3 f1 = main[i].add(dir.add(side.scale(1.2)).normalize().scale(0.6));
                Vec3 f2 = f1.add(dir.add(side.scale(1.8)).normalize().scale(0.6));
                bolts.add(new Bolt(new Vec3[]{main[i], f1, f2}, 0.5, 0.8f, 2));
            }
        }

        // Chispas que salen disparadas en el punto de impacto
        for (int j = 0; j < 6; j++) {
            Vec3 rd = new Vec3(rng.nextDouble() - 0.5, rng.nextDouble() - 0.5, rng.nextDouble() - 0.5).normalize();
            bolts.add(new Bolt(new Vec3[]{b, b.add(rd.scale(0.3 + rng.nextDouble() * 0.5))}, 0.7, 0.9f, 2));
        }

        // Capa por capa, de fina a gruesa
        for (int layer = 0; layer < 3; layer++) {
            double width = LAYER_WIDTH[layer];
            float[] c = LAYER_COLOR[layer];
            for (Bolt bolt : bolts) {
                if (layer >= bolt.layers()) continue;
                Vec3[] pts = bolt.pts();
                for (int i = 0; i < pts.length - 1; i++) {
                    tube(vc, m, pts[i], pts[i + 1], width * bolt.widthScale(),
                            c[0], c[1], c[2], c[3] * bolt.alphaScale());
                }
            }
        }

        float flick = 0.8f + 0.4f * rng.nextFloat();
        cube(vc, m, b, 0.16 * flick, 0.75f, 0.92f, 1.0f, 0.55f);  // destello del impacto
        cube(vc, m, a, 0.07, 0.60f, 0.90f, 1.0f, 0.60f);          // boca del arma
    }

    /** Línea quebrada entre a y b, con desviaciones aleatorias (0 en las puntas). */
    private static Vec3[] jagged(Vec3 a, Vec3 b, Vec3 u, Vec3 v, int segs, double amp, RandomSource rng) {
        Vec3 d = b.subtract(a);
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double t = (double) i / segs;
            Vec3 p = a.add(d.scale(t));
            if (i != 0 && i != segs) {
                double envelope = Math.sin(Math.PI * t);
                double ox = (rng.nextDouble() - 0.5) * 2.0 * amp * envelope;
                double oy = (rng.nextDouble() - 0.5) * 2.0 * amp * envelope;
                p = p.add(u.scale(ox)).add(v.scale(oy));
            }
            pts[i] = p;
        }
        return pts;
    }

    // ------------------------------------------------------------------ Geometría

    /** Tubo de sección cuadrada entre a y b (cada cara se emite en ambos sentidos: sin culling). */
    private static void tube(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double r,
                             float cr, float cg, float cb, float ca) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4) {
            return;
        }
        Vec3 dn = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dn.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u);

        // pequeño solape en los extremos para que los tramos se unan sin huecos
        Vec3 a2 = a.subtract(dn.scale(r * 0.6));
        Vec3 b2 = b.add(dn.scale(r * 0.6));

        Vec3[] c = {
                u.scale(r).add(v.scale(r)),
                u.scale(-r).add(v.scale(r)),
                u.scale(-r).add(v.scale(-r)),
                u.scale(r).add(v.scale(-r))
        };
        for (int i = 0; i < 4; i++) {
            Vec3 c0 = c[i];
            Vec3 c1 = c[(i + 1) % 4];
            quad(vc, m, a2.add(c0), a2.add(c1), b2.add(c1), b2.add(c0), cr, cg, cb, ca);
        }
    }

    private static void cube(VertexConsumer vc, Matrix4f m, Vec3 c, double h,
                             float r, float g, float b, float a) {
        Vec3 p000 = new Vec3(c.x - h, c.y - h, c.z - h);
        Vec3 p100 = new Vec3(c.x + h, c.y - h, c.z - h);
        Vec3 p110 = new Vec3(c.x + h, c.y + h, c.z - h);
        Vec3 p010 = new Vec3(c.x - h, c.y + h, c.z - h);
        Vec3 p001 = new Vec3(c.x - h, c.y - h, c.z + h);
        Vec3 p101 = new Vec3(c.x + h, c.y - h, c.z + h);
        Vec3 p111 = new Vec3(c.x + h, c.y + h, c.z + h);
        Vec3 p011 = new Vec3(c.x - h, c.y + h, c.z + h);
        quad(vc, m, p000, p100, p110, p010, r, g, b, a);
        quad(vc, m, p001, p101, p111, p011, r, g, b, a);
        quad(vc, m, p000, p001, p011, p010, r, g, b, a);
        quad(vc, m, p100, p101, p111, p110, r, g, b, a);
        quad(vc, m, p000, p100, p101, p001, r, g, b, a);
        quad(vc, m, p010, p110, p111, p011, r, g, b, a);
    }

    /** Emite el cuadrilátero por las dos caras. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             float r, float g, float b, float a) {
        vertex(vc, m, p0, r, g, b, a);
        vertex(vc, m, p1, r, g, b, a);
        vertex(vc, m, p2, r, g, b, a);
        vertex(vc, m, p3, r, g, b, a);
        vertex(vc, m, p3, r, g, b, a);
        vertex(vc, m, p2, r, g, b, a);
        vertex(vc, m, p1, r, g, b, a);
        vertex(vc, m, p0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
