package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Dardo eléctrico: vuela recto soltando chispas y paraliza a lo que golpea durante 5 segundos. */
public class TaserProjectile extends ThrowableItemProjectile {
    private static final float DAMAGE = 2.0f;
    public static final int PARALYSIS_TICKS = 100;   // 5 segundos
    private static final int MAX_LIFE_TICKS = 40;

    public TaserProjectile(EntityType<? extends TaserProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public TaserProjectile(Level level, LivingEntity owner) {
        super(ModEntities.TASER_PROJECTILE.get(), owner, level);
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.LIGHTNING_ROD;
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
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), y, getZ(), 5, 0.18, 0.18, 0.18, 0.12);
        level.sendParticles(ParticleTypes.END_ROD, getX(), y, getZ(), 1, 0.05, 0.05, 0.05, 0.0);
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
            Paralysis.apply(living, PARALYSIS_TICKS);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(0.5), getZ(), 30, 0.4, 0.4, 0.4, 0.3);
            level.sendParticles(ParticleTypes.FLASH, getX(), getY(0.5), getZ(), 1, 0, 0, 0, 0);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.NEUTRAL, 0.6f, 2.0f);
        this.discard();
    }
}
