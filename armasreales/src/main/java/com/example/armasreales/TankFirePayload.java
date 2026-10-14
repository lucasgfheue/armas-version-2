package com.example.armasreales;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: el conductor dispara el cañón del tanque. */
public record TankFirePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TankFirePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "disparo_tanque"));

    public static final StreamCodec<ByteBuf, TankFirePayload> STREAM_CODEC =
            StreamCodec.unit(new TankFirePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TankFirePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof TankEntity tank) {
                tank.fire(player);
            }
        });
    }
}
