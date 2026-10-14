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

    /** Siendo el conductor, el clic izquierdo dispara el cañón en vez de atacar. */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null
                && mc.player.getVehicle() instanceof TankEntity tank
                && tank.getControllingPassenger() == mc.player) {
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(new TankFirePayload());
        }
    }
}
