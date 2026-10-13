package com.example.armasreales;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cuchillo lanzado: se clava en bloques y entidades, y se puede recoger. */
public class ThrownKnife extends AbstractArrow {
    private static final double DAMAGE = 3.0;   // multiplicado por la velocidad: ~8 de daño (4 corazones)

    public ThrownKnife(EntityType<? extends ThrownKnife> type, Level level) {
        super(type, level);
        this.setBaseDamage(DAMAGE);
    }

    public ThrownKnife(Level level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.KNIFE.get(), owner, level, stack, null);
        this.setBaseDamage(DAMAGE);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.CUCHILLO.get());
    }
}
