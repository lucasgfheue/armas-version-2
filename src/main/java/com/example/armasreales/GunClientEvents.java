package com.example.armasreales;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID, value = Dist.CLIENT)
public final class GunClientEvents {
    private static boolean wasFiring = false;

    private GunClientEvents() {}

    /** Envía al servidor el estado del clic izquierdo mientras sostienes un arma. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean firing = player != null
                && mc.screen == null
                && mc.mouseHandler.isMouseGrabbed()
                && mc.options.keyAttack.isDown()
                && player.getMainHandItem().getItem() instanceof Gun;

        if (firing) {
            PacketDistributor.sendToServer(new FirePayload(true));
        } else if (wasFiring) {
            PacketDistributor.sendToServer(new FirePayload(false));
        }
        wasFiring = firing;
    }

    /** Con un arma, el clic izquierdo no ataca ni agita el brazo. */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null && mc.player.getMainHandItem().getItem() instanceof Gun) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Zoom al apuntar (clic derecho mantenido). */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof Gun gun) {
            event.setNewFovModifier(event.getNewFovModifier() * gun.aimZoom());
        }
    }
}
