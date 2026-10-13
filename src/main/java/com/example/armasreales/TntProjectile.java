package com.example.armasreales;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Bloque de TNT disparado: explota al instante al tocar una entidad o un bloque. */
public class TntProjectile extends ThrowableItemProjectile {
    /** Potencia de la explosión (el TNT normal es 4.0). */
    private static final float EXPLOSION_POWER = 3.0f;

    public TntProjectile(EntityType<? extends TntProjectile> type, Level level) {
        super(type, level);
    }

    public TntProjectile(Level level, LivingEntity owner) {
        super(ModEntities.TNT_PROJECTILE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.TNT;
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
