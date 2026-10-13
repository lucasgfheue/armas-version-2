package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Trazado de balas instantáneas (hitscan): primer bloque o entidad en el camino. */
public final class BulletUtil {
    private BulletUtil() {}

    /** Resultado: entidad golpeada (o null), distancia recorrida y punto final. */
    public record Hit(Entity entity, double distance, Vec3 end) {}

    public static Hit trace(ServerLevel level, Player player, Vec3 eye, Vec3 dir, double range) {
        Vec3 end = eye.add(dir.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        AABB box = new AABB(eye, end).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, player, eye, end, box, e -> !e.isSpectator() && e.isPickable());
        Entity target = null;
        if (entityHit != null) {
            target = entityHit.getEntity();
            end = entityHit.getLocation();
        }
        return new Hit(target, eye.distanceTo(end), end);
    }

    /** Dirección con dispersión gaussiana alrededor de la mirada. */
    public static Vec3 spread(ServerLevel level, Vec3 look, double amount) {
        if (amount <= 0.0) {
            return look;
        }
        return look.add(level.random.nextGaussian() * amount,
                level.random.nextGaussian() * amount,
                level.random.nextGaussian() * amount).normalize();
    }

    /** Estela de partículas (pocas, para no saturar). */
    public static void tracer(ServerLevel level, Vec3 eye, Vec3 dir, double distance, double step) {
        for (double d = 2.5; d < distance; d += step) {
            Vec3 p = eye.add(dir.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    public static void muzzleFlash(ServerLevel level, Vec3 eye, Vec3 look) {
        Vec3 muzzle = eye.add(look.scale(0.9));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.12, muzzle.z, 4, 0.03, 0.03, 0.03, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.12, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
    }
}
