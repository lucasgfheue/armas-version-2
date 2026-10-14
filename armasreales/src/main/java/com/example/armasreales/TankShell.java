package com.example.armasreales;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Obús del cañón del tanque: explota al tocar un bloque o una entidad. */
public class TankShell extends ThrowableItemProjectile {
    private static final float EXPLOSION_POWER = 3.5f;

    public TankShell(EntityType<? extends TankShell> type, Level level) {
        super(type, level);
    }

    public TankShell(Level level, LivingEntity owner) {
        super(ModEntities.TANK_SHELL.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.OBUS.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.level().explode(this, this.getX(), this.getY(), this.getZ(),
                    EXPLOSION_POWER, Level.ExplosionInteraction.TNT);
            this.discard();
        }
    }
}
