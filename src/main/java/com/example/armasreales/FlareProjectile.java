package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/**
 * Bengala gigante: vuela recto, brilla (halo + bloque de luz invisible que la sigue),
 * deja una gran estela de humo rojo y prende fuego a la entidad que golpea.
 */
public class FlareProjectile extends ThrowableItemProjectile {
    private static final float DIRECT_DAMAGE = 4.0f;   // 2 corazones
    private static final int FIRE_TICKS = 160;         // 8 segundos ardiendo
    private static final int MAX_LIFE_TICKS = 80;      // se apaga a los 4 segundos

    // Humo rojo: partículas grandes que tardan en desvanecerse
    private static final DustParticleOptions RED_SMOKE =
            new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.05f), 3.0f);
    private static final DustParticleOptions DARK_RED_SMOKE =
            new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 2.5f);

    private BlockPos lightPos = null;

    public FlareProjectile(EntityType<? extends FlareProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public FlareProjectile(Level level, LivingEntity owner) {
        super(ModEntities.FLARE_PROJECTILE.get(), owner, level);
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double cx = this.getX();
        double cy = this.getY(0.5);
        double cz = this.getZ();

        if (this.tickCount > MAX_LIFE_TICKS) {
            clearLight();
            serverLevel.sendParticles(RED_SMOKE, cx, cy, cz, 25, 0.6, 0.6, 0.6, 0.0);
            this.discard();
            return;
        }

        // Luz que acompaña a la bengala
        BlockPos pos = BlockPos.containing(cx, cy, cz);
        if (!pos.equals(lightPos)) {
            clearLight();
            if (serverLevel.getBlockState(pos).isAir()) {
                serverLevel.setBlock(pos, Blocks.LIGHT.defaultBlockState(), 2);
                lightPos = pos;
            }
        }

        // Estela: bastante humo rojo + chispas de fuego
        serverLevel.sendParticles(RED_SMOKE, cx, cy, cz, 6, 0.35, 0.35, 0.35, 0.0);
        serverLevel.sendParticles(DARK_RED_SMOKE, cx, cy, cz, 3, 0.4, 0.4, 0.4, 0.0);
        serverLevel.sendParticles(ParticleTypes.FLAME, cx, cy, cz, 3, 0.25, 0.25, 0.25, 0.02);
        serverLevel.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 1, 0.2, 0.2, 0.2, 0.01);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        target.hurt(this.damageSources().thrown(this, this.getOwner()), DIRECT_DAMAGE);
        if (!target.fireImmune()) {
            target.setRemainingFireTicks(FIRE_TICKS);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            double cy = this.getY(0.5);
            serverLevel.sendParticles(ParticleTypes.FLAME, getX(), cy, getZ(), 30, 0.5, 0.5, 0.5, 0.08);
            serverLevel.sendParticles(ParticleTypes.LAVA, getX(), cy, getZ(), 8, 0.4, 0.4, 0.4, 0.0);
            serverLevel.sendParticles(RED_SMOKE, getX(), cy, getZ(), 40, 0.8, 0.8, 0.8, 0.0);
            serverLevel.sendParticles(DARK_RED_SMOKE, getX(), cy, getZ(), 25, 0.8, 0.8, 0.8, 0.0);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.NEUTRAL, 1.0f, 0.8f);
        clearLight();
        this.discard();
    }

    private void clearLight() {
        if (lightPos != null && !this.level().isClientSide) {
            Level level = this.level();
            if (level.getBlockState(lightPos).is(Blocks.LIGHT)) {
                level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        lightPos = null;
    }
}
