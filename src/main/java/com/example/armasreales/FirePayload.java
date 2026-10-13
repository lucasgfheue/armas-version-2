package com.example.armasreales;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: estado del clic izquierdo mientras se sostiene un arma. */
public record FirePayload(boolean down) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FirePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "disparo"));

    public static final StreamCodec<ByteBuf, FirePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(FirePayload::new, FirePayload::down);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FirePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GunControl.onInput(player, payload.down());
            }
        });
    }
}
