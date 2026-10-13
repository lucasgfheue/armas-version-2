package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Trazado del rayo: rompe al instante hojas, nieve y hielo, y se detiene en el primer bloque que no sea de esos. */
public final class BeamUtil {
    private static final int MAX_BREAKS_PER_TICK = 32;

    private BeamUtil() {}

    public static boolean isFragile(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.SNOW) || state.is(BlockTags.ICE);
    }

    /**
     * Lanza el rayo desde el ojo. Los bloques frágiles (hojas, nieve, hielo) se destruyen y el rayo
     * sigue de largo; devuelve el impacto final contra un bloque sólido (o MISS si no choca con nada).
     */
    public static BlockHitResult traceAndClear(ServerLevel level, Player player, Vec3 eye, Vec3 look, double range) {
        Vec3 start = eye;
        int broken = 0;

        while (true) {
            double remaining = range - start.distanceTo(eye);
            Vec3 end = start.add(look.scale(Math.max(remaining, 0.0)));
            BlockHitResult hit = level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            boolean missed = hit.getType() == HitResult.Type.MISS;
            boolean hitFragile = !missed && isFragile(level.getBlockState(hit.getBlockPos()));

            // Capas finas de nieve y similares que el rayo atraviesa sin "chocar"
            Vec3 segEnd = missed ? end : hit.getLocation();
            double len = start.distanceTo(segEnd);
            for (double d = 0.0; d <= len && broken < MAX_BREAKS_PER_TICK; d += 0.5) {
                BlockPos p = BlockPos.containing(start.add(look.scale(d)));
                if (isFragile(level.getBlockState(p))) {
                    level.destroyBlock(p, false, player);
                    broken++;
                }
            }

            if (missed || !hitFragile || broken >= MAX_BREAKS_PER_TICK || remaining <= 0.5) {
                return hit;
            }

            // El bloque del impacto es frágil: lo rompemos y seguimos desde ahí
            BlockPos hp = hit.getBlockPos();
            if (isFragile(level.getBlockState(hp))) {
                level.destroyBlock(hp, false, player);
                broken++;
            }
            start = hit.getLocation().add(look.scale(0.02));
        }
    }
}
