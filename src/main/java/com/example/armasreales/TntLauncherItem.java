package com.example.armasreales;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Lanzador de TNT: clic derecho apunta, clic izquierdo dispara. Usa bloques de TNT. */
public class TntLauncherItem extends Item implements Gun {
    private static final int COOLDOWN_TICKS = 30;
    private static final float SPEED = 2.0f;

    public TntLauncherItem(Properties properties) {
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
        return 0.8f;
    }

    @Override
    public boolean onFirePressed(ServerPlayer player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack tnt = AmmoUtil.find(player, Items.TNT);

        if (tnt.isEmpty() && !creative) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            player.getCooldowns().addCooldown(this, 10);
            return false;
        }

        Level level = player.level();
        TntProjectile projectile = new TntProjectile(level, player);
        float inaccuracy = player.isUsingItem() ? 0.2f : 1.5f;
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, SPEED, inaccuracy);
        level.addFreshEntity(projectile);

        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.TNT_PRIMED,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        player.swing(InteractionHand.MAIN_HAND, true);

        if (!creative) {
            tnt.shrink(1);
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return false;
    }
}
