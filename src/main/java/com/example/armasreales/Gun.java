package com.example.armasreales;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Arma del mod: clic derecho = apuntar (mantener), clic izquierdo = disparar. */
public interface Gun {
    /** Se pulsó el disparo. Devuelve true si empieza un disparo continuo (rayo). */
    boolean onFirePressed(ServerPlayer player, ItemStack stack);

    /** Cada tick mientras se mantiene pulsado (sólo armas continuas). false = cortar. */
    default boolean onFireHeld(ServerPlayer player, ItemStack stack, int heldTicks) {
        return false;
    }

    /** Fin del disparo continuo (soltaste el botón o se cortó). */
    default void onFireStopped(ServerPlayer player, ItemStack stack, int heldTicks) {
    }

    /** Multiplicador del FOV al apuntar (menor = más zoom). */
    default float aimZoom() {
        return 0.8f;
    }
}
