package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Rifle láser: clic derecho apunta (zoom), clic izquierdo mantenido = rayo rojo continuo.
 * Atraviesa entidades (daño continuo + fuego); se detiene en bloques y convierte la arena en vidrio.
 */
public class LaserRifleItem extends Item implements Gun {
    private static final double RANGE = 240.0;
    private static final int MAX_BEAM_TICKS = 80;     // 4 s
    private static final int COOLDOWN_TICKS = 60;     // 3 s de recalentamiento
    private static final int DAMAGE_INTERVAL = 4;
    private static final float DAMAGE = 2.0f;

    public LaserRifleItem(Properties properties) {
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
                    SoundSource.PLAYERS, 1.0f, 1.2f);
            player.getCooldowns().addCooldown(this, 10);
            return false;
        }
        if (!creative) {
            cell.shrink(1);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8f, 2.0f);
        return true;
    }

    @Override
    public boolean onFireHeld(ServerPlayer player, ItemStack gun, int heldTicks) {
        if (heldTicks >= MAX_BEAM_TICKS) {
            return false;
        }
        fireBeam(player.serverLevel(), player, heldTicks);
        if (heldTicks % 20 == 0 && !player.getAbilities().instabuild) {
            gun.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public void onFireStopped(ServerPlayer player, ItemStack gun, int heldTicks) {
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
    }

    private void fireBeam(ServerLevel level, Player player, int elapsed) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));

        BlockHitResult blockHit = BeamUtil.traceAndClear(level, player, eye, look, RANGE);
        boolean hitBlock = blockHit.getType() == HitResult.Type.BLOCK;
        if (hitBlock) {
            end = blockHit.getLocation();
        }

        if (elapsed % DAMAGE_INTERVAL == 0) {
            AABB searchBox = new AABB(eye, end).inflate(1.0);
            for (Entity target : level.getEntities(player, searchBox,
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
                Optional<Vec3> hit = target.getBoundingBox().inflate(0.3).clip(eye, end);
                if (hit.isPresent()) {
                    target.invulnerableTime = 0;
                    target.hurt(level.damageSources().playerAttack(player), DAMAGE);
                    if (!target.fireImmune()) {
                        target.setRemainingFireTicks(60);
                    }
                }
            }
        }

        if (hitBlock) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.SAND)) {
                level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
                level.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
            }
            level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.01);
        }

        BeamEntity.update(level, player, BeamEntity.STYLE_LASER, (float) eye.distanceTo(end));

        if (elapsed % 10 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.5f, 2.0f);
        }
    }
}
