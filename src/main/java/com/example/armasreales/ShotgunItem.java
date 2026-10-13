package com.example.armasreales;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escopeta: cada disparo lanza 12 perdigones con dispersión. El daño de cada perdigón
 * depende de la distancia: de cerca se suman todos (hasta 36 = 18 corazones), de lejos casi nada.
 */
public class ShotgunItem extends Item implements Gun {
    private static final int PELLETS = 12;
    private static final double RANGE = 50.0;
    private static final float PELLET_DAMAGE = 3.0f;       // por perdigón a quemarropa
    private static final double FULL_DAMAGE_DISTANCE = 6.0; // hasta aquí, daño completo
    private static final double FALLOFF_DISTANCE = 24.0;    // a partir de ahí cae linealmente
    private static final float MIN_FACTOR = 0.05f;
    private static final double HIP_SPREAD = 0.11;
    private static final double AIM_SPREAD = 0.07;
    private static final int COOLDOWN_TICKS = 22;

    public ShotgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public float aimZoom() {
        return 0.85f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack shell = AmmoUtil.find(player, ModItems.CARTUCHO_ESCOPETA.get());

        if (shell.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.9f);
            player.getCooldowns().addCooldown(this, 8);
            return false;
        }

        fire(player.serverLevel(), player, player.isUsingItem());
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            shell.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return false;
    }

    private void fire(ServerLevel level, Player player, boolean aiming) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double spread = aiming ? AIM_SPREAD : HIP_SPREAD;

        // Se acumula el daño de todos los perdigones por entidad y se aplica de una vez
        Map<Entity, Float> damage = new LinkedHashMap<>();
        for (int i = 0; i < PELLETS; i++) {
            Vec3 dir = BulletUtil.spread(level, look, spread);
            BulletUtil.Hit hit = BulletUtil.trace(level, player, eye, dir, RANGE);
            BulletUtil.tracer(level, eye, dir, hit.distance(), 6.0);

            if (hit.entity() != null) {
                float factor = (float) Mth.clamp(
                        1.0 - (hit.distance() - FULL_DAMAGE_DISTANCE) / FALLOFF_DISTANCE, MIN_FACTOR, 1.0);
                damage.merge(hit.entity(), PELLET_DAMAGE * factor, Float::sum);
            }
        }
        for (Map.Entry<Entity, Float> e : damage.entrySet()) {
            e.getKey().invulnerableTime = 0;
            e.getKey().hurt(level.damageSources().playerAttack(player), e.getValue());
        }

        BulletUtil.muzzleFlash(level, eye, look);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.6f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.2f, 0.5f);
    }
}
