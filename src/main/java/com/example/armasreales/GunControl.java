package com.example.armasreales;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Estado del botón de disparo de cada jugador (servidor) y llamadas a su arma. */
@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID)
public final class GunControl {
    private static final int INPUT_TIMEOUT_TICKS = 4;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        boolean down;
        boolean active;
        int heldTicks;
        long lastInputTick;
    }

    private GunControl() {}

    public static void onInput(ServerPlayer player, boolean down) {
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        long now = player.serverLevel().getGameTime();

        if (down) {
            s.lastInputTick = now;
            if (!s.down) {
                s.down = true;
                s.active = false;
                s.heldTicks = 0;
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof Gun gun && player.isAlive() && !player.isSpectator()) {
                    s.active = gun.onFirePressed(player, stack);
                }
            }
        } else {
            stop(player, s);
        }
    }

    /** true mientras el jugador mantiene un disparo continuo activo. */
    public static boolean isActive(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s != null && s.down && s.active;
    }

    private static void stop(ServerPlayer player, State s) {
        if (s.down && s.active) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof Gun gun) {
                gun.onFireStopped(player, stack, s.heldTicks);
            }
        }
        s.down = false;
        s.active = false;
        s.heldTicks = 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        State s = STATES.get(player.getUUID());
        if (s == null || !s.down) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        ItemStack stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof Gun gun) || !player.isAlive()
                || now - s.lastInputTick > INPUT_TIMEOUT_TICKS) {
            stop(player, s);
            return;
        }
        if (s.active) {
            boolean keep = gun.onFireHeld(player, stack, s.heldTicks);
            s.heldTicks++;
            if (!keep) {
                gun.onFireStopped(player, stack, s.heldTicks);
                s.active = false;
            }
        }
    }

    /** Con un arma en la mano, el clic izquierdo no rompe bloques. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof Gun) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
