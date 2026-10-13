package com.example.armasreales;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fusil de asalto automático: mantén el clic izquierdo y dispara 10 balas por segundo.
 * La dispersión aumenta mientras sigues disparando (retroceso) y es menor al apuntar.
 */
public class AssaultRifleItem extends Item implements Gun {
    private static final double RANGE = 120.0;
    private static final float DAMAGE = 4.0f;           // 2 corazones por bala
    private static final int TICKS_PER_SHOT = 2;        // 10 disparos por segundo
    private static final double HIP_SPREAD = 0.035;
    private static final double AIM_SPREAD = 0.008;
    private static final double BLOOM_PER_TICK = 0.0015; // dispersión extra por cada tick disparando
    private static final double MAX_BLOOM = 0.04;
    private static final int WEAR_EVERY_SHOTS = 4;

    public AssaultRifleItem(Properties properties) {
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
        return 0.75f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (hasAmmo(player)) {
            return true;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                SoundSource.PLAYERS, 1.0f, 1.5f);
        return false;
    }

    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        if (heldTicks % TICKS_PER_SHOT != 0) {
            return true;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.MUNICION_FUSIL.get());
        if (ammo.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.5f);
            return false;
        }

        shoot(player.serverLevel(), player, heldTicks);
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            ammo.shrink(1);
            if ((heldTicks / TICKS_PER_SHOT) % WEAR_EVERY_SHOTS == 0) {
                gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        }
        return true;
    }

    /** Daño por bala (las variantes lo sobrescriben). */
    protected float damage() {
        return DAMAGE;
    }

    private boolean hasAmmo(ServerPlayer player) {
        return player.getAbilities().instabuild || !AmmoUtil.find(player, ModItems.MUNICION_FUSIL.get()).isEmpty();
    }

    private void shoot(ServerLevel level, Player player, int heldTicks) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double base = player.isUsingItem() ? AIM_SPREAD : HIP_SPREAD;
        double spread = base + Math.min(MAX_BLOOM, heldTicks * BLOOM_PER_TICK);

        Vec3 dir = BulletUtil.spread(level, look, spread);
        BulletUtil.Hit hit = BulletUtil.trace(level, player, eye, dir, RANGE);
        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0;   // permite una bala cada tick de disparo
            hit.entity().hurt(level.damageSources().playerAttack(player), damage());
        }

        BulletUtil.tracer(level, eye, dir, Math.min(hit.distance(), 40.0), 5.0);
        BulletUtil.muzzleFlash(level, eye, look);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.0f, 1.25f);
    }
}
