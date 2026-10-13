package com.example.armasreales;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Rifle láser violeta semiautomático: un disparo potente (20 de daño) que atraviesa
 * entidades y bloques. Usa Celdas de energía.
 */
public class VioletRifleItem extends Item implements Gun {
    private static final double RANGE = 240.0;
    private static final float DAMAGE = 20.0f;
    private static final int COOLDOWN_TICKS = 30;
    private static final int BEAM_VISIBLE_TICKS = 8;

    public VioletRifleItem(Properties properties) {
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
        return 0.6f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack cell = AmmoUtil.find(player, ModItems.CELDA_ENERGIA.get());

        if (cell.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getCooldowns().addCooldown(this, 10);
            return false;
        }

        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));

        // Atraviesa bloques y entidades: daña a todas las entidades vivas del recorrido
        AABB searchBox = new AABB(eye, end).inflate(1.0);
        for (Entity target : level.getEntities(player, searchBox,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            Optional<Vec3> hit = target.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().playerAttack(player), DAMAGE);
            }
        }

        BeamEntity.flash(level, player, BeamEntity.STYLE_VIOLET, (float) RANGE, BEAM_VISIBLE_TICKS);

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 0.4f, 2.0f);
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            cell.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return false;
    }
}
