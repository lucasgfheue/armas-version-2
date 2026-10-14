package com.example.armasreales;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID, value = Dist.CLIENT)
public final class TankClientEvents {
    private TankClientEvents() {}

    /** Dentro del tanque, el clic izquierdo dispara el cañón. */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null && mc.player.getVehicle() instanceof TankEntity) {
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(new TankFirePayload());
        }
    }
}
