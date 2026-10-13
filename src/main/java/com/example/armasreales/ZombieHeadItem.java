package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cabeza de zombi: arma cuerpo a cuerpo que muerde. Cada golpe (clic izquierdo) o mordisco
 * (clic derecho, alcance 3,5 bloques) infecta al objetivo con Wither III durante 8 segundos.
 */
public class ZombieHeadItem extends Item {
    private static final int INFECTION_TICKS = 160;   // 8 segundos
    private static final int INFECTION_AMPLIFIER = 2; // Wither III
    private static final double BITE_REACH = 3.5;
    private static final float BITE_DAMAGE = 4.0f;
    private static final int BITE_COOLDOWN_TICKS = 25;
    private static final int MISS_COOLDOWN_TICKS = 10;

    public ZombieHeadItem(Properties properties) {
        super(properties);
    }

    /** 6 de daño cuerpo a cuerpo (1 base + 5), ataque algo lento. */
    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 5.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Golpe cuerpo a cuerpo: contagia. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        infect(target, attacker);
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    /** Mordisco con clic derecho. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (level instanceof ServerLevel serverLevel) {
            Vec3 eye = player.getEyePosition();
            BulletUtil.Hit hit = BulletUtil.trace(serverLevel, player, eye, player.getLookAngle(), BITE_REACH);

            if (hit.entity() instanceof LivingEntity target) {
                target.hurt(serverLevel.damageSources().playerAttack(player), BITE_DAMAGE);
                infect(target, player);
                stack.hurtAndBreak(1, player,
                        hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                player.getCooldowns().addCooldown(this, BITE_COOLDOWN_TICKS);
            } else {
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                        SoundSource.PLAYERS, 0.5f, 1.8f);
                player.getCooldowns().addCooldown(this, MISS_COOLDOWN_TICKS);
            }
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void infect(LivingEntity target, LivingEntity attacker) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, INFECTION_TICKS, INFECTION_AMPLIFIER), attacker);

        level.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,
                SoundSource.PLAYERS, 0.8f, 0.7f);
        level.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_INFECT,
                SoundSource.PLAYERS, 0.9f, 1.0f);
        double y = target.getY() + target.getBbHeight() * 0.6;
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), y, target.getZ(), 6, 0.3, 0.2, 0.3, 0.1);
        level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), y, target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
    }
}
