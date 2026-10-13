package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Paraliza entidades: no se mueven, los mobs pierden su objetivo, y salen chispas eléctricas. */
@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID)
public final class Paralysis {
    private record Entry(ServerLevel level, long until) {}

    private static final Map<UUID, Entry> ACTIVE = new HashMap<>();

    private Paralysis() {}

    public static void apply(LivingEntity target, int ticks) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 4, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false));
        ACTIVE.put(target.getUUID(), new Entry(level, level.getGameTime() + ticks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, Entry>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> e = it.next();
            ServerLevel level = e.getValue().level();
            Entity entity = level.getEntity(e.getKey());
            long now = level.getGameTime();

            if (!(entity instanceof LivingEntity living) || !living.isAlive() || now >= e.getValue().until()) {
                it.remove();
                continue;
            }

            living.setDeltaMovement(0.0, Math.min(0.0, living.getDeltaMovement().y), 0.0);
            if (living instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }

            double w = living.getBbWidth() * 0.6;
            double h = living.getBbHeight() * 0.5;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    living.getX(), living.getY() + h, living.getZ(), 4, w, h, w, 0.08);
            if (now % 8 == 0) {
                level.playSound(null, living.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                        SoundSource.HOSTILE, 0.15f, 2.0f);
            }
        }
    }
}
