package com.example.armasreales;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/** Bola de ácido: al golpear a una entidad le aplica Wither durante 5 segundos. */
public class AcidProjectile extends ThrowableItemProjectile {
    private static final float DAMAGE = 2.0f;
    private static final int WITHER_TICKS = 200;   // 10 segundos
    private static final int WITHER_AMPLIFIER = 2; // Wither III
    private static final float EXPLOSION_POWER = 1.5f;   // explosión pequeña (el creeper es 3.0)
    private static final double SPLASH_RADIUS = 3.0;
    private static final int MAX_LIFE_TICKS = 100;

    private static final DustParticleOptions ACID_DUST =
            new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.1f), 1.6f);

    public AcidProjectile(EntityType<? extends AcidProjectile> type, Level level) {
        super(type, level);
    }

    public AcidProjectile(Level level, LivingEntity owner) {
        super(ModEntities.ACID_PROJECTILE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SLIME_BALL;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount > MAX_LIFE_TICKS) {
            this.discard();
            return;
        }
        double y = this.getY(0.5);
        level.sendParticles(ACID_DUST, getX(), y, getZ(), 3, 0.15, 0.15, 0.15, 0.0);
        level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), y, getZ(), 2, 0.12, 0.12, 0.12, 0.0);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        target.hurt(this.damageSources().thrown(this, this.getOwner()), DAMAGE);
        if (target instanceof LivingEntity living) {
            applyWither(living);
        }

        // Explota al golpear a una entidad: afecta a las de alrededor pero NO rompe bloques
        if (this.level() instanceof ServerLevel level) {
            level.explode(this, getX(), getY(0.5), getZ(), EXPLOSION_POWER, Level.ExplosionInteraction.NONE);

            AABB area = this.getBoundingBox().inflate(SPLASH_RADIUS);
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != target && e != this.getOwner() && e.isAlive() && !e.isSpectator())) {
                if (other.distanceToSqr(this) <= SPLASH_RADIUS * SPLASH_RADIUS) {
                    applyWither(other);
                }
            }
            level.sendParticles(ACID_DUST, getX(), getY(0.5), getZ(), 40, SPLASH_RADIUS * 0.4,
                    SPLASH_RADIUS * 0.3, SPLASH_RADIUS * 0.4, 0.0);
        }
    }

    private void applyWither(LivingEntity living) {
        living.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_AMPLIFIER), this.getOwner());
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ACID_DUST, getX(), getY(0.5), getZ(), 30, 0.5, 0.5, 0.5, 0.0);
            level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(0.5), getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH,
                SoundSource.NEUTRAL, 1.0f, 0.7f);
        this.discard();
    }
}
